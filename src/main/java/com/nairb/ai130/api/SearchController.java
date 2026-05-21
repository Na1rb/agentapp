package com.nairb.ai130.api;

import com.nairb.ai130.app.SearchAppService;
import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.types.dto.SearchResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SearchController {
    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchAppService searchService;

    public SearchController(SearchAppService searchService) { this.searchService = searchService; }

    @GetMapping("/search/fuzzy")
    public ResponseEntity<ApiResponse<List<SearchResultVO>>> fuzzySearch(
            @RequestParam("query") String query,
            @RequestParam(required = false) String chatId,
            @RequestParam(value = "kbId", required = false) String kbId,
            @RequestParam(defaultValue = "5") int topK) {
        try { return ResponseEntity.ok(ApiResponse.success(searchService.fuzzySearch(query, chatId, kbId, topK))); }
        catch (Exception e) { return ResponseEntity.badRequest().body(ApiResponse.badRequest(e.getMessage())); }
    }
}
