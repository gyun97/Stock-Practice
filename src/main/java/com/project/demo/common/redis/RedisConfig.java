//package com.project.demo.common.redis;
//
//
//import com.project.demo.common.tick.TickSubscriber;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.listener.ChannelTopic;
//import org.springframework.data.redis.listener.RedisMessageListenerContainer;
//import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
//import org.springframework.scheduling.annotation.EnableScheduling;
//
//@Configuration
//@EnableScheduling
//public class RedisConfig {
//
//    @Bean
//    public RedisConnectionFactory redisConnectionFactory() {
//        return new LettuceConnectionFactory("redis_master", 6379);
//    }
//
//    @Bean
//    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory cf) {
//        return new StringRedisTemplate(cf);
//    }
//
//    @Bean
//    public ChannelTopic tickTopic() {
//        return new ChannelTopic("ticks"); // 모든 종목 틱이 여기로 publish됨(심플 버전)
//    }
//
//    @Bean
//    public RedisMessageListenerContainer container(
//            RedisConnectionFactory cf, MessageListenerAdapter adapter, ChannelTopic tickTopic) {
//        RedisMessageListenerContainer c = new RedisMessageListenerContainer();
//        c.setConnectionFactory(cf);
//        c.addMessageListener(adapter, tickTopic);
//        return c;
//    }
//
//    @Bean
//    public MessageListenerAdapter listenerAdapter(TickSubscriber subscriber) {
//        return new MessageListenerAdapter(subscriber, "onMessage"); // 메서드 바인딩
//    }
//}
