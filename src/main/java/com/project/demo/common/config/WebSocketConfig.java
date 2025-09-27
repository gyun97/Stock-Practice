package com.project.demo.common.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/*
 * Spring WebSocket + STOMP를 활성화하고 클라이언트가 연결할 수 있는 엔드포인트를 정의하는 웹소켓 설정 클래스
 * 클라이언트가 특정 채널(/topic/stocks)을 구독하고, 서버가 그 채널로 메시지를 보내면 자동으로 브로드캐스트
 */
@Configuration
@EnableWebSocketMessageBroker // STOMP(메시지 브로커를 사용한 Pub/Sub 방식) 기반의 메시징을 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

//    private final StompHandler stompHandler;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic"); // 클라이언트가 구독할 수 있는 목적지 prefix를 /topic으로 지정
        // 클라이언트가 stompClient.subscribe("/topic/stocks") 하면 이 채널 메시지를 받을 수 있음
        config.setApplicationDestinationPrefixes("/app"); // 클라이언트가 메시지를 보낼 때 사용할 prefix
    }

    // WebSocket 연결 엔드포인트를 정의
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // setAllowedOriginPatterns("*") → CORS 문제를 피하기 위해 모든 origin 허용
        // withSockJS() → 브라우저가 WebSocket을 지원하지 않아도 SockJS를 통해 fallback 가능.
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }

//    @Override
//    public void configureClientInboundChannel(ChannelRegistration registration) {
//        registration.interceptors(stompHandler);
//    }
}