package com.project.demo.common.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        RedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Redis Pub/Sub 제거 - 단일 서버 환경에서 직접 STOMP로 전송
        // container.addMessageListener(subscriber, new ChannelTopic("stock:updates")); // 제거됨
        // container.addMessageListener(subscriber, new PatternTopic("order:notifications:*")); // 제거됨
        return container;
    }
}
