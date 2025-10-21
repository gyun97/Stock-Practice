package com.project.demo.domain.portfolio.service;

import com.project.demo.domain.portfolio.dto.response.PortfolioResponse;

public interface PortfolioService {

    public PortfolioResponse getMyPortfolio(Long userId);
}
