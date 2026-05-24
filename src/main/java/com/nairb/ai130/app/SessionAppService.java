package com.nairb.ai130.app;

import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.infrastructure.mapper.DocumentEmbeddingMapper;
import com.nairb.ai130.infrastructure.memory.RedisChatMemory;
import com.nairb.ai130.infrastructure.storage.LocalFileStorage;
import com.nairb.ai130.types.dto.MessageVO;
import com.nairb.ai130.types.dto.SessionInfoVO;
import com.nairb.ai130.types.dto.SessionVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class SessionAppService {
    private static final Logger log = LoggerFactory.getLogger(SessionAppService.class);

    private final LocalFileStorage storage;
    private final RedisChatMemory memory;
    private final DocumentEmbeddingMapper documentEmbeddingMapper;

    public SessionAppService(LocalFileStorage storage, RedisChatMemory memory, DocumentEmbeddingMapper documentEmbeddingMapper) {
        this.storage = storage; this.memory = memory; this.documentEmbeddingMapper = documentEmbeddingMapper;
    }

    public List<SessionVO> list() {
        return storage.getAllChatFiles().entrySet().stream().map(e -> {
            String id = e.getKey(), path = e.getValue();
            int count = 0;
            try { count = documentEmbeddingMapper.countByChatId(id); } catch (Exception ignored) {}
            String name = path.contains("-") ? path.substring(path.indexOf("-") + 1) : path;
            return new SessionVO(id, name, count);
        }).toList();
    }

    public SessionInfoVO info(String chatId) {
        Resource r = storage.getFile(chatId);
        if (r == null) return new SessionInfoVO(false);
        int count = 0;
        try { count = documentEmbeddingMapper.countByChatId(chatId); } catch (Exception ignored) {}
        String name = r.getFilename();
        if (name != null && name.contains("-")) name = name.substring(name.indexOf("-") + 1);
        return new SessionInfoVO(chatId, name != null ? name : "unknown", count);
    }

    public List<MessageVO> messages(String chatId, int limit) {
        return memory.get(chatId, limit).stream().map(m -> {
            String role = m.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER ? "user" : "assistant";
            return new MessageVO(role, m.getText() != null ? m.getText() : "");
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(String chatId) {
        if (chatId == null || chatId.isBlank()) throw new BusinessException(400, "chatId required");
        boolean hasFile = storage.hasFile(chatId);
        int count = documentEmbeddingMapper.countByChatId(chatId);
        if (!hasFile && count == 0) throw new BusinessException(404, "session not found");

        documentEmbeddingMapper.deleteByChatId(chatId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                storage.deleteFile(chatId);
                try { memory.clear(chatId); } catch (Exception e) { log.error("Clear memory failed", e); }
            }
        });
    }
}
