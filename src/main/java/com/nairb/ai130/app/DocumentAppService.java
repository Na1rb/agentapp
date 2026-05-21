package com.nairb.ai130.app;

import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.domain.service.DocumentReader;
import com.nairb.ai130.infrastructure.storage.LocalFileStorage;
import com.nairb.ai130.types.dto.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    public DocumentAppService(@Qualifier("pdfReader") DocumentReader pdfReader,
                              @Qualifier("tikaReader") DocumentReader tikaReader,
                              VectorStore vectorStore, TextSplitter textSplitter, LocalFileStorage fileStorage) {
        this.pdfReader = pdfReader; this.tikaReader = tikaReader;
        this.vectorStore = vectorStore; this.textSplitter = textSplitter; this.fileStorage = fileStorage;
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
}
