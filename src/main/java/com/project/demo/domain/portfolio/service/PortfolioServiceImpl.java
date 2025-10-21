package com.project.demo.domain.portfolio.service;

import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.domain.portfolio.dto.response.PortfolioResponse;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService{

    private final PortfolioRepository portfolioRepository;

    /*
    내 포토폴리오 조회
     */
    public PortfolioResponse getMyPortfolio(Long userId) {
        Portfolio myPortfolio = portfolioRepository.findByUserId(userId)
                .orElseThrow(NotFoundPortfolioException::new);

        return PortfolioResponse.of(myPortfolio);
    }
}
