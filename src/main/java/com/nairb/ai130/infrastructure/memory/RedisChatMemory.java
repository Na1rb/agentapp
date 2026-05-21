package com.nairb.ai130.infrastructure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RedisChatMemory implements ChatMemory {
    private static final Logger log = LoggerFactory.getLogger(RedisChatMemory.class);
    private static final String KEY_PREFIX = "chat:memory:";
    private static final long TTL = 24;
    private final RedisTemplate<String, String> redis;
    private final ObjectMapper mapper;

    public RedisChatMemory(RedisTemplate<String, String> redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    @Override
    public void add(String convId, List<Message> msgs) {
        if (msgs == null || msgs.isEmpty()) return;
        String key = KEY_PREFIX + convId;
        for (Message m : msgs) {
            try { redis.opsForList().rightPush(key, mapper.writeValueAsString(StoredMessage.from(m))); }
            catch (JsonProcessingException e) { log.error("Serialize failed", e); }
        }
        try { redis.expire(key, TTL, TimeUnit.HOURS); } catch (Exception ignored) {}
    }

    @Override
    public List<Message> get(String convId, int lastN) {
        String key = KEY_PREFIX + convId;
        List<String> raw;
        try { raw = redis.opsForList().range(key, -lastN, -1); } catch (Exception e) { return List.of(); }
        if (raw == null) return List.of();
        List<Message> msgs = new ArrayList<>();
        for (String r : raw) {
            try { Message m = mapper.readValue(r, StoredMessage.class).toMessage(); if (m != null) msgs.add(m); }
            catch (Exception ignored) {}
        }
        return msgs;
    }

    @Override
    public List<Message> get(String convId) {
        String key = KEY_PREFIX + convId;
        List<String> raw;
        try { raw = redis.opsForList().range(key, 0, -1); } catch (Exception e) { return List.of(); }
        if (raw == null) return List.of();
        List<Message> msgs = new ArrayList<>();
        for (String r : raw) {
            try { Message m = mapper.readValue(r, StoredMessage.class).toMessage(); if (m != null) msgs.add(m); }
            catch (Exception ignored) {}
        }
        return msgs;
    }

    @Override
    public void clear(String convId) { try { redis.delete(KEY_PREFIX + convId); } catch (Exception e) { log.error("Clear failed", e); } }

    private static class StoredMessage {
        private MessageType messageType;
        private String text;
        private Map<String, Object> metadata;
        public StoredMessage() {}
        static StoredMessage from(Message m) { var s = new StoredMessage(); s.messageType = m.getMessageType(); s.text = m.getText(); s.metadata = m.getMetadata(); return s; }
        Message toMessage() { if (messageType == null) return null; String t = text != null ? text : ""; return switch (messageType) { case USER -> new UserMessage(t); default -> new AssistantMessage(t); }; }
        public MessageType getMessageType() { return messageType; } public void setMessageType(MessageType v) { messageType = v; }
        public String getText() { return text; } public void setText(String v) { text = v; }
        public Map<String, Object> getMetadata() { return metadata; } public void setMetadata(Map<String, Object> v) { metadata = v; }
    }
}
