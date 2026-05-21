package com.nairb.ai130.app;

import com.nairb.ai130.types.dto.SearchResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SearchAppService {
    private static final Logger log = LoggerFactory.getLogger(SearchAppService.class);

    private final JdbcTemplate jdbc;
    private final VectorStore vectorStore;

    public SearchAppService(JdbcTemplate jdbc, VectorStore vectorStore) { this.jdbc = jdbc; this.vectorStore = vectorStore; }

    public List<SearchResultVO> fuzzySearch(String query, String chatId, int topK) {
        Set<String> seen = new HashSet<>();
        List<SearchResultVO> results = new ArrayList<>();

        try {
            for (Document d : vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(topK).similarityThreshold(0.5).build())) {
                if (d.getId() != null && seen.add(d.getId()))
                    results.add(new SearchResultVO(d.getId(), d.getText(), (double) d.getMetadata().getOrDefault("distance", 0), "vector"));
            }
        } catch (Exception e) { log.warn("Vector search failed", e); }

        try {
            String sql = "SELECT id, text FROM document_embeddings WHERE text ILIKE ?";
            List<Object> params = new ArrayList<>(); params.add("%" + query + "%");
            if (chatId != null && !chatId.isEmpty()) { sql += " AND metadata->>'chat_id' = ?"; params.add(chatId); }
            sql += " LIMIT ?"; params.add(topK);
            for (Map<String, Object> row : jdbc.queryForList(sql, params.toArray())) {
                String id = String.valueOf(row.get("id"));
                if (seen.add(id)) results.add(new SearchResultVO(id, (String) row.get("text"), 0, "text"));
            }
        } catch (Exception e) { log.error("Text search failed", e); }
        return results;
    }
}
