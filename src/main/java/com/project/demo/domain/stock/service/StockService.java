package com.project.demo.domain.stock.service;

import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;

import java.util.List;

public interface StockService {

    public List<StockResponse> showAllStock();

    public List<StockResponse> getMinuteCandles(String ticker, String date, String time);

    public List<CandleResponse> getPeriodStockInfo(String ticker, String period);
    
    public List<CandleResponse> getPeriodStockInfoByRange(String ticker, String period, String startDate, String endDate);
    
    /**
     * 티커로 현재 주가 조회
     */
    int getCurrentPrice(String ticker);

}
