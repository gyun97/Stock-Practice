package com.project.demo.common.redis;

import com.project.demo.domain.stock.service.StockMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis Stream의 모니터링 수치를 주기적으로 수집하는 컴포넌트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamMonitor {

    private final StringRedisTemplate redisTemplate;
    private final StockMetrics stockMetrics;

    private static final String STREAM_KEY = RedisStreamProducer.STREAM_KEY;
    private static final String CONSUMER_GROUP = "stock-group";

    /**
     * 1초마다 Redis Stream의 Lag(미처리 메시지 수)을 조회하여 메트릭 업데이트
     */
    @Scheduled(fixedDelay = 1000)
    public void monitorRedisStreamLag() {
        try {
            // 해당 스트림의 컨슈머 그룹 정보 조회
            var groups = redisTemplate.opsForStream().groups(STREAM_KEY);
            
            for (StreamInfo.XInfoGroup group : groups) {
                if (CONSUMER_GROUP.equals(group.groupName())) {
                    long lag = group.pendingCount(); // 미처리(Pending) 메시지 수
                    // 참고: 최신 Redis 버전(7+)에서는 group.lag()를 사용할 수 있으나 
                    // 하위 호환성을 위해 pendingCount를 우선 활용하거나 로직에 따라 조절 가능
                    
                    stockMetrics.updateRedisLag((double) lag);
                    
                    if (lag > 100) {
                        log.warn("Redis Stream Lag 발생 중! 현재 대기량: {}", lag);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            // 스트림이 삭제되었거나 아직 생성되지 않았으면 수치를 0으로 강제 리셋
            stockMetrics.updateRedisLag(0);
        }
    }
}
