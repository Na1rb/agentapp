package com.nairb.ai130.app.workflow;

import com.nairb.ai130.common.enums.WorkflowNodeType;
import com.nairb.ai130.domain.workflow.NodeResult;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import com.nairb.ai130.domain.workflow.WorkflowExecution;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class WorkflowRepository {

    private final JdbcTemplate jdbc;

    public WorkflowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public WorkflowDefinition loadDefinition(String definitionId) {
        List<WorkflowDefinition> definitions = jdbc.query(
                "SELECT id, name, description, version, enabled, created_at, updated_at FROM wf_definition WHERE id = ?",
                this::mapDefinition,
                definitionId);
        if (definitions.isEmpty()) return null;

        WorkflowDefinition def = definitions.get(0);
        List<WorkflowDefinition.NodeDef> nodes = jdbc.query(
                "SELECT id, workflow_id, node_type, name, config_json, retry_limit, sort_order FROM wf_node WHERE workflow_id = ? ORDER BY sort_order",
                this::mapNode,
                definitionId);
        def.setNodes(nodes);

        List<WorkflowDefinition.EdgeDef> edges = jdbc.query(
                "SELECT id, workflow_id, source_node_id, target_node_id, condition_expr, sort_order FROM wf_edge WHERE workflow_id = ? ORDER BY sort_order",
                this::mapEdge,
                definitionId);
        def.setEdges(edges);

        def.buildIndex();
        return def;
    }

    public List<WorkflowDefinition> listDefinitions() {
        return jdbc.query(
                "SELECT id, name, description, version, enabled, created_at, updated_at FROM wf_definition WHERE enabled = TRUE ORDER BY created_at",
                this::mapDefinition);
    }

    public void createExecution(WorkflowExecution execution) {
        jdbc.update(
                "INSERT INTO wf_execution (id, workflow_id, session_id, prompt, status, current_node_id, retry_count, error_message, started_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                execution.getId(),
                execution.getWorkflowId(),
                execution.getSessionId(),
                execution.getPrompt(),
                execution.getStatus(),
                execution.getCurrentNodeId(),
                execution.getRetryCount(),
                execution.getErrorMessage(),
                toTimestamp(execution.getStartedAt()),
                toTimestamp(execution.getUpdatedAt()));
    }

    public void updateExecution(WorkflowExecution execution) {
        execution.setUpdatedAt(Instant.now());
        jdbc.update(
                "UPDATE wf_execution SET status = ?, current_node_id = ?, retry_count = ?, error_message = ?, finished_at = ?, updated_at = ? WHERE id = ?",
                execution.getStatus(),
                execution.getCurrentNodeId(),
                execution.getRetryCount(),
                execution.getErrorMessage(),
                toTimestamp(execution.getFinishedAt()),
                toTimestamp(execution.getUpdatedAt()),
                execution.getId());
    }

    public WorkflowExecution findLastExecutionBySession(String sessionId) {
        List<WorkflowExecution> list = jdbc.query(
                "SELECT id, workflow_id, session_id, prompt, status, current_node_id, retry_count, error_message, started_at, finished_at, updated_at FROM wf_execution WHERE session_id = ? ORDER BY started_at DESC LIMIT 1",
                this::mapExecution,
                sessionId);
        return list.isEmpty() ? null : list.get(0);
    }

    public WorkflowExecution getExecution(String executionId) {
        List<WorkflowExecution> list = jdbc.query(
                "SELECT id, workflow_id, session_id, prompt, status, current_node_id, retry_count, error_message, started_at, finished_at, updated_at FROM wf_execution WHERE id = ?",
                this::mapExecution,
                executionId);
        return list.isEmpty() ? null : list.get(0);
    }

    public void saveNodeResult(NodeResult result) {
        jdbc.update(
                "INSERT INTO wf_node_result (id, execution_id, node_id, status, input_text, output_text, error_message, check_result, started_at, finished_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                result.getId(),
                result.getExecutionId(),
                result.getNodeId(),
                result.getStatus(),
                result.getInputText(),
                result.getOutputText(),
                result.getErrorMessage(),
                result.getCheckResultJson(),
                toTimestamp(result.getStartedAt()),
                toTimestamp(result.getFinishedAt()));
    }

    public void updateNodeResult(NodeResult result) {
        jdbc.update(
                "UPDATE wf_node_result SET status = ?, output_text = ?, error_message = ?, check_result = ?, finished_at = ? WHERE id = ?",
                result.getStatus(),
                result.getOutputText(),
                result.getErrorMessage(),
                result.getCheckResultJson(),
                toTimestamp(result.getFinishedAt()),
                result.getId());
    }

    public List<NodeResult> getNodeResults(String executionId) {
        return jdbc.query(
                "SELECT id, execution_id, node_id, status, input_text, output_text, error_message, check_result, started_at, finished_at FROM wf_node_result WHERE execution_id = ? ORDER BY started_at NULLS LAST",
                this::mapNodeResult,
                executionId);
    }

    public NodeResult getLastCompletedNodeResult(String executionId) {
        List<NodeResult> list = jdbc.query(
                "SELECT id, execution_id, node_id, status, input_text, output_text, error_message, check_result, started_at, finished_at FROM wf_node_result WHERE execution_id = ? AND status = 'completed' ORDER BY finished_at DESC LIMIT 1",
                this::mapNodeResult,
                executionId);
        return list.isEmpty() ? null : list.get(0);
    }

    public String getLastNodeStatus(String executionId) {
        List<String> list = jdbc.query(
                "SELECT status FROM wf_node_result WHERE execution_id = ? ORDER BY finished_at DESC NULLS LAST LIMIT 1",
                (rs, row) -> rs.getString("status"),
                executionId);
        return list.isEmpty() ? null : list.get(0);
    }

    private WorkflowDefinition mapDefinition(ResultSet rs, int row) throws SQLException {
        WorkflowDefinition def = new WorkflowDefinition();
        def.setId(rs.getString("id"));
        def.setName(rs.getString("name"));
        def.setDescription(rs.getString("description"));
        def.setVersion(rs.getInt("version"));
        def.setEnabled(rs.getBoolean("enabled"));
        def.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        def.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return def;
    }

    private WorkflowDefinition.NodeDef mapNode(ResultSet rs, int row) throws SQLException {
        WorkflowDefinition.NodeDef node = new WorkflowDefinition.NodeDef();
        node.setId(rs.getString("id"));
        node.setWorkflowId(rs.getString("workflow_id"));
        node.setNodeType(WorkflowNodeType.valueOf(rs.getString("node_type")));
        node.setName(rs.getString("name"));
        node.setConfigJson(rs.getString("config_json"));
        node.setRetryLimit(rs.getInt("retry_limit"));
        node.setSortOrder(rs.getInt("sort_order"));
        return node;
    }

    private WorkflowDefinition.EdgeDef mapEdge(ResultSet rs, int row) throws SQLException {
        WorkflowDefinition.EdgeDef edge = new WorkflowDefinition.EdgeDef();
        edge.setId(rs.getString("id"));
        edge.setWorkflowId(rs.getString("workflow_id"));
        edge.setSourceNodeId(rs.getString("source_node_id"));
        edge.setTargetNodeId(rs.getString("target_node_id"));
        edge.setConditionExpr(rs.getString("condition_expr"));
        edge.setSortOrder(rs.getInt("sort_order"));
        return edge;
    }

    private WorkflowExecution mapExecution(ResultSet rs, int row) throws SQLException {
        WorkflowExecution exec = new WorkflowExecution();
        exec.setId(rs.getString("id"));
        exec.setWorkflowId(rs.getString("workflow_id"));
        exec.setSessionId(rs.getString("session_id"));
        exec.setPrompt(rs.getString("prompt"));
        exec.setStatus(rs.getString("status"));
        exec.setCurrentNodeId(rs.getString("current_node_id"));
        exec.setRetryCount(rs.getInt("retry_count"));
        exec.setErrorMessage(rs.getString("error_message"));
        exec.setStartedAt(toInstant(rs.getTimestamp("started_at")));
        exec.setFinishedAt(toInstant(rs.getTimestamp("finished_at")));
        exec.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
        return exec;
    }

    private NodeResult mapNodeResult(ResultSet rs, int row) throws SQLException {
        NodeResult r = new NodeResult();
        r.setId(rs.getString("id"));
        r.setExecutionId(rs.getString("execution_id"));
        r.setNodeId(rs.getString("node_id"));
        r.setStatus(rs.getString("status"));
        r.setInputText(rs.getString("input_text"));
        r.setOutputText(rs.getString("output_text"));
        r.setErrorMessage(rs.getString("error_message"));
        r.setCheckResultJson(rs.getString("check_result"));
        r.setStartedAt(toInstant(rs.getTimestamp("started_at")));
        r.setFinishedAt(toInstant(rs.getTimestamp("finished_at")));
        return r;
    }

    private Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }
}
