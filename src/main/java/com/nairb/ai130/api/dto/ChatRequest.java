package com.nairb.ai130.api.dto;

import java.util.Collections;
import java.util.List;

/**
 * 对话请求 DTO。
 * <p>
 * 用于 POST /api/chat，支持携带工具选择列表。
 * 同时兼容 GET /api/dchat 的简化参数。
 */
public class ChatRequest {

    /** 用户输入（必填） */
    private String prompt;

    /** 会话 ID（可选，为空时自动生成） */
    private String chatId;

    /** 工具名称列表（可选，对应 mcp_tool_config.tool_name） */
    private List<String> toolIds;

    /** 模型 code（可选，为空时使用默认模型） */
    private String modelCode;

    /** 角色模板 code（可选，为空时使用默认「通用助手」） */
    private String promptCode;

    /** 编排策略（可选，如 STEP_CHECK 启用分步编排；为空则使用默认单步模式） */
    private String strategy;

    public ChatRequest() {}

    // ==================== Getters / Setters ====================

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public List<String> getToolIds() {
        return toolIds != null ? toolIds : Collections.emptyList();
    }

    public void setToolIds(List<String> toolIds) {
        this.toolIds = toolIds;
    }

    public String getModelCode() {
        return modelCode;
    }

    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getPromptCode() {
        return promptCode;
    }

    public void setPromptCode(String promptCode) {
        this.promptCode = promptCode;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    @Override
    public String toString() {
        return "ChatRequest{" +
                "prompt='" + (prompt != null ? prompt.substring(0, Math.min(prompt.length(), 50)) : null) + "'" +
                ", chatId='" + chatId + '\'' +
                ", toolIds=" + getToolIds() +
                ", modelCode='" + modelCode + '\'' +
                ", promptCode='" + promptCode + '\'' +
                ", strategy='" + strategy + '\'' +
                '}';
    }
}
