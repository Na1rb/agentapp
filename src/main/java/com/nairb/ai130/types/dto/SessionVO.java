package com.nairb.ai130.types.dto;

public class SessionVO {
    private String chatId;
    private String title;
    private String createdAt;
    private String updatedAt;
    private int documentCount;

    public SessionVO() {}

    public SessionVO(String chatId, String title, int documentCount) {
        this(chatId, title, null, null, documentCount);
    }

    public SessionVO(String chatId, String title, String createdAt, String updatedAt, int documentCount) {
        this.chatId = chatId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.documentCount = documentCount;
    }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public int getDocumentCount() { return documentCount; }
    public void setDocumentCount(int documentCount) { this.documentCount = documentCount; }
}
