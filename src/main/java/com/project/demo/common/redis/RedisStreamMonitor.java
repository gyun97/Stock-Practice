package com.project.demo.common.redis;

import com.project.demo.domain.stock.service.StockMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            // Spring Data Redis의 StreamInfo.XInfoGroup은 lag 필드를 지원하지 않는 경우가 많으므로
            // 직접 RAW 명령어를 실행하여 대기열(lag) 정보를 가져옵니다.
            Long lagValue = redisTemplate
                    .execute((org.springframework.data.redis.connection.RedisConnection connection) -> {
                        // [XINFO, GROUPS, STREAM_KEY] 실행
                        byte[] streamKeyBytes = STREAM_KEY.getBytes();
                        Object result = connection.execute("XINFO", "GROUPS".getBytes(), streamKeyBytes);

                        if (result instanceof java.util.List<?> groups) {
                            for (Object groupObj : groups) {
                                if (groupObj instanceof java.util.List<?> groupInfo) {
                                    // 리스트 형태의 응답에서 name과 lag를 찾습니다.
                                    String name = null;
                                    Long lag = null;
                                    for (int i = 0; i < groupInfo.size(); i += 2) {
                                        String key = new String((byte[]) groupInfo.get(i));
                                        Object val = groupInfo.get(i + 1);
                                        if ("name".equals(key)) {
                                            name = new String((byte[]) val);
                                        } else if ("lag".equals(key)) {
                                            lag = (Long) val;
                                        }
                                    }
                                    if (CONSUMER_GROUP.equals(name)) {
                                        return lag;
                                    }
                                }
                            }
                        }
                        return 0L;
                    });

            long finalLag = (lagValue != null) ? lagValue : 0L;
            stockMetrics.updateRedisLag((double) finalLag);

            if (finalLag > 100) {
                log.warn("Redis Stream Lag 발생 중! 현재 대기열(True Lag): {}", finalLag);
            }

        } catch (Exception e) {
            // log.error("Redis Stream Lag 모니터링 실패: {}", e.getMessage());
            stockMetrics.updateRedisLag(0);
        }
    }
}
