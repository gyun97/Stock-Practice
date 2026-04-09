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
        executor.setCorePoolSize(6);         // 10 -> 6 하향
        executor.setMaxPoolSize(20);        // 50 -> 20 하향
        executor.setQueueCapacity(500);     // 1000 -> 500 하향
        executor.setThreadNamePrefix("broadcast-");
        executor.initialize();
        return executor;
    }

    /**
     * 예약 주문 체결 전용 비동기 스레드 풀
     * DB 트랜잭션이 포함된 무거운 작업이므로 별도의 풀로 격리합니다.
     */
    @Bean(name = "orderExecutor")
    public Executor orderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);        // 5 -> 3 하향
        executor.setMaxPoolSize(10);        // 20 -> 10 하향
        executor.setQueueCapacity(200);     // 500 -> 200 하향
        executor.setThreadNamePrefix("order-");
        executor.initialize();
        return executor;
    }
}

