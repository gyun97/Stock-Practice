package com.project.demo.domain.stock.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.demo.common.oauth.service.ApprovalKeyService;
import com.project.demo.common.oauth.service.KisApiAccessTokenService;
import com.project.demo.common.websocket.ConnectWebSocketClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Primary
public class StockServiceImpl implements StockService {

    private final ConnectWebSocketClient client;
    private final ObjectMapper mapper;
    private final ApprovalKeyService approvalKeyService;
    private final StockBroadcastService stockBroadcastService;
    private final KisApiAccessTokenService kisApiAccessTokenService;

    private String approvalKey;

    @Value("${KIS_APP_KEY}")
    private String appKey;

    @Value("${KIS_APP_SECRET}")
    private String appSecret;

    @Value("${BASE_URL}")
    private String baseUrl;


    private String iv;
    private String key;

    @Override
    public void getStockInfo(String trId, String trKey) throws JsonProcessingException {
//        subscribe(trId, trKey); // 웹소켓을 통한 실시간 체결가
        // 만약 장이 열린 상태라면
        if (isMarketOpen()) {
            subscribe(trId, trKey); // 웹소켓을 통한 실시간 체결가
        } else { // 장이 열리지 않았다면
            // KIS의 주식 현재가 시세 REST API 호출해서 주식 정보
            getStockPriceRest(trKey);
        }
    }

    public void subscribe(String trId, String trKey) throws JsonProcessingException {
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


    public void unSubscribe(String trId, String trKey) throws JsonProcessingException {
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
    public boolean isMarketOpen() {
        LocalTime now = LocalTime.now(ZoneId.of("Asia/Seoul"));
        return !now.isBefore(LocalTime.of(9, 0)) && !now.isAfter(LocalTime.of(15, 30));
    }

    /**
     * 장 마감 이후 REST API로 현재가 조회
     */
    public void getStockPriceRest(String trKey) {
        String url = baseUrl + "/uapi/domestic-stock/v1/quotations/inquire-price";

        log.info("url: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(kisApiAccessTokenService.getAccessToken()); // Access Token 필요
        headers.set("appkey", appKey);
        headers.set("appsecret", appSecret);
        headers.set("tr_id", "FHKST01010100"); // 국내 주식 현재가 조회 TR_ID

        // 쿼리 파라미터
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("fid_cond_mrkt_div_code", "J") // J: 주식
                .queryParam("fid_input_iscd", trKey);      // 종목 코드

        HttpEntity<String> entity = new HttpEntity<>(headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<Map> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                entity,
                Map.class
        );

        log.info("주식 현재가 조회 응답: {}", response.getBody());

        try {
            Double price = null;
            Map body = response.getBody();
            if (body != null) {
                Object output = body.get("output");
                if (output instanceof Map<?,?> om) {
                    Object p = om.get("stck_prpr"); // KIS 현재가 필드 추정
                    if (p != null) {
                        try { price = Double.valueOf(p.toString()); } catch (Exception ignore) {}
                    }
                }
            }

            String tradeTime = LocalTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("HHmmss"));
            ObjectNode out = mapper.createObjectNode();
            out.put("stockCode", trKey);
            if (price != null) out.put("price", price);
            out.put("tradeTime", tradeTime);

            stockBroadcastService.broadcast(out.toString());
        } catch (Exception e) {
            log.warn("REST 현재가 브로드캐스트 실패", e);
        }
    }

}
