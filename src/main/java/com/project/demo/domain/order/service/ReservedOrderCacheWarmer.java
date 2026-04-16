package com.project.demo.domain.order.service;

import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 서버 시작 시 DB에 저장된 미체결 예약 주문들을 Redis ZSet으로 로드합니다.
 * 이를 통해 DB와 Redis 간의 데이터 일관성을 확보하고 실시간 체결 엔진이 Redis를 즉시 사용할 수 있게 합니다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReservedOrderCacheWarmer implements CommandLineRunner {

    private final OrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void run(String... args) {
        log.info("[CACHE-WARMER] 미체결 예약 주문 Redis 로드 시작...");
        
        // 미체결 예약 주문 전체 조회
        List<Order> unexecutedReservedOrders = orderRepository.findAllByIsReservedTrueAndIsExecutedFalse();
        
        if (unexecutedReservedOrders.isEmpty()) {
            log.info("[CACHE-WARMER] 로드할 미체결 예약 주문이 없습니다.");
            return;
        }

        long count = 0;
        for (Order order : unexecutedReservedOrders) {
            String ticker = order.getStock().getTicker();
            String type = order.getType().name(); // BUY or SELL
            String redisKey = "order:reserved:" + type.toLowerCase() + ":" + ticker;
            
            redisTemplate.opsForZSet().add(redisKey, order.getId().toString(), order.getPrice());
            count++;
        }

        log.info("[CACHE-WARMER] 총 {}건의 예약 주문을 Redis ZSet으로 로드 완료.", count);
    }
}
