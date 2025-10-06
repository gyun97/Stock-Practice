package com.project.demo.domain.candle;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CandleRepository extends JpaRepository<Candle, Long> {
    boolean existsByTickerAndDateAndTime(String ticker, String date, String time);
}
