package com.project.demo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AppAsyncConfig {

    /**
     * STOMP 브로드캐스트 전용 비동기 스레드 풀
     * 컨슈머 스레드가 전송 완료를 기다리지 않도록 분리합니다.
     */
    @Bean(name = "broadcastExecutor")
    public Executor broadcastExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("broadcast-");
        executor.initialize();
        return executor;
    }
}
