package com.nairb.ai130.domain.workflow;

import java.util.List;

/**
 * 结构化检查结果。
 * <p>
 * Check 节点输出的标准化判定，用于条件边路由和回退决策。
 * <pre>
 * {
 *   "passed": false,
 *   "score": 0.35,
 *   "reasons": ["代码缺少异常处理", "未验证输入边界"],
 *   "suggestion": "需要补充 try-catch 和参数校验"
 * }
 * </pre>
 */
public class CheckResult {

    /** 是否通过检查 */
    private boolean passed;

    /** 质量评分（0.0 ~ 1.0） */
    private double score;

    /** 未通过的具体原因列表 */
    private List<String> reasons;

    /** 改进建议 */
    private String suggestion;

    public CheckResult() {}

    public CheckResult(boolean passed, double score, List<String> reasons, String suggestion) {
        this.passed = passed;
        this.score = score;
        this.reasons = reasons;
        this.suggestion = suggestion;
    }

    // ==================== 便捷工厂 ====================

    public static CheckResult pass() {
        return new CheckResult(true, 1.0, List.of(), null);
    }

    public static CheckResult fail(List<String> reasons, String suggestion) {
        return new CheckResult(false, 0.0, reasons, suggestion);
    }

    // ==================== Getters / Setters ====================

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public double getScore() { return score; }
    public void setScore(double score) { this.score = score; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public String getSuggestion() { return suggestion; }
    public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
}
