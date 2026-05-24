package com.nairb.ai130.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

/**
 * 自定义线程池配置 —— 用于 Agent 异步执行
 */
@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor agentThreadPool() {
        return new ThreadPoolExecutor(
                20,                  // 核心线程数
                50,                  // 最大线程数
                60L,                 // 空闲线程存活时间
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(5000),  // 阻塞队列
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略：主线程执行
        );
    }
}
