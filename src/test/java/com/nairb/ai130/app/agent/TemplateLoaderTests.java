package com.nairb.ai130.app.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateLoaderTests {

    private final TemplateLoader loader = new TemplateLoader();

    @Test
    void loadsLoopAndReactTemplatesFromClasspath() {
        assertTrue(loader.load("loop", "analyzer").contains("Analyzer"));
        assertTrue(loader.load("react", "evaluator").contains("Evaluator"));
    }

    @Test
    void rejectsUnknownAndUnsafeTemplatePaths() {
        assertThrows(IllegalArgumentException.class, () -> loader.load("loop", "unknown"));
        assertThrows(IllegalArgumentException.class, () -> loader.load("../loop", "analyzer"));
    }
}
