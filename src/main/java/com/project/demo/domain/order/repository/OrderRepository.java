package com.project.demo.domain.order.repository;

import com.project.demo.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByIsReservedTrueAndIsExecutedFalse();

    List<Order> findByUserId(Long userId);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.isReserved = false")
    List<Order> findNormalOrdersByUser(@Param("userId") Long userId);

    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.isReserved = true")
    List<Order> findReservationOrdersByUser(@Param("userId") Long userId);

//     특정 종목의 나의 일반 주문 내역 조회
//    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.stock.ticker = :ticker AND o.isReserved = false")
//    List<Order> findNormalOrdersByUserAndStock(@Param("userId") Long userId, @Param("ticker") String ticker);

    // 특정 종목의 나의 예약 주문 내역 조회
//    @Query("SELECT o FROM Order o WHERE o.user.id = :userId AND o.stock.ticker = :ticker AND o.isReserved = true")
//    List<Order> findReservationOrdersByUserAndStock(@Param("userId") Long userId, @Param("ticker") String ticker);
}
