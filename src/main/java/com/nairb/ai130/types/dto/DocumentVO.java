package com.nairb.ai130.types.dto;

/**
 * 文档列表响应 DTO。
 * <p>
 * 对应前端 {@code DocumentFile} 类型：
 * <pre>{@code
 * interface DocumentFile {
 *   fileId: string
 *   fileName: string
 *   fileSize: number
 *   chatId: string
 *   uploadedAt: string
 * }
 * }</pre>
 */
public class DocumentVO {

    private String fileId;
    private String fileName;
    private long fileSize;
    private String chatId;
    private String uploadedAt;

    public DocumentVO() {}

    public DocumentVO(String fileId, String fileName, long fileSize, String chatId, String uploadedAt) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.chatId = chatId;
        this.uploadedAt = uploadedAt;
    }

    // ==================== Getters & Setters ====================

    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }

    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
}
