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
        String msg = new String(message.getBody(), StandardCharsets.UTF_8);
        // 그대로 프론트에 전송
        messagingTemplate.convertAndSend("/topic/stocks", msg);
    }
}
