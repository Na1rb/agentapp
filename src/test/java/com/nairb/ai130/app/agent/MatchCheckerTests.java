package com.nairb.ai130.app.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchCheckerTests {

    private final MatchChecker checker = new MatchChecker();

    @Test
    void normalAlwaysMatchesAndSpecializedStrategiesCheckIntent() {
        assertTrue(checker.check("normal", "hello").matched());
        assertTrue(checker.check("loop", "写一份深度研究分析报告").matched());
        assertFalse(checker.check("loop", "你好").matched());
        assertTrue(checker.check("react", "查询当前天气").matched());
        assertFalse(checker.check("react", "帮我润色这句话").matched());
    }
}
