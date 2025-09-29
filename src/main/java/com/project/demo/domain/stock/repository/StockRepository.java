package com.project.demo.domain.stock.repository;

import com.project.demo.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Set;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Query("SELECT s.ticker FROM Stock s")
    Set<String> findAllTickers();

    boolean existsByTicker(String ticker);
}
