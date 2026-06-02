package com.nairb.ai130.api;

import com.nairb.ai130.app.DocumentAppService;
import com.nairb.ai130.common.exception.BusinessException;
import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.infrastructure.storage.LocalFileStorage;
import com.nairb.ai130.types.dto.DocumentVO;
import com.nairb.ai130.types.dto.UploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class DocumentController {
    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final Set<String> SUPPORTED = Set.of("pdf","docx","xlsx","pptx","odt","ods","odp","txt","csv","html","htm","xml","rtf","epub");

    private final DocumentAppService docService;
    private final LocalFileStorage storage;

    public DocumentController(DocumentAppService docService, LocalFileStorage storage) { this.docService = docService; this.storage = storage; }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<UploadResponse>> upload(@RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isEmpty()) return ResponseEntity.badRequest().body(ApiResponse.badRequest("File name empty"));
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
        if (!SUPPORTED.contains(ext)) return ResponseEntity.badRequest().body(ApiResponse.badRequest("Unsupported: ." + ext));
        try { return ResponseEntity.ok(ApiResponse.success("Upload succeeded", docService.upload(file, ext))); }
        catch (Exception e) { log.error("Upload failed", e); return ResponseEntity.internalServerError().body(ApiResponse.serverError(e.getMessage())); }
    }

    @GetMapping("/download/{chatId}")
    public ResponseEntity<Resource> download(@PathVariable String chatId) {
        Resource r = storage.getFile(chatId);
        if (r == null) return ResponseEntity.notFound().build();
        String name = r.getFilename();
        if (name != null && name.contains("-")) name = name.substring(name.indexOf("-") + 1);
        String enc = URLEncoder.encode(name, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + enc).body(r);
    }

    /**
     * 获取所有已上传文档列表。
     * <pre>GET /api/documents</pre>
     */
    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<DocumentVO>>> listDocuments() {
        try {
            List<DocumentVO> docs = docService.listDocuments();
            return ResponseEntity.ok(ApiResponse.success(docs));
        } catch (Exception e) {
            log.error("Failed to list documents", e);
            return ResponseEntity.internalServerError().body(ApiResponse.serverError(e.getMessage()));
        }
    }

    /**
     * 删除文档（含向量嵌入和文件）。
     * <pre>DELETE /api/document/{chatId}</pre>
     */
    @DeleteMapping("/document/{chatId}")
    public ResponseEntity<ApiResponse<String>> deleteDocument(@PathVariable String chatId) {
        try {
            docService.deleteDocument(chatId);
            return ResponseEntity.ok(ApiResponse.success("Document deleted"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getCode() >= 500 ? 500 : 400)
                    .body(ApiResponse.error(e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete document", e);
            return ResponseEntity.internalServerError().body(ApiResponse.serverError(e.getMessage()));
        }
    }
}
