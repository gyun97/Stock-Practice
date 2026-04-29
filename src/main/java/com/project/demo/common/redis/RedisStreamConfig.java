package com.project.demo.common.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Streams Consumer 설정.
 */
@Slf4j
@Configuration
public class RedisStreamConfig {

    private final RedisStreamConsumer streamConsumer;
    private final StringRedisTemplate redisTemplate;

    public RedisStreamConfig(@Lazy RedisStreamConsumer streamConsumer, StringRedisTemplate redisTemplate) {
        this.streamConsumer = streamConsumer;
        this.redisTemplate = redisTemplate;
    }

    public static final String STREAM_KEY = RedisStreamProducer.STREAM_KEY;
    private static final String CONSUMER_GROUP = "stock-group";
    private static final String CONSUMER_NAME = "worker-1";

    /** 컨테이너 재시작 중복 방지 플래그 */
    private final AtomicBoolean restarting = new AtomicBoolean(false);

    /**
     * Consumer 처리를 위한 스레드 풀
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
     * Producer(XADD) 처리를 위한 전용 스레드 풀 (중요: RedisStreamProducer에서 사용함)
     */
    @Bean
    public ThreadPoolTaskExecutor producerTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("StreamProducer-");
        executor.initialize();
        return executor;
    }

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ThreadPoolTaskExecutor streamTaskExecutor) {

        initStreamAndGroup();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container = buildContainer(
                connectionFactory, streamTaskExecutor);

        log.info("Redis Stream Consumer Container 설정 완료");
        return container;
    }

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> buildContainer(
            RedisConnectionFactory connectionFactory,
            ThreadPoolTaskExecutor streamTaskExecutor) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        .pollTimeout(Duration.ofMillis(200))
                        .executor(streamTaskExecutor)
                        .errorHandler(t -> {
                            log.error("=== REDIS STREAM CONSUMER CRITICAL ERROR ===");
                            log.error("에러 메시지: {}", t.getMessage());
                            if (t instanceof NullPointerException) {
                                log.warn("StreamPollTask NPE 감지 — 컨테이너 재시작 시도");
                                restartContainer(connectionFactory, streamTaskExecutor);
                            }
                        })
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                streamConsumer);

        return container;
    }

    private void restartContainer(RedisConnectionFactory connectionFactory, ThreadPoolTaskExecutor streamTaskExecutor) {
        if (!restarting.compareAndSet(false, true)) return;

        new Thread(() -> {
            try {
                Thread.sleep(2000);
                log.info("Redis Stream Consumer Container 재시작 중...");
                initStreamAndGroup();
                StreamMessageListenerContainer<String, MapRecord<String, String, String>> newContainer =
                        buildContainer(connectionFactory, streamTaskExecutor);
                newContainer.start();
                log.info("Redis Stream Consumer Container 재시작 완료");
            } catch (Exception e) {
                log.error("컨테이너 재시작 실패", e);
            } finally {
                restarting.set(false);
            }
        }, "StreamContainer-Restart").start();
    }

    private void initStreamAndGroup() {
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(STREAM_KEY))) {
                redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
            } else {
                boolean groupExists = redisTemplate.opsForStream().groups(STREAM_KEY).stream()
                        .anyMatch(g -> g.groupName().equals(CONSUMER_GROUP));
                if (!groupExists) {
                    redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
                }
            }
        } catch (Exception e) {
            log.warn("Redis Stream/Group 초기화 중 예외 발생: {}", e.getMessage());
        }
    }
}
