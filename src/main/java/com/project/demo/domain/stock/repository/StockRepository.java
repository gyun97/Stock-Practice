package com.project.demo.domain.stock.repository;

import com.project.demo.domain.stock.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface StockRepository extends JpaRepository<Stock, Long> {

    @Query("SELECT s.ticker FROM Stock s")
    Set<String> findAllTickers();

    boolean existsByTicker(String ticker);

    @Query("SELECT s.name FROM Stock s WHERE s.ticker = :ticker")
    String findNameByTicker(@Param("ticker") String ticker);

}
