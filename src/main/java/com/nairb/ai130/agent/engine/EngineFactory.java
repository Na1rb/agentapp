package com.nairb.ai130.agent.engine;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class EngineFactory {

    private final Map<String, ExecutionEngine> engines;

    public EngineFactory(List<ExecutionEngine> availableEngines) {
        Map<String, ExecutionEngine> registered = new LinkedHashMap<>();
        for (ExecutionEngine engine : availableEngines) {
            String strategy = normalize(engine.strategy());
            ExecutionEngine previous = registered.putIfAbsent(strategy, engine);
            if (previous != null) {
                throw new IllegalStateException("Duplicate Agent execution strategy: " + strategy);
            }
        }
        if (!registered.containsKey("normal")) {
            throw new IllegalStateException("Normal Agent execution strategy is required");
        }
        this.engines = Map.copyOf(registered);
    }

    public ExecutionEngine getEngine(String strategy) {
        return engines.getOrDefault(normalize(strategy), engines.get("normal"));
    }

    private String normalize(String strategy) {
        return strategy == null || strategy.isBlank()
                ? "normal"
                : strategy.trim().toLowerCase(Locale.ROOT);
    }
}
