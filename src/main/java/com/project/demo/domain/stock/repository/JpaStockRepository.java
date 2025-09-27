package com.project.demo.domain.stock.repository;

import com.project.demo.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Set;

public interface JpaStockRepository extends JpaRepository<Stock, Long> {

    Set<Integer> findStockSymbol



}
