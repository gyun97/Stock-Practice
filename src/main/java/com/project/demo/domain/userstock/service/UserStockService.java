package com.project.demo.domain.userstock.service;

import com.project.demo.domain.userstock.dto.response.UserStockResponse;

import java.util.List;

public interface UserStockService {
    
    /**
     * 사용자 ID로 보유 주식 목록 조회
     */
    List<UserStockResponse> getUserStocksByUserId(Long userId);
}
