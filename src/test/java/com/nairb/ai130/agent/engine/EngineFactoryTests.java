package com.nairb.ai130.agent.engine;

import com.nairb.ai130.domain.entity.AgentFlowStep;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EngineFactoryTests {

    @Test
    void selectsRegisteredStrategyAndFallsBackToNormal() {
        ExecutionEngine normal = engine("normal");
        ExecutionEngine loop = engine("loop");
        ExecutionEngine react = engine("react");
        EngineFactory factory = new EngineFactory(List.of(normal, loop, react));

        assertSame(loop, factory.getEngine(" LOOP "));
        assertSame(react, factory.getEngine("react"));
        assertSame(normal, factory.getEngine("unknown"));
        assertSame(normal, factory.getEngine(null));
    }

    @Test
    void rejectsDuplicateStrategiesAndMissingNormalEngine() {
        assertThrows(IllegalStateException.class,
                () -> new EngineFactory(List.of(engine("normal"), engine("NORMAL"))));
        assertThrows(IllegalStateException.class,
                () -> new EngineFactory(List.of(engine("loop"))));
    }

    private ExecutionEngine engine(String strategy) {
        return new ExecutionEngine() {
            @Override
            public String strategy() {
                return strategy;
            }

            @Override
            public Flux<ServerSentEvent<String>> execute(String sessionId,
                                                          String userInput,
                                                          List<AgentFlowStep> steps,
                                                          String modelCode) {
                return Flux.empty();
            }
        };
    }
}
