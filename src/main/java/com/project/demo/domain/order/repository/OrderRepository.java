package com.project.demo.domain.order.repository;

import com.project.demo.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

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
}
