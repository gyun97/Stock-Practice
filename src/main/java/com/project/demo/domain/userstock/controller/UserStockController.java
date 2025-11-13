package com.project.demo.domain.userstock.controller;

import com.project.demo.common.response.ApiResponse;
import com.project.demo.domain.user.entity.AuthUser;

import com.project.demo.domain.userstock.dto.response.UserStockResponse;
import com.project.demo.domain.userstock.service.UserStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/userstocks")
@RequiredArgsConstructor
@Slf4j
public class UserStockController {

    private final UserStockService userStockService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserStockResponse>>> getMyUserStocks(
            @AuthenticationPrincipal AuthUser authUser) {
        Long userId = authUser.getUserId();
        List<UserStockResponse> userStocks = userStockService.getUserStocksByUserId(userId);
        return ResponseEntity.ok(ApiResponse.requestSuccess(userStocks));
    }
}
