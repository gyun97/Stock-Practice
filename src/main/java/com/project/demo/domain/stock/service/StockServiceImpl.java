package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.demo.common.oauth.service.KisApprovalKeyService;
import com.project.demo.common.oauth.service.KisApiAccessTokenService;

import com.project.demo.common.websocket.ConnectWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Primary
public class StockServiceImpl implements StockService {

    private final ConnectWebSocketClient client;
    private final ObjectMapper mapper;
    private final KisApprovalKeyService approvalKeyService;
    private final StockBroadcastService stockBroadcastService;
    private final KisApiAccessTokenService kisApiAccessTokenService;
    private final WebClient webClient;

    private String approvalKey;

    @Value("${KIS_APP_KEY}")
    private String appKey;

    @Value("${KIS_APP_SECRET}")
    private String appSecret;

    @Value("${REAL_BASE_URL}")
    private String baseUrl;

    private String iv;
    private String key;


}
