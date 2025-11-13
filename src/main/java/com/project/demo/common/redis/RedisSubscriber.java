package com.project.demo.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        // 패턴 구독의 경우 message.getChannel()에서 실제 채널 이름을 가져옴
        // ChannelTopic의 경우에도 message.getChannel()이 정확함
        byte[] channelBytes = message.getChannel();
        byte[] patternBytes = pattern;
        
        String patternStr = patternBytes != null ? new String(patternBytes, StandardCharsets.UTF_8) : "null";
        
        if (channelBytes == null) {
            log.warn("Redis 메시지 수신 - channel이 null입니다. pattern: {}", patternStr);
            return;
        }
        
        String channel = new String(channelBytes, StandardCharsets.UTF_8);
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        
        log.info("Redis 메시지 수신 - 채널: {}, pattern: {}, 메시지 길이: {}", channel, patternStr, msg.length());
        
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
        } else if (channel.startsWith("order:notifications:")) {
            // 주문 체결 알림은 사용자별 토픽으로
            String userId = channel.substring("order:notifications:".length());
            String topic = "/topic/order/notifications/" + userId;
            log.info("주문 알림 데이터 WebSocket 발행: {}", topic);
            messagingTemplate.convertAndSend(topic, msg);
        } else {
            log.warn("처리되지 않은 Redis 채널: {}", channel);
        }
    }
}
