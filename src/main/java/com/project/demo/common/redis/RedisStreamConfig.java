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

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final RedisStreamConsumer streamConsumer;
    private final StringRedisTemplate redisTemplate;

    public static final String STREAM_KEY = "stock:stream";
    private static final String CONSUMER_GROUP = "stock-group";
    private static final String CONSUMER_NAME = "worker-1";

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

    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> streamMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ThreadPoolTaskExecutor streamTaskExecutor) {

        initStreamAndGroup();

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions
                        .<String, MapRecord<String, String, String>>builder()
                        .pollTimeout(Duration.ofMillis(100))
                        .executor(streamTaskExecutor)
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(connectionFactory, options);

        container.receive(
                Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()),
                streamConsumer);

        container.start();
        return container;
    }

    private void initStreamAndGroup() {
        try {
            if (Boolean.FALSE.equals(redisTemplate.hasKey(STREAM_KEY))) {
                redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
            } else {
                redisTemplate.opsForStream().groups(STREAM_KEY).stream()
                        .filter(g -> g.groupName().equals(CONSUMER_GROUP))
                        .findFirst()
                        .orElseGet(() -> {
                            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.from("0-0"), CONSUMER_GROUP);
                            return null;
                        });
            }
        } catch (Exception e) {
            log.warn("Stream Group already exists or error: {}", e.getMessage());
        }
    }
}
