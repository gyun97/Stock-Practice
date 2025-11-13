package com.project.demo.domain.portfolio.service;

import com.project.demo.domain.portfolio.dto.response.PortfolioResponse;
import com.project.demo.domain.portfolio.dto.response.RankingResponse;

import java.util.List;

public interface PortfolioService {

    public PortfolioResponse getMyPortfolio(Long userId);
    
    public List<RankingResponse> getRanking(int limit);
}
