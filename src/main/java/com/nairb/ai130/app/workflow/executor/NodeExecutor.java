package com.nairb.ai130.app.workflow.executor;

import com.nairb.ai130.domain.workflow.NodeResult;
import com.nairb.ai130.domain.workflow.WorkflowDefinition;
import com.nairb.ai130.domain.workflow.WorkflowExecution;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 工作流节点执行器接口。
 * <p>
 * 每种 {@link com.nairb.ai130.common.enums.WorkflowNodeType} 对应一个实现，
 * 负责执行节点逻辑并返回结果流。
 */
public interface NodeExecutor {

    /**
     * 执行当前节点。
     *
     * @param execution    当前执行记录
     * @param node         当前节点定义
     * @param definition   完整流程定义（用于查询上下游）
     * @param prevResults  已完成的前置节点结果列表
     * @param prompt       用户原始输入
     * @return 节点执行结果流（Flux 用于流式输出场景如 SYNTHESIZE）
     */
    Flux<NodeResult> execute(WorkflowExecution execution,
                             WorkflowDefinition.NodeDef node,
                             WorkflowDefinition definition,
                             List<NodeResult> prevResults,
                             String prompt);
}
