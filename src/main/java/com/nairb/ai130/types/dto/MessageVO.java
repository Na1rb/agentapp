package com.nairb.ai130.types.dto;

public class MessageVO {
    private String role;
    private String content;

    public MessageVO() {}
    public MessageVO(String role, String content) { this.role = role; this.content = content; }
    public String getRole() { return role; }
    public String getContent() { return content; }
}
