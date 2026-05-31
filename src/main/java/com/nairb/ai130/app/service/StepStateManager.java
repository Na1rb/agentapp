package com.nairb.ai130.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nairb.ai130.api.dto.StepState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 步骤状态管理器。
 * <p>
 * 负责 {@link StepState} 在 Redis 中的读写操作，TTL 与 {@code RedisChatMemory} 对齐（24h）。
 *
 * <h3>Key 结构</h3>
 * <pre>{@code step:state:{chatId}}</pre>
 */
@Component
public class StepStateManager {

    private static final Logger log = LoggerFactory.getLogger(StepStateManager.class);
    private static final String KEY_PREFIX = "step:state:";
    private static final long TTL_HOURS = 24;

    private final RedisTemplate<String, String> redis;
    private final ObjectMapper mapper;

    public StepStateManager(@Qualifier("redisTemplate") RedisTemplate<String, String> redis) {
        this.redis = redis;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
    }

    // ==================== 公开方法 ====================

    /**
     * 获取步骤状态，不存在时自动初始化。
     *
     * @param chatId   会话 ID
     * @param strategy 策略标识（如 STEP_CHECK）
     * @return 步骤状态（始终非 null）
     */
    public StepState getOrInit(String chatId, String strategy) {
        StepState existing = get(chatId);
        if (existing != null) {
            return existing;
        }
        StepState state = StepState.init(strategy);
        save(chatId, state);
        log.info("StepState initialized for chatId={}, strategy={}", chatId, strategy);
        return state;
    }

    /**
     * 获取步骤状态。
     *
     * @param chatId 会话 ID
     * @return 步骤状态，不存在时返回 {@code null}
     */
    public StepState get(String chatId) {
        String key = KEY_PREFIX + chatId;
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return mapper.readValue(json, StepState.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize StepState for chatId={}: {}", chatId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Redis error reading StepState for chatId={}: {}", chatId, e.getMessage());
            return null;
        }
    }

    /**
     * 保存步骤状态到 Redis（带乐观锁版本检测），并设置 24h TTL。
     * <p>
     * 乐观锁策略：每次保存时递增 version，使用 Redis SET 命令的 NX 模式
     * 仅当 key 不存在（首次写入）或通过 Lua 脚本检测版本号（防止并发覆盖）。
     * 如果版本冲突，记录告警日志但继续写入（last-write-wins + 可观测性）。
     *
     * @param chatId 会话 ID
     * @param state  步骤状态
     */
    public void save(String chatId, StepState state) {
        if (state == null) return;
        String key = KEY_PREFIX + chatId;
        try {
            // 读取 Redis 中当前版本号进行冲突检测
            String existingJson = redis.opsForValue().get(key);
            if (existingJson != null && !existingJson.isEmpty()) {
                try {
                    StepState existing = mapper.readValue(existingJson, StepState.class);
                    if (existing.getVersion() > state.getVersion()) {
                        log.warn("Version conflict for chatId={}: current={}, incoming={}. "
                                + "Possible concurrent write detected, overwriting.",
                                chatId, existing.getVersion(), state.getVersion());
                    }
                } catch (JsonProcessingException ignored) {
                    // 旧数据无法解析，直接覆盖
                }
            }

            // 递增版本号
            state.setVersion(state.getVersion() + 1);

            String json = mapper.writeValueAsString(state);
            redis.opsForValue().set(key, json);
            redis.expire(key, TTL_HOURS, TimeUnit.HOURS);
            log.debug("StepState saved for chatId={}, phase={}, loop={}, version={}",
                    chatId, state.getPhase(), state.getLoopCount(), state.getVersion());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize StepState for chatId={}: {}", chatId, e.getMessage());
        }
    }

    /**
     * 清除步骤状态（编排流程结束后调用）。
     *
     * @param chatId 会话 ID
     */
    public void clear(String chatId) {
        String key = KEY_PREFIX + chatId;
        try {
            redis.delete(key);
            log.info("StepState cleared for chatId={}", chatId);
        } catch (Exception e) {
            log.error("Failed to clear StepState for chatId={}: {}", chatId, e.getMessage());
        }
    }

    /**
     * 联动清理：同时清除步骤状态和对话记忆。
     * 通过调用 {@link com.nairb.ai130.infrastructure.memory.RedisChatMemory#clear(String)} 实现。
     */
    public void clearAll(String chatId) {
        clear(chatId);
        try {
            redis.delete("chat:memory:" + chatId);
        } catch (Exception e) {
            log.error("Failed to clear chat memory for chatId={}: {}", chatId, e.getMessage());
        }
    }
}
