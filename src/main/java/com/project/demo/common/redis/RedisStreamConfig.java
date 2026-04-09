package com.project.demo.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;

/**
 * Redis Streams Consumer 설정.
 *
 * <pre>
 * 기능:
 *  1. Consumer 구동을 위한 비동기 스레드 풀(ThreadPoolTaskExecutor) 정의
 *  2. Redis Stream 키 및 Consumer Group 이 존재하지 않으면 초기화 (XGROUP CREATE)
 *  3. StreamMessageListenerContainer에 Consumer 연결
 * </pre>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final RedisStreamConsumer streamConsumer;
    private final StringRedisTemplate redisTemplate;

    private static final String STREAM_KEY = RedisStreamProducer.STREAM_KEY;
    private static final String CONSUMER_GROUP = "stock-group";
    private static final String CONSUMER_NAME = "worker-1";

    /**
     * Consumer 처리를 위한 스레드 풀 정의.
     * Netty 수신 스레드와 병목을 분리하기 위해 별도의 스레드 풀을 사용합니다.
     */
    @Bean
    public ThreadPoolTaskExecutor streamTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("StreamConsumer-");
        executor.initialize();
        return executor;
    }

    /**
     * Producer(XADD) 처리를 위한 전용 스레드 풀.
     * Netty EventLoop를 블로킹하지 않기 위해 별도로 분리합니다.
     */
    @Bean
    public ThreadPoolTaskExecutor producerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8); 
        executor.setQueueCapacity(200); 
        executor.setThreadNamePrefix("StreamProducer-");
        executor.initialize();
        return executor;
    }

    /**
     * StreamMessageListenerContainer 설정 및 리스너 등록
     */
    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ThreadPoolTaskExecutor streamTaskExecutor) {

        // 1. 스트림과 컨슈머 그룹 초기화
        initStreamAndGroup();

        // 2. 컨테이너 옵션 설정 (초기화된 TaskExecutor 주입, Poll 타임아웃 100ms)
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                .builder()
                .pollTimeout(Duration.ofMillis(100))
                .executor(streamTaskExecutor)
                .errorHandler(t -> {
                    log.error("=== REDIS STREAM CONSUMER CRITICAL ERROR ===");
                    log.error("에러 메시지: {}", t.getMessage());
                    log.error("에러 원인 클래스: {}", t.getClass().getName());
                    t.printStackTrace(); // 스택트레이스 강제 출력
                })
                .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container = StreamMessageListenerContainer
                .create(connectionFactory, options);

        // 3. 리스너 등록: stock-group 그룹의 worker-1 이름으로, 소비하지 않은 다음
        // 메시지(ReadOffset.lastConsumed())를 읽음
        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                streamConsumer);

        // 4. 컨테이너 시작
        container.start();
        log.info("Redis Stream Consumer Container 시작 완료");

        return container;
    }

    /**
     * 애플리케이션 기동 시 스트림 및 Consumer Group 초기화
     */
    private void initStreamAndGroup() {
        try {
            boolean hasKey = Boolean.TRUE.equals(redisTemplate.hasKey(STREAM_KEY));
            if (!hasKey) {
                // 스트림 키가 없으면 그룹 생성 전 XADD로 키를 먼저 만들어야 함 (Redis 5+ 기준 XGROUP CREATE MKSTREAM)
                // MKSTREAM 옵션을 지원하기 위해 opsForStream().createGroup 사용
                redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
                log.info("Redis Stream 및 Consumer Group 생성 완료: {}", CONSUMER_GROUP);
            } else {
                // 키는 있는데 그룹이 있는지 확인
                boolean groupExists = false;
                var groups = redisTemplate.opsForStream().groups(STREAM_KEY);
                for (var info : groups) {
                    if (CONSUMER_GROUP.equals(info.groupName())) {
                        groupExists = true;
                        break;
                    }
                }
                if (!groupExists) {
                    redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
                    log.info("기존 Stream에 Consumer Group 생성 완료: {}", CONSUMER_GROUP);
                } else {
                    log.info("Consumer Group 이미 존재함: {}", CONSUMER_GROUP);
                }
            }
        } catch (Exception e) {
            log.warn("Redis Stream/Group 초기화 중 예외 발생 (이미 존재할 가능성 높음): {}", e.getMessage());
        }
    }
}
