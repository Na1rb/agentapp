package com.nairb.ai130.api;

import com.nairb.ai130.common.response.ApiResponse;
import com.nairb.ai130.domain.entity.AppUser;
import com.nairb.ai130.domain.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 最简单的用户系统。
 * <p>
 * 用户输入 ID 登录；没有 ID 则自动分配下一个顺序号。
 *
 * <pre>
 * POST /api/user/login    → {"id": 1}
 * POST /api/user/register → 自动分配 ID
 * </pre>
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final AppUserRepository userRepo;

    public UserController(AppUserRepository userRepo) {
        this.userRepo = userRepo;
    }

    /**
     * 登录：检查用户 ID 是否存在。
     */
    @PostMapping("/user/login")
    public ResponseEntity<ApiResponse<Map<String, Long>>> login(@RequestBody Map<String, Long> body) {
        Long id = body.get("id");
        if (id == null) {
            return ResponseEntity.badRequest().body(ApiResponse.badRequest("id is required"));
        }
        if (userRepo.findById(id).isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.notFound("用户 " + id + " 不存在，请先注册"));
        }
        log.info("User login: id={}", id);
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", id)));
    }

    /**
     * 注册：自动分配下一个顺序 ID。
     */
    @PostMapping("/user/register")
    public ResponseEntity<ApiResponse<Map<String, Long>>> register() {
        AppUser user = userRepo.create();
        log.info("User registered: id={}", user.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("id", user.getId())));
    }
}
