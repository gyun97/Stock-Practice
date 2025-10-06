package com.project.demo.domain.candle;

import com.project.demo.domain.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class CandleScheduler {

    private final CandleService candleService;
    private final StockRepository stockRepository;

    // 1분마다 실행 (09:00~15:30만)
    @Scheduled(cron = "0 * 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void fetchCandlesEveryMinute() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        if (now.isBefore(LocalTime.of(9, 0)) || now.isAfter(LocalTime.of(15, 20))) {
            return;
        }

        log.info("분봉 데이터 수집 시작: {}", now);

        // 예: 삼성전자(005930), 카카오(035720)
        Set<String> tickers = stockRepository.findAllTickers();

        for (String ticker : tickers) {
            candleService.fetchAndSaveCandles(ticker);
        }
    }
}
