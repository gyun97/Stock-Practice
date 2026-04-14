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
        executor.setCorePoolSize(10);       
        executor.setMaxPoolSize(50);        
        executor.setQueueCapacity(2000);    
        executor.setThreadNamePrefix("order-");
        
        // Micrometer Tracing (Tempo) Context 전달을 위한 TaskDecorator 설정
        // 이 설정을 통해 비동기 쓰레드로 넘어갈 때 부모 Trace ID가 끊기지 않고 유지됩니다.
        executor.setTaskDecorator(new org.springframework.core.task.support.ContextPropagatingTaskDecorator());
        
        executor.initialize();
        return executor;
    }
}

