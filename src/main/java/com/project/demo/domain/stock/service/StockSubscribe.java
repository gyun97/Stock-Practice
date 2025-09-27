//package com.project.demo.domain.stock.service;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.databind.node.ObjectNode;
//import com.project.demo.common.oauth.service.ApprovalKeyService;
//import com.project.demo.domain.stock.repository.JpaStockRepository;
//import com.project.demo.domain.stock.repository.StockRepository;
//import jakarta.annotation.PostConstruct;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//@Transactional(readOnly = true)
//public class StockSubscribe {
//
//    private final ObjectMapper mapper;
//    private final ApprovalKeyService approvalKeyService;
//    private final JpaStockRepository stockRepository;
//
//    private String approvalKey;
//
//    @Value("${KIS_APP_KEY}")
//    private String appKey;
//
//    @Value("${KIS_APP_SECRET}")
//    private String appSecret;
//
//    @Value("${REAL_BASE_URL}")
//    private String baseUrl;
//
//    private String iv;
//    private String key;
//
//    @PostConstruct
//    public void subscribeInitStock() {
//
//    }
//
//
//    public void subscribe(String trId, String trKey) throws JsonProcessingException {
//        approvalKey = approvalKeyService.getApprovalKey(); // 웹소켓 인증 키 발급
//        ObjectNode header = mapper.createObjectNode();
//
//
//
//
//
//
//
//        header.put("approval_key", approvalKey);
//        header.put("custtype", "P");
//        header.put("tr_type", "1");
//        header.put("content-type", "utf-8");
//
//        ObjectNode input = mapper.createObjectNode();
//        input.put("tr_id", trId);
//        input.put("tr_key", trKey);
//
//        ObjectNode body = mapper.createObjectNode();
//        body.set("input", input);
//
//        ObjectNode request = mapper.createObjectNode();
//        request.set("header", header);
//        request.set("body", body);
//
//        String json = mapper.writeValueAsString(request);
////        client.send(json);
////        unSubscribe(approvalKey, trId, trKey);
//    }
//}
