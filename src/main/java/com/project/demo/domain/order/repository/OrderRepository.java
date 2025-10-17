package com.project.demo.domain.order.repository;

import com.project.demo.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByIsReservedTrueAndIsExecutedFalse();
}
