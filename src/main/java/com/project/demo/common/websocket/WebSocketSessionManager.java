package com.project.demo.common.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketSessionManager {

    // 사용자 ID별로 세션을 관리하는 Map
    private final Map<Long, String> userSessions = new ConcurrentHashMap<>();
    
    private SimpMessagingTemplate messagingTemplate;

    /**
     * SimpMessagingTemplate 설정 (순환 의존성 해결을 위해)
     */
    public void setMessagingTemplate(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 사용자 세션 등록
     */
    public void addUserSession(Long userId, String sessionId) {
        userSessions.put(userId, sessionId);
        log.info("사용자 세션 등록 - 사용자 ID: {}, 세션 ID: {}", userId, sessionId);
    }

    /**
     * 사용자 세션 제거
     */
    public void removeUserSession(Long userId) {
        userSessions.remove(userId);
        log.info("사용자 세션 제거 - 사용자 ID: {}", userId);
    }

    /**
     * 특정 사용자에게 포트폴리오 데이터 전송
     */
    public void sendPortfolioUpdate(Long userId, Object portfolioData) {
        String sessionId = userSessions.get(userId);
        if (sessionId != null && messagingTemplate != null) {
            String destination = "/topic/portfolio/updates/" + userId;
            messagingTemplate.convertAndSend(destination, portfolioData);
            log.info("포트폴리오 데이터 전송 - 사용자 ID: {}, 목적지: {}", userId, destination);
        } else {
            log.warn("사용자 세션이 없거나 MessagingTemplate이 설정되지 않음 - 사용자 ID: {}", userId);
        }
    }

    /**
     * 특정 사용자에게 보유 주식 데이터 전송
     */
    public void sendUserStockUpdate(Long userId, Object userStockData) {
        String sessionId = userSessions.get(userId);
        if (sessionId != null && messagingTemplate != null) {
            String destination = "/topic/userstock/updates/" + userId;
            messagingTemplate.convertAndSend(destination, userStockData);
            log.info("보유 주식 데이터 전송 - 사용자 ID: {}, 목적지: {}", userId, destination);
        } else {
            log.warn("사용자 세션이 없거나 MessagingTemplate이 설정되지 않음 - 사용자 ID: {}", userId);
        }
    }

    /**
     * 특정 사용자에게 주문 체결 알림 전송
     */
    public void sendOrderNotification(Long userId, Object notification) {
        if (messagingTemplate != null) {
            String destination = "/topic/order/notifications/" + userId;
            log.info("주문 알림 전송 시도 - 사용자 ID: {}, 목적지: {}, 알림 내용: {}", userId, destination, notification);
            messagingTemplate.convertAndSend(destination, notification);
            log.info("주문 알림 전송 완료 - 사용자 ID: {}, 목적지: {}", userId, destination);
        } else {
            log.warn("MessagingTemplate이 설정되지 않음 - 주문 알림 전송 실패 - 사용자 ID: {}", userId);
        }
    }

    /**
     * 모든 연결된 사용자에게 데이터 전송
     */
    public void broadcastToAllUsers(Object data) {
        userSessions.keySet().forEach(userId -> {
            sendPortfolioUpdate(userId, data);
        });
    }

    /**
     * 현재 연결된 사용자 수 반환
     */
    public int getConnectedUserCount() {
        return userSessions.size();
    }

    /**
     * 연결된 사용자 목록 반환
     */
    public Map<Long, String> getConnectedUsers() {
        return new ConcurrentHashMap<>(userSessions);
    }
}


