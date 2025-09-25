package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.oauth.service.ApprovalKeyService;
import com.project.demo.common.websocket.ConnectWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Primary
public class StockServiceImpl implements StockService {

    private final ConnectWebSocketClient client;
    private final ObjectMapper mapper;
    private final ApprovalKeyService approvalKeyService;

    @Value("approval_key")
    private String approvalKey;

    private String iv;
    private String key;

    @Override
    public void getTradedPrice(String trId, String trKey) throws JsonProcessingException {
        subscribe(trId, trKey); // 웹소켓을 통한 실시간 체결가
        // 만약 장이 열린 상태라면
//        if (isMarketOpen()) {
////            subscribe(trId, trKey); // 웹소켓을 통한 실시간 체결가
//        } else {
//
//        }
    }

    private void subscribe(String trId, String trKey) throws JsonProcessingException {
        approvalKey = approvalKeyService.getApprovalKey(); // 웹소켓 인증 키 발급
        ObjectNode header = mapper.createObjectNode();
        header.put("approval_key", approvalKey);
        header.put("custtype", "P");
        header.put("tr_type", "1");
        header.put("content-type", "utf-8");

        ObjectNode input = mapper.createObjectNode();
        input.put("tr_id", trId);
        input.put("tr_key", trKey);

        ObjectNode body = mapper.createObjectNode();
        body.set("input", input);

        ObjectNode request = mapper.createObjectNode();
        request.set("header", header);
        request.set("body", body);

        String json = mapper.writeValueAsString(request);
        client.send(json);
//        unSubscribe(approvalKey, trId, trKey);
    }


    private void unSubscribe(String trId, String trKey) throws JsonProcessingException {
        ObjectNode header = mapper.createObjectNode();
        header.put("approval_key", approvalKey);
        header.put("custtype", "P");
        header.put("tr_type", "2");
        header.put("content-type", "utf-8");

        ObjectNode input = mapper.createObjectNode();
        input.put("tr_id", trId);
        input.put("tr_key", trKey);

        ObjectNode body = mapper.createObjectNode();
        body.set("input", input);

        ObjectNode request = mapper.createObjectNode();
        request.set("header", header);
        request.set("body", body);

        String json = mapper.writeValueAsString(request);
        client.send(json);
    }

    // 장 시간인지 확인
    private boolean isMarketOpen() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        return !now.isBefore(LocalTime.of(9, 0)) && !now.isAfter(LocalTime.of(15, 30));
    }

}
