package com.nairb.ai130.app;

import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.domain.service.DocumentReader;
import com.nairb.ai130.infrastructure.storage.LocalFileStorage;
import com.nairb.ai130.infrastructure.storage.LocalFileStorage;
import com.nairb.ai130.types.dto.DocumentVO;
import com.nairb.ai130.types.dto.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DocumentAppService {
    private static final Logger log = LoggerFactory.getLogger(DocumentAppService.class);

    private final DocumentReader pdfReader;
    private final DocumentReader tikaReader;
    private final VectorStore vectorStore;
    private final TextSplitter textSplitter;
    private final LocalFileStorage fileStorage;
    private final JdbcTemplate jdbc;

    public DocumentAppService(@Qualifier("pdfReader") DocumentReader pdfReader,
                              @Qualifier("tikaReader") DocumentReader tikaReader,
                              VectorStore vectorStore, TextSplitter textSplitter,
                              LocalFileStorage fileStorage, JdbcTemplate jdbc) {
        this.pdfReader = pdfReader; this.tikaReader = tikaReader;
        this.vectorStore = vectorStore; this.textSplitter = textSplitter;
        this.fileStorage = fileStorage; this.jdbc = jdbc;
    }

    public UploadResponse upload(MultipartFile file, String ext) {
        String fileName = file.getOriginalFilename();
        DocumentReader reader = "pdf".equals(ext) ? pdfReader : tikaReader;
        List<Document> docs = reader.read(file.getResource());
        log.info("Parsed {} ({}): {} segments", fileName, ext, docs.size());

        String chatId = UUID.randomUUID().toString();
        List<Document> metaDocs = docs.stream().map(d -> {
            Map<String, Object> m = new HashMap<>(d.getMetadata() != null ? d.getMetadata() : Map.of());
            m.put("file_name", fileName); m.put("chat_id", chatId);
            return new Document(d.getId(), d.getText(), m);
        }).collect(Collectors.toList());

        List<Document> split = textSplitter.apply(metaDocs);
        int batch = 10;
        for (int i = 0; i < split.size(); i += batch) vectorStore.add(split.subList(i, Math.min(i + batch, split.size())));
        if (!fileStorage.save(chatId, file)) throw new BusinessException("Failed to save file");
        return new UploadResponse(chatId, fileName, split.size());
    }

    /**
     * 获取所有已上传文档的列表。
     */
    public List<DocumentVO> listDocuments() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<DocumentVO> list = new ArrayList<>();
        for (Map.Entry<String, String> entry : fileStorage.getAllChatFiles().entrySet()) {
            String chatId = entry.getKey();
            String path = entry.getValue();
            String fileName = path.contains("-") ? path.substring(path.indexOf("-") + 1) : path;

            LocalFileStorage.FileInfo info = fileStorage.getFileInfo(chatId);
            long fileSize = info != null ? info.size() : 0;
            String uploadedAt = info != null ? sdf.format(new Date(info.lastModified())) : "";

            list.add(new DocumentVO(chatId, fileName, fileSize, chatId, uploadedAt));
        }
        // 按上传时间降序
        list.sort((a, b) -> b.getUploadedAt().compareTo(a.getUploadedAt()));
        return list;
    }

    /**
     * 删除文档：删除文件、向量嵌入和 Redis 记忆。
     */
    public void deleteDocument(String chatId) {
        if (chatId == null || chatId.isBlank()) {
            throw new BusinessException(400, "chatId is required");
        }
        if (!fileStorage.hasFile(chatId)) {
            throw new BusinessException(404, "document not found");
        }
        // 删除向量库中的嵌入
        try {
            jdbc.update("DELETE FROM document_embeddings WHERE metadata->>'chat_id' = ?", chatId);
        } catch (Exception e) {
            log.warn("Failed to delete vector embeddings for chatId={}: {}", chatId, e.getMessage());
        }
        // 删除物理文件
        fileStorage.deleteFile(chatId);
        log.info("Document deleted: chatId={}", chatId);
    }
}
