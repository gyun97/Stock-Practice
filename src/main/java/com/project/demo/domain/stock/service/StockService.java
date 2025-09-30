package com.project.demo.domain.stock.service;

import com.project.demo.domain.stock.dto.response.CandleResponse;
import com.project.demo.domain.stock.dto.response.StockResponse;

import java.util.List;

public interface StockService {

    public List<StockResponse> showAllStock();

    public List<CandleResponse> getMinuteCandles(String ticker, String date, String time);

}
