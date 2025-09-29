package com.project.demo.domain.stock.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Primary
public class StockServiceImpl implements StockService {

//    private final ConnectWebSocketClient client;
//    private final ObjectMapper mapper;
//    private final ApprovalKeyService approvalKeyService;
//    private final KisApiAccessTokenService kisApiAccessTokenService;
//
//    private String approvalKey;
//
//    @Value("${KIS_APP_KEY}")
//    private String appKey;
//
//    @Value("${KIS_APP_SECRET}")
//    private String appSecret;
//
//    @Value("${BASE_URL}")
//    private String baseUrl;
//
//    private String iv;
//    private String key;



}
