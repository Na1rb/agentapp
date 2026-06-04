package com.nairb.ai130.app.workflow;

import com.nairb.ai130.common.enums.WorkflowNodeType;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 工作流模板管理服务。
 * <p>
 * 提供模板的 CRUD 和自定义功能：用户可以在预设流程中插入/跳过节点，
 * 或创建全新的流程模板。
 */
@Service
public class WorkflowTemplateService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowTemplateService.class);

    private final WorkflowRepository repository;
    private final JdbcTemplate jdbc;

    public WorkflowTemplateService(WorkflowRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    // ==================== 查询 ====================

    /** 列出所有可用模板 */
    public List<WorkflowDefinition> listTemplates() {
        return repository.listDefinitions().stream()
                .map(definition -> repository.loadDefinition(definition.getId()))
                .toList();
    }

    /** 获取模板详情（含节点和边） */
    public WorkflowDefinition getTemplate(String definitionId) {
        return repository.loadDefinition(definitionId);
    }

    // ==================== 自定义模板 ====================

    /**
     * 在指定节点后插入一个节点。
     *
     * @param definitionId  原始模板 ID
     * @param afterNodeId   在此节点后插入
     * @param nodeType      新节点类型
     * @param nodeName      新节点名称
     * @return 新模板 ID
     */
    @Transactional
    public String insertNode(String definitionId, String afterNodeId,
                              WorkflowNodeType nodeType, String nodeName) {
        WorkflowDefinition def = repository.loadDefinition(definitionId);
        if (def == null) throw new IllegalArgumentException("模板不存在: " + definitionId);

        String newDefId = cloneDefinition(def, "自定义-" + def.getName());
        String newNodeId = "custom-" + UUID.randomUUID().toString().substring(0, 8);

        // 插入新节点
        WorkflowDefinition.NodeDef afterNode = def.getNode(afterNodeId);
        int newSortOrder = afterNode != null ? afterNode.getSortOrder() + 1 : def.getNodes().size() + 1;

        jdbc.update("INSERT INTO wf_node (id, workflow_id, node_type, name, config_json, retry_limit, sort_order) VALUES (?, ?, ?, ?, NULL, 3, ?)",
                newNodeId, newDefId, nodeType.name(), nodeName, newSortOrder);

        // 更新边的连接：原 afterNode 的出边改为 newNode → 原目标
        List<WorkflowDefinition.EdgeDef> outgoingEdges = def.getOutgoingEdges(afterNodeId);
        for (WorkflowDefinition.EdgeDef edge : outgoingEdges) {
            jdbc.update("UPDATE wf_edge SET source_node_id = ? WHERE id = ? AND workflow_id = ?",
                    newNodeId, edge.getId(), newDefId);
        }

        // 创建 afterNode → newNode 的边
        String newEdgeId = "edge-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO wf_edge (id, workflow_id, source_node_id, target_node_id, condition_expr, sort_order) VALUES (?, ?, ?, ?, NULL, 1)",
                newEdgeId, newDefId, afterNodeId, newNodeId);

        log.info("Inserted node {} ({}) after {} in template {}", newNodeId, nodeName, afterNodeId, newDefId);
        return newDefId;
    }

    /**
     * 从模板中移除一个节点（跳过）。
     * 原节点的入边直接连接到原节点的出边目标。
     */
    @Transactional
    public String skipNode(String definitionId, String nodeId) {
        WorkflowDefinition def = repository.loadDefinition(definitionId);
        if (def == null) throw new IllegalArgumentException("模板不存在: " + definitionId);

        WorkflowDefinition.NodeDef node = def.getNode(nodeId);
        if (node == null) throw new IllegalArgumentException("节点不存在: " + nodeId);

        String newDefId = cloneDefinition(def, "跳过-" + node.getName() + "-" + def.getName());

        // 找到入边和出边
        List<WorkflowDefinition.EdgeDef> incomingEdges = def.getEdges().stream()
                .filter(e -> e.getTargetNodeId().equals(nodeId))
                .toList();
        List<WorkflowDefinition.EdgeDef> outgoingEdges = def.getOutgoingEdges(nodeId);

        // 将入边连接到出边的目标
        if (!incomingEdges.isEmpty() && !outgoingEdges.isEmpty()) {
            for (WorkflowDefinition.EdgeDef inEdge : incomingEdges) {
                for (WorkflowDefinition.EdgeDef outEdge : outgoingEdges) {
                    jdbc.update("UPDATE wf_edge SET target_node_id = ? WHERE id = ? AND workflow_id = ?",
                            outEdge.getTargetNodeId(), inEdge.getId(), newDefId);
                }
            }
        }

        // 删除被跳过的节点（CASCADE 会处理出边）
        jdbc.update("DELETE FROM wf_node WHERE id = ? AND workflow_id = ?", nodeId, newDefId);

        log.info("Skipped node {} in template {}", nodeId, newDefId);
        return newDefId;
    }

    /**
     * 启用/禁用模板。
     */
    public void toggleTemplate(String definitionId, boolean enabled) {
        jdbc.update("UPDATE wf_definition SET enabled = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                enabled, definitionId);
        log.info("Template {} enabled={}", definitionId, enabled);
    }

    // ==================== 私有工具 ====================

    /**
     * 克隆一个模板定义（深拷贝所有节点和边到新 ID）。
     */
    private String cloneDefinition(WorkflowDefinition source, String newName) {
        String newId = "wf-" + UUID.randomUUID().toString().substring(0, 8);

        // 克隆定义头
        jdbc.update("INSERT INTO wf_definition (id, name, description, version, enabled) VALUES (?, ?, ?, 1, TRUE)",
                newId, newName, "自定义模板，基于 " + source.getName());

        // 克隆节点
        if (source.getNodes() != null) {
            for (WorkflowDefinition.NodeDef node : source.getNodes()) {
                jdbc.update("INSERT INTO wf_node (id, workflow_id, node_type, name, config_json, retry_limit, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        node.getId(), newId, node.getNodeType().name(), node.getName(),
                        node.getConfigJson(), node.getRetryLimit(), node.getSortOrder());
            }
        }

        // 克隆边
        if (source.getEdges() != null) {
            for (WorkflowDefinition.EdgeDef edge : source.getEdges()) {
                jdbc.update("INSERT INTO wf_edge (id, workflow_id, source_node_id, target_node_id, condition_expr, sort_order) VALUES (?, ?, ?, ?, ?, ?)",
                        edge.getId(), newId, edge.getSourceNodeId(), edge.getTargetNodeId(),
                        edge.getConditionExpr(), edge.getSortOrder());
            }
        }

        log.info("Cloned template {} -> {}", source.getId(), newId);
        return newId;
    }
}
