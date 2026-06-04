package com.nairb.ai130.domain.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mutable runtime state shared by the nodes of an Agent execution strategy.
 */
public class ExecuteContext {

    private String sessionId;
    private Long executionLogId;
    private String userInput;
    private String strategy = "normal";
    private int round = 1;
    private int maxRound = 5;
    private int pace = 1;
    private int maxPace = 10;
    private StringBuilder executionHistory = new StringBuilder();
    private String lastAnalysis;
    private String lastResult;
    private String lastVerdict;
    private String currentRole;
    private List<Map<String, String>> nodeResults = new ArrayList<>();

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public Long getExecutionLogId() { return executionLogId; }
    public void setExecutionLogId(Long executionLogId) { this.executionLogId = executionLogId; }

    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public int getRound() { return round; }
    public void setRound(int round) { this.round = round; }

    public int getMaxRound() { return maxRound; }
    public void setMaxRound(int maxRound) { this.maxRound = maxRound; }

    public int getPace() { return pace; }
    public void setPace(int pace) { this.pace = pace; }

    public int getMaxPace() { return maxPace; }
    public void setMaxPace(int maxPace) { this.maxPace = maxPace; }

    public StringBuilder getExecutionHistory() { return executionHistory; }
    public void setExecutionHistory(StringBuilder executionHistory) {
        this.executionHistory = executionHistory != null ? executionHistory : new StringBuilder();
    }

    public String getLastAnalysis() { return lastAnalysis; }
    public void setLastAnalysis(String lastAnalysis) { this.lastAnalysis = lastAnalysis; }

    public String getLastResult() { return lastResult; }
    public void setLastResult(String lastResult) { this.lastResult = lastResult; }

    public String getLastVerdict() { return lastVerdict; }
    public void setLastVerdict(String lastVerdict) { this.lastVerdict = lastVerdict; }

    public String getCurrentRole() { return currentRole; }
    public void setCurrentRole(String currentRole) { this.currentRole = currentRole; }

    public List<Map<String, String>> getNodeResults() { return nodeResults; }
    public void setNodeResults(List<Map<String, String>> nodeResults) {
        this.nodeResults = nodeResults != null ? new ArrayList<>(nodeResults) : new ArrayList<>();
    }
}
