package com.nairb.ai130.infrastructure.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Component
public class LocalFileStorage {
    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private final Map<String, String> chatFiles = new HashMap<>();
    private static final String STORAGE_DIR = "pdf-storage";
    private static final String PROPERTIES_FILE = "chat-pdf.properties";

    @PostConstruct
    private void init() {
        try {
            Path p = Paths.get(STORAGE_DIR);
            if (!Files.exists(p)) Files.createDirectories(p);
            File pf = new File(PROPERTIES_FILE);
            if (pf.exists()) {
                Properties props = new Properties();
                props.load(new BufferedReader(new InputStreamReader(new FileInputStream(pf), StandardCharsets.UTF_8)));
                chatFiles.putAll(new HashMap<>((Map) props));
            }
        } catch (IOException e) { log.error("Init failed", e); }
    }

    @PreDestroy
    private void persist() {
        try {
            Properties props = new Properties();
            props.putAll(chatFiles);
            props.store(new OutputStreamWriter(new FileOutputStream(PROPERTIES_FILE), StandardCharsets.UTF_8), "PDF Chat Storage");
        } catch (IOException e) { log.error("Persist failed", e); }
    }

    public boolean save(String chatId, MultipartFile file) {
        String fn = file.getOriginalFilename();
        if (fn == null) return false;
        try {
            String saved = chatId + "-" + fn.replaceAll("[^a-zA-Z0-9.-]", "_");
            Path target = Paths.get(STORAGE_DIR, saved);
            Files.copy(file.getInputStream(), target);
            chatFiles.put(chatId, target.toString());
            return true;
        } catch (IOException e) { log.error("Save failed", e); return false; }
    }

    public Resource getFile(String chatId) {
        String p = chatFiles.get(chatId);
        if (p != null) { File f = new File(p); if (f.exists()) return new FileSystemResource(f); }
        return null;
    }

    public boolean deleteFile(String chatId) {
        String p = chatFiles.remove(chatId);
        if (p != null) { try { Files.deleteIfExists(Paths.get(p)); return true; } catch (IOException e) { log.error("Delete failed", e); } }
        return false;
    }

    public boolean hasFile(String chatId) { return chatFiles.containsKey(chatId); }
    public Map<String, String> getAllChatFiles() { return new HashMap<>(chatFiles); }

    /**
     * 重命名已上传的文件（仅更新显示名称，物理文件名不变）。
     * @return true 如果 chatId 存在
     */
    public boolean renameFile(String chatId, String newDisplayName) {
        String currentPath = chatFiles.get(chatId);
        if (currentPath == null) return false;
        // 保持物理文件名不变，仅更新 path 中的显示名称部分
        String cleanName = newDisplayName.replaceAll("[^a-zA-Z0-9.\\-\\u4e00-\\u9fff_]", "_");
        String prefix = chatId + "-";
        String newFilename = prefix + cleanName;
        Path newTarget = Paths.get(STORAGE_DIR, newFilename);
        chatFiles.put(chatId, newTarget.toString());
        return true;
    }

    /**
     * 获取文件的元数据（大小、最后修改时间）。
     */
    public FileInfo getFileInfo(String chatId) {
        String p = chatFiles.get(chatId);
        if (p == null) return null;
        File f = new File(p);
        if (!f.exists()) return null;
        return new FileInfo(f.length(), f.lastModified());
    }

    /** 文件元数据值对象 */
    public record FileInfo(long size, long lastModified) {}

    public void clearAll() {
        try {
            Path sp = Paths.get(STORAGE_DIR);
            if (Files.exists(sp)) Files.walk(sp).filter(Files::isRegularFile).forEach(p -> { try { Files.delete(p); } catch (IOException e) { log.error("Del failed", e); } });
            chatFiles.clear();
        } catch (IOException e) { log.error("ClearAll failed", e); }
    }
}
