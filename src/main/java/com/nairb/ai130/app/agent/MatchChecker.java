package com.nairb.ai130.app.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class MatchChecker {

    private static final List<String> LOOP_MARKERS = List.of(
            "分析", "报告", "研究", "方案", "优化", "迭代", "评估",
            "analyze", "report", "research", "plan", "optimize", "evaluate");
    private static final List<String> REACT_MARKERS = List.of(
            "实时", "当前", "最新", "天气", "查询", "搜索", "监控", "状态", "环境",
            "live", "current", "latest", "weather", "search", "monitor", "status");

    public MatchResult check(String strategy, String userInput) {
        String normalizedStrategy = strategy == null ? "normal" : strategy.toLowerCase(Locale.ROOT);
        String input = userInput == null ? "" : userInput.trim().toLowerCase(Locale.ROOT);
        if ("normal".equals(normalizedStrategy)) return MatchResult.allow();
        if (input.isBlank()) return MatchResult.reject("请输入具体任务后再启动 Agent。");
        if ("loop".equals(normalizedStrategy) && !containsAny(input, LOOP_MARKERS) && input.length() < 30) {
            return MatchResult.reject("该任务较简单，建议使用普通 Agent 或 AI 对话；Loop 更适合复杂分析与迭代优化任务。");
        }
        if ("react".equals(normalizedStrategy) && !containsAny(input, REACT_MARKERS)) {
            return MatchResult.reject("该任务不涉及明显的实时信息或环境变化，建议使用普通 Agent 或 Loop 策略。");
        }
        return MatchResult.allow();
    }

    private boolean containsAny(String input, List<String> markers) {
        return markers.stream().anyMatch(input::contains);
    }

    public record MatchResult(boolean matched, String suggestion) {
        public static MatchResult allow() { return new MatchResult(true, ""); }
        public static MatchResult reject(String suggestion) { return new MatchResult(false, suggestion); }
    }
}
