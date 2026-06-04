package com.nairb.ai130.domain.workflow;

import com.nairb.ai130.common.enums.WorkflowNodeType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流模板定义 — 对应于 wf_definition + wf_node + wf_edge 三表联合。
 * <p>
 * 描述一个完整的 DAG 工作流：节点列表 + 边的转移条件。
 * 由 {@code WorkflowRepository} 从数据库加载。
 */
public class WorkflowDefinition {

    private String id;
    private String name;
    private String description;
    private int version;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    /** 节点列表 */
    private List<NodeDef> nodes;

    /** 边列表 */
    private List<EdgeDef> edges;

    /** nodes 按 id 索引，便于快速查找 */
    private Map<String, NodeDef> nodeIndex;

    public WorkflowDefinition() {}

    // ==================== 初始化 ====================

    /**
     * 调用 setNodes / setEdges 后重建索引。
     */
    public void buildIndex() {
        this.nodeIndex = nodes != null
                ? nodes.stream().collect(Collectors.toMap(NodeDef::getId, n -> n))
                : Map.of();
    }

    // ==================== 查询方法 ====================

    /** 获取起始节点（sort_order 最小的 ANALYSIS 或第一个节点） */
    public NodeDef getStartNode() {
        if (nodes == null || nodes.isEmpty()) return null;
        return nodes.stream()
                .filter(n -> n.nodeType == WorkflowNodeType.ANALYSIS)
                .findFirst()
                .orElse(nodes.get(0));
    }

    /** 根据节点 ID 获取节点定义 */
    public NodeDef getNode(String nodeId) {
        return nodeIndex != null ? nodeIndex.get(nodeId) : null;
    }

    /** 获取某个节点的出边列表 */
    public List<EdgeDef> getOutgoingEdges(String nodeId) {
        if (edges == null) return List.of();
        return edges.stream()
                .filter(e -> e.sourceNodeId.equals(nodeId))
                .sorted((a, b) -> Integer.compare(a.sortOrder, b.sortOrder))
                .collect(Collectors.toList());
    }

    // ==================== Getters / Setters ====================

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<NodeDef> getNodes() { return nodes; }
    public void setNodes(List<NodeDef> nodes) { this.nodes = nodes; }

    public List<EdgeDef> getEdges() { return edges; }
    public void setEdges(List<EdgeDef> edges) { this.edges = edges; }

    // ==================== 内部类 ====================

    /** 节点定义 */
    public static class NodeDef {
        private String id;
        private String workflowId;
        private WorkflowNodeType nodeType;
        private String name;
        private String configJson;
        private int retryLimit;
        private int sortOrder;

        public NodeDef() {}

        public NodeDef(String id, WorkflowNodeType nodeType, String name, int retryLimit, int sortOrder) {
            this.id = id;
            this.nodeType = nodeType;
            this.name = name;
            this.retryLimit = retryLimit;
            this.sortOrder = sortOrder;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

        public WorkflowNodeType getNodeType() { return nodeType; }
        public void setNodeType(WorkflowNodeType nodeType) { this.nodeType = nodeType; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getConfigJson() { return configJson; }
        public void setConfigJson(String configJson) { this.configJson = configJson; }

        public int getRetryLimit() { return retryLimit; }
        public void setRetryLimit(int retryLimit) { this.retryLimit = retryLimit; }

        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    }

    /** 边定义 */
    public static class EdgeDef {
        private String id;
        private String workflowId;
        private String sourceNodeId;
        private String targetNodeId;
        private String conditionExpr;
        private int sortOrder;

        public EdgeDef() {}

        public EdgeDef(String id, String sourceNodeId, String targetNodeId, String conditionExpr, int sortOrder) {
            this.id = id;
            this.sourceNodeId = sourceNodeId;
            this.targetNodeId = targetNodeId;
            this.conditionExpr = conditionExpr;
            this.sortOrder = sortOrder;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }

        public String getSourceNodeId() { return sourceNodeId; }
        public void setSourceNodeId(String sourceNodeId) { this.sourceNodeId = sourceNodeId; }

        public String getTargetNodeId() { return targetNodeId; }
        public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

        public String getConditionExpr() { return conditionExpr; }
        public void setConditionExpr(String conditionExpr) { this.conditionExpr = conditionExpr; }

        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    }
}
