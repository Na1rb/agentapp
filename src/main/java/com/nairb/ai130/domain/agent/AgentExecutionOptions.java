package com.nairb.ai130.domain.agent;

public record AgentExecutionOptions(Long executionLogId, int maxRound, int maxPace) {

    public AgentExecutionOptions {
        maxRound = Math.max(1, Math.min(maxRound, 20));
        maxPace = Math.max(1, Math.min(maxPace, 50));
    }

    public static AgentExecutionOptions defaults(Long executionLogId) {
        return new AgentExecutionOptions(executionLogId, 5, 10);
    }
}
