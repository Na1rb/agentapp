package com.nairb.ai130.domain.agent.service.impl.framework;

import java.util.HashMap;
import java.util.Map;

/**
 * 策略路由基础抽象类
 */
public abstract class AbstractStrategyRouter<I, C, O> implements StrategyHandler<I, C, O> {

    @Override
    public O apply(I request, C context) throws Exception {
        return doApply(request, context);
    }

    protected abstract O doApply(I request, C context) throws Exception;

    /**
     * 动态上下文基类
     */
    public static class DynamicContext {
        private int step = 1;
        private int maxStep = 3;
        private String currentTask;
        private boolean isCompleted;
        private Map<String, Object> dataObjects = new HashMap<>();

        public int getStep() { return step; }
        public void setStep(int step) { this.step = step; }
        public int getMaxStep() { return maxStep; }
        public void setMaxStep(int maxStep) { this.maxStep = maxStep; }
        public String getCurrentTask() { return currentTask; }
        public void setCurrentTask(String currentTask) { this.currentTask = currentTask; }
        public boolean isCompleted() { return isCompleted; }
        public void setCompleted(boolean completed) { isCompleted = completed; }

        @SuppressWarnings("unchecked")
        public <T> T getValue(String key) { return (T) dataObjects.get(key); }
        public <T> void setValue(String key, T value) { dataObjects.put(key, value); }
    }
}
