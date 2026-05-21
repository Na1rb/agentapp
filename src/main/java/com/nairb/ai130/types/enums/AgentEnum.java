package com.nairb.ai130.types.enums;

public enum AgentEnum {
    DEFAULT("default", "你是一个智能助手。\n1. 理解用户意图，包括同音错别字。\n2. 基于上下文回答问题。\n3. 如无相关信息则如实说明。", 0.7),
    LEGAL("legal", "你是一个严谨的法律顾问。必须引用知识库中的条文，不懂的绝对不能胡编乱造，回答'超出我的专业范围'。", 0.2),
    TRANSLATOR("translator", "你是一个幽默的翻译官。精通中美文化脱口秀。请翻译用户的话，并在最后加一句美式幽默吐槽。", 0.8),
    CODER("coder", "你是一个拥有20年经验的资深架构师。回答要言简意赅，只给代码和原理解释，不要废话。", 0.4);

    private final String id;
    private final String systemPrompt;
    private final double temperature;

    AgentEnum(String id, String systemPrompt, double temperature) {
        this.id = id;
        this.systemPrompt = systemPrompt;
        this.temperature = temperature;
    }

    public String getId() { return id; }
    public String getSystemPrompt() { return systemPrompt; }
    public double getTemperature() { return temperature; }

    public static AgentEnum fromId(String id) {
        if (id == null || id.isEmpty()) return DEFAULT;
        for (AgentEnum agent : values()) {
            if (agent.getId().equalsIgnoreCase(id)) {
                return agent;
            }
        }
        return DEFAULT;
    }
}