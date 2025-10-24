package com.project.demo.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(pattern, StandardCharsets.UTF_8);
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        
        log.info("Redis 메시지 수신 - 채널: {}, 메시지: {}", channel, msg);
        
        // 채널에 따라 다른 토픽으로 발행
        if ("stock:updates".equals(channel)) {
            // 주식 데이터는 stock 토픽으로
            log.info("주식 데이터 WebSocket 발행: /topic/stocks");
            messagingTemplate.convertAndSend("/topic/stocks", msg);
        } else if (channel.startsWith("portfolio:updates:")) {
            // 사용자별 포트폴리오 수익률은 사용자별 토픽으로
            String userId = channel.substring("portfolio:updates:".length());
            String topic = "/topic/portfolio/updates/" + userId;
            log.info("포트폴리오 데이터 WebSocket 발행: {}", topic);
            messagingTemplate.convertAndSend(topic, msg);
        } else if (channel.startsWith("userstock:updates:")) {
            // 보유 주식 정보는 사용자별 토픽으로
            String userId = channel.substring("userstock:updates:".length());
            String topic = "/topic/userstock/updates/" + userId;
            log.info("보유 주식 데이터 WebSocket 발행: {}", topic);
            messagingTemplate.convertAndSend(topic, msg);
        } else {
            log.warn("처리되지 않은 Redis 채널: {}", channel);
        }
    }
}
