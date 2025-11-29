package com.project.demo.common.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionManager {

    private final StringRedisTemplate redisTemplate;
    private static final String USER_SESSION_KEY_PREFIX = "websocket:user:session:";
    private static final String SESSION_USER_KEY_PREFIX = "websocket:session:user:";
    private static final long SESSION_TTL_HOURS = 1; // 세션 TTL: 1시간
    
    private SimpMessagingTemplate messagingTemplate;

    /**
     * SimpMessagingTemplate 설정 (순환 의존성 해결을 위해)
     */
    public void setMessagingTemplate(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 사용자 세션 등록 (Redis에 저장)
     */
    public void addUserSession(Long userId, String sessionId) {
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
        String sessionUserKey = SESSION_USER_KEY_PREFIX + sessionId;
        
        // 양방향 매핑 저장 (userId -> sessionId, sessionId -> userId)
        redisTemplate.opsForValue().set(userSessionKey, sessionId, SESSION_TTL_HOURS, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(sessionUserKey, userId.toString(), SESSION_TTL_HOURS, TimeUnit.HOURS);
        
        log.info("사용자 세션 등록 (Redis) - 사용자 ID: {}, 세션 ID: {}", userId, sessionId);
    }

    /**
     * 사용자 세션 제거 (Redis에서 삭제)
     */
    public void removeUserSession(Long userId) {
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
        String sessionId = redisTemplate.opsForValue().get(userSessionKey);
        
        if (sessionId != null) {
            String sessionUserKey = SESSION_USER_KEY_PREFIX + sessionId;
            redisTemplate.delete(userSessionKey);
            redisTemplate.delete(sessionUserKey);
            log.info("사용자 세션 제거 (Redis) - 사용자 ID: {}, 세션 ID: {}", userId, sessionId);
        } else {
            log.warn("제거할 세션을 찾을 수 없음 - 사용자 ID: {}", userId);
        }
    }

    /**
     * 세션 ID로 사용자 ID 조회 (DISCONNECT 시 사용)
     */
    public Long getUserIdBySessionId(String sessionId) {
        String sessionUserKey = SESSION_USER_KEY_PREFIX + sessionId;
        String userIdStr = redisTemplate.opsForValue().get(sessionUserKey);
        if (userIdStr != null) {
            try {
                return Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                log.error("사용자 ID 파싱 실패 - sessionId: {}, userIdStr: {}", sessionId, userIdStr);
                return null;
            }
        }
        return null;
    }

    /**
     * 사용자 ID로 세션 ID 조회
     */
    public String getSessionIdByUserId(Long userId) {
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
        return redisTemplate.opsForValue().get(userSessionKey);
    }

    /**
     * 사용자가 연결되어 있는지 확인
     */
    public boolean isUserConnected(Long userId) {
        String userSessionKey = USER_SESSION_KEY_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(userSessionKey));
    }

    /**
     * 특정 사용자에게 포트폴리오 데이터 전송
     */
    public void sendPortfolioUpdate(Long userId, Object portfolioData) {
        // Redis에서 세션 확인 (사용자가 연결되어 있는지)
        if (isUserConnected(userId) && messagingTemplate != null) {
            String destination = "/topic/portfolio/updates/" + userId;
            messagingTemplate.convertAndSend(destination, portfolioData);
            log.info("포트폴리오 데이터 전송 - 사용자 ID: {}, 목적지: {}", userId, destination);
        } else {
            log.debug("사용자 세션이 없거나 MessagingTemplate이 설정되지 않음 - 사용자 ID: {}", userId);
        }
    }

    /**
     * 특정 사용자에게 보유 주식 데이터 전송
     */
    public void sendUserStockUpdate(Long userId, Object userStockData) {
        // Redis에서 세션 확인 (사용자가 연결되어 있는지)
        if (isUserConnected(userId) && messagingTemplate != null) {
            String destination = "/topic/userstock/updates/" + userId;
            messagingTemplate.convertAndSend(destination, userStockData);
            log.info("보유 주식 데이터 전송 - 사용자 ID: {}, 목적지: {}", userId, destination);
        } else {
            log.debug("사용자 세션이 없거나 MessagingTemplate이 설정되지 않음 - 사용자 ID: {}", userId);
        }
    }

    /**
     * 특정 사용자에게 주문 체결 알림 전송 (직접 STOMP 전송)
     */
    public void sendOrderNotification(Long userId, Object notification) {
        // MessagingTemplate이 없으면 전송 불가
        if (messagingTemplate == null) {
            log.warn("MessagingTemplate이 설정되지 않음 - 사용자 ID: {}", userId);
            return;
        }
        
        String destination = "/topic/order/notifications/" + userId;
        
        // 사용자가 연결되어 있는지 확인
        boolean connected = isUserConnected(userId);
        if (connected) {
            log.info("주문 알림 전송 - 사용자 ID: {}, 목적지: {}, 연결 상태: 연결됨", userId, destination);
        } else {
            log.warn("주문 알림 전송 시도 - 사용자 ID: {}, 목적지: {}, 연결 상태: 연결 안됨 (알림은 전송되지만 사용자가 받지 못할 수 있음)", userId, destination);
        }
        
        // 연결 여부와 관계없이 알림 전송 (사용자가 나중에 접속하면 받을 수 있도록)
        try {
            messagingTemplate.convertAndSend(destination, notification);
            log.info("주문 알림 전송 완료 - 사용자 ID: {}, 목적지: {}", userId, destination);
        } catch (Exception e) {
            log.error("주문 알림 전송 실패 - 사용자 ID: {}, 목적지: {}, 오류: {}", userId, destination, e.getMessage(), e);
        }
    }

    /**
     * 모든 연결된 사용자에게 데이터 전송
     */
    public void broadcastToAllUsers(Object data) {
        // Redis에서 모든 연결된 사용자 조회
        Set<String> keys = redisTemplate.keys(USER_SESSION_KEY_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                String userIdStr = key.substring(USER_SESSION_KEY_PREFIX.length());
                try {
                    Long userId = Long.parseLong(userIdStr);
                    sendPortfolioUpdate(userId, data);
                } catch (NumberFormatException e) {
                    log.warn("유효하지 않은 사용자 ID 키: {}", key);
                }
            }
        }
    }

    /**
     * 현재 연결된 사용자 수 반환 (Redis에서 조회)
     */
    public int getConnectedUserCount() {
        Set<String> keys = redisTemplate.keys(USER_SESSION_KEY_PREFIX + "*");
        return keys != null ? keys.size() : 0;
    }

    /**
     * 연결된 사용자 목록 반환 (Redis에서 조회)
     */
    public Map<Long, String> getConnectedUsers() {
        Map<Long, String> users = new HashMap<>();
        Set<String> keys = redisTemplate.keys(USER_SESSION_KEY_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                String userIdStr = key.substring(USER_SESSION_KEY_PREFIX.length());
                try {
                    Long userId = Long.parseLong(userIdStr);
                    String sessionId = redisTemplate.opsForValue().get(key);
                    if (sessionId != null) {
                        users.put(userId, sessionId);
                    }
                } catch (NumberFormatException e) {
                    log.warn("유효하지 않은 사용자 ID 키: {}", key);
                }
            }
        }
        return users;
    }
}


