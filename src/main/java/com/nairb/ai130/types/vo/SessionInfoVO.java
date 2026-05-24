package com.nairb.ai130.types.vo;

public class SessionInfoVO {
    private boolean exists;
    private String chatId;
    private String fileName;
    private int documentCount;

    public SessionInfoVO() {}
    public SessionInfoVO(boolean exists) { this.exists = exists; }
    public SessionInfoVO(String chatId, String fileName, int documentCount) {
        this.exists = true; this.chatId = chatId; this.fileName = fileName; this.documentCount = documentCount;
    }
    public boolean isExists() { return exists; }
    public String getChatId() { return chatId; }
    public String getFileName() { return fileName; }
    public int getDocumentCount() { return documentCount; }
}
