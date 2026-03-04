package com.project.demo.common.websocket;

import com.project.demo.common.jwt.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;

@Slf4j
@Component
public class WebSocketConnectionInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final WebSocketSessionManager sessionManager;
    @Lazy
    private final SimpMessagingTemplate messagingTemplate;

    // 생성자에서 MessagingTemplate 설정
    public WebSocketConnectionInterceptor(JwtUtil jwtUtil, WebSocketSessionManager sessionManager,
            @Lazy SimpMessagingTemplate messagingTemplate) {
        this.jwtUtil = jwtUtil;
        this.sessionManager = sessionManager;
        this.messagingTemplate = messagingTemplate;
        // 순환 의존성 해결을 위해 MessagingTemplate 설정
        this.sessionManager.setMessagingTemplate(messagingTemplate);
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            // WebSocket 연결 시 JWT 토큰 검증 및 사용자 ID 추출
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                // "null" 또는 "undefined" 문자열이 들어오는 경우 예외 처리
                if ("null".equals(token) || "undefined".equals(token)) {
                    log.debug("비로그인 상태의 WebSocket 연결 시도 (토큰 없음)");
                    return message;
                }

                try {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String sessionId = accessor.getSessionId();

                    // 사용자 세션 등록
                    sessionManager.addUserSession(userId, sessionId);

                    log.info("WebSocket 연결 성공 - 사용자 ID: {}, 세션 ID: {}", userId, sessionId);
                } catch (Exception e) {
                    log.error("WebSocket 연결 실패 - JWT 토큰 검증 오류", e);
                }
            }
        } else if (StompCommand.DISCONNECT.equals(command)) {
            // WebSocket 연결 해제 시 세션 제거 (Redis에서 조회)
            String sessionId = accessor.getSessionId();
            Long userId = sessionManager.getUserIdBySessionId(sessionId);
            if (userId != null) {
                // 특정 세션 아이디를 명시하여 대표 세션 오삭제 방지
                sessionManager.removeUserSession(userId, sessionId);
                log.info("WebSocket 연결 해제 완료 - 사용자 ID: {}, 세션 ID: {}", userId, sessionId);
            } else {
                log.warn("연결 해제할 사용자를 찾을 수 없음 - 세션 ID: {}", sessionId);
            }
        }

        return message;
    }
}
