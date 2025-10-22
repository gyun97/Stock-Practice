package com.project.demo.common.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.connection.Message;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    // 서버에서 받은 메시지를 수신
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(pattern, StandardCharsets.UTF_8);
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        
        // 채널에 따라 다른 토픽으로 발행
        if ("stock:updates".equals(channel)) {
            // 주식 데이터는 기존 토픽으로
            messagingTemplate.convertAndSend("/topic/stocks", msg);
        } else if ("portfolio:updates".equals(channel)) {
            // 포트폴리오 수익률은 별도 토픽으로
            messagingTemplate.convertAndSend("/topic/portfolio/updates", msg);
        }
    }
}
