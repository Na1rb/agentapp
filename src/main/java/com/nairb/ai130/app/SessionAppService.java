package com.nairb.ai130.app;

import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.infrastructure.memory.RedisChatMemory;
import com.nairb.ai130.infrastructure.storage.LocalFileStorage;
import com.nairb.ai130.types.dto.MessageVO;
import com.nairb.ai130.types.dto.SessionInfoVO;
import com.nairb.ai130.types.dto.SessionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.util.List;

@Service
public class SessionAppService {
    private static final Logger log = LoggerFactory.getLogger(SessionAppService.class);

    private final LocalFileStorage storage;
    private final RedisChatMemory memory;
    private final JdbcTemplate jdbc;

    public SessionAppService(LocalFileStorage storage, RedisChatMemory memory, JdbcTemplate jdbc) {
        this.storage = storage; this.memory = memory; this.jdbc = jdbc;
    }

    /**
     * 列出指定用户的会话（从 user_session 表查询）。
     */
    public List<SessionVO> list(long userId) {
        String sql = "SELECT us.chat_id, us.title, us.created_at, us.updated_at FROM user_session us WHERE us.user_id = ? ORDER BY us.updated_at DESC";
        try {
            return jdbc.query(sql, (rs, row) -> {
                String chatId = rs.getString("chat_id");
                String title = rs.getString("title");
                Timestamp createdAt = rs.getTimestamp("created_at");
                Timestamp updatedAt = rs.getTimestamp("updated_at");
                Integer count = 0;
                try {
                    count = jdbc.queryForObject(
                            "SELECT COUNT(*) FROM vector_store WHERE metadata->>'chat_id' = ?",
                            Integer.class, chatId);
                } catch (Exception ignored) {}
                return new SessionVO(
                        chatId,
                        title,
                        createdAt != null ? createdAt.toInstant().toString() : null,
                        updatedAt != null ? updatedAt.toInstant().toString() : null,
                        count != null ? count : 0);
            }, userId);
        } catch (Exception e) {
            log.error("Failed to list sessions for userId={}", userId, e);
            return List.of();
        }
    }

    /**
     * 为用户创建一条会话记录。
     */
    public void createUserSession(long userId, String chatId, String title) {
        jdbc.update(
                "INSERT INTO user_session (user_id, chat_id, title) VALUES (?, ?, ?) " +
                "ON CONFLICT (chat_id) DO NOTHING",
                userId, chatId, title);
    }

    public SessionInfoVO info(String chatId) {
        Resource r = storage.getFile(chatId);
        if (r == null) return new SessionInfoVO(false);
        Integer count = 0;
        try { count = jdbc.queryForObject("SELECT COUNT(*) FROM vector_store WHERE metadata->>'chat_id' = ?", Integer.class, chatId); } catch (Exception ignored) {}
        String name = r.getFilename();
        if (name != null && name.contains("-")) name = name.substring(name.indexOf("-") + 1);
        return new SessionInfoVO(chatId, name != null ? name : "unknown", count != null ? count : 0);
    }

    public List<MessageVO> messages(String chatId, int limit) {
        return memory.get(chatId, limit).stream().map(m -> {
            String role = m.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER ? "user" : "assistant";
            return new MessageVO(role, m.getText() != null ? m.getText() : "");
        }).toList();
    }

    /**
     * 重命名会话。
     */
    public void rename(String chatId, String newName) {
        if (chatId == null || chatId.isBlank()) throw new BusinessException(400, "chatId required");
        if (newName == null || newName.isBlank()) throw new BusinessException(400, "name required");
        // 更新 user_session 表
        int updated = jdbc.update("UPDATE user_session SET title = ?, updated_at = CURRENT_TIMESTAMP WHERE chat_id = ?",
                newName.trim(), chatId);
        if (updated == 0) {
            // 会话尚未同步到后端（本地新建但未发消息），静默成功
            log.info("Session {} not yet persisted, rename skipped", chatId);
            return;
        }
        // 如果有物理文件也更新
        if (storage.hasFile(chatId)) {
            storage.renameFile(chatId, newName.trim());
        }
        log.info("Session renamed: chatId={}, newName={}", chatId, newName);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String chatId) {
        if (chatId == null || chatId.isBlank()) throw new BusinessException(400, "chatId required");

        // 删除接口保持幂等：不存在的会话视为已经删除，不再返回 404。
        // 删除向量嵌入
        jdbc.update("DELETE FROM vector_store WHERE metadata->>'chat_id' = ?", chatId);
        // 删除会话关联
        jdbc.update("DELETE FROM user_session WHERE chat_id = ?", chatId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { storage.deleteFile(chatId); } catch (Exception e) { log.error("Delete file failed", e); }
                try { memory.clear(chatId); } catch (Exception e) { log.error("Clear memory failed", e); }
            }
        });
    }
}
