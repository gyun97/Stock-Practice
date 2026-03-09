package com.project.demo.domain.order.repository;

import com.project.demo.domain.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o JOIN FETCH o.stock JOIN FETCH o.user WHERE o.isReserved = true AND o.isExecuted = false")
    List<Order> findAllByIsReservedTrueAndIsExecutedFalse();

    @Query("SELECT o FROM Order o JOIN FETCH o.stock JOIN FETCH o.user WHERE o.stock.ticker = :ticker AND o.isReserved = true AND o.isExecuted = false")
    List<Order> findReservedOrdersByTicker(@Param("ticker") String ticker);

    List<Order> findByUserId(Long userId);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.isReserved = false")
    List<Order> findNormalOrdersByUser(@Param("userId") Long userId);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.isReserved = true")
    List<Order> findReservationOrdersByUser(@Param("userId") Long userId);

    /**
     * 이중 체결 방지를 위한 비관적 잠금 조회
     * 체결 처리 시작 전 DB에서 최신 상태를 잠금과 함께 읽어, 중복 체결을 원천 차단합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findWithLockById(@Param("orderId") Long orderId);
}
