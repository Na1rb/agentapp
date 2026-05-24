package com.nairb.ai130.types.vo;

public class UploadResponse {
    private String chatId;
    private String fileName;
    private int documentCount;

    public UploadResponse() {}
    public UploadResponse(String chatId, String fileName, int documentCount) {
        this.chatId = chatId; this.fileName = fileName; this.documentCount = documentCount;
    }
    public String getChatId() { return chatId; }
    public String getFileName() { return fileName; }
    public int getDocumentCount() { return documentCount; }
}
