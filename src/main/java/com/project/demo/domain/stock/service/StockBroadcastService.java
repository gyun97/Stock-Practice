package com.project.demo.domain.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/*
서버가 받은 데이터를 프론트엔드(클라이언트)로 실시간 전송
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockBroadcastService {

    /*
    Spring이 제공하는 메시지 전송 도구.
    STOMP 프로토콜 기반 WebSocket 환경에서 클라이언트에게 데이터를 보내기 위해 사용됨.
    “서버에서 클라이언트로 메시지를 밀어 넣는 역할”을 담당.
     */
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(String message) {
        // 메서드가 호출되면 /topic/stocks를 구독하고 있는 모든 클라이언트가 메시지를 받음
        log.info("브로드캐스트 데이터: {}", message);
        messagingTemplate.convertAndSend("/topic/stocks", message); // /topic/stocks → 클라이언트가 구독(subscribe)하는 채널(Endpoint)
    }
}
