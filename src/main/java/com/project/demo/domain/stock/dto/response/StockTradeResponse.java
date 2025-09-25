package com.project.demo.domain.stock.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockTradeResponse {
    private String stockCode;        // 종목코드 (MKSC_SHRN_ISCD)
    private String tradeTime;        // 체결시간 (STCK_CNTG_HOUR)
    private String price;            // 현재가 (STCK_PRPR)
    private String compareSign;      // 전일 대비 부호 (PRDY_VRSS_SIGN)
    private String compare;          // 전일 대비 (PRDY_VRSS)
    private String compareRate;      // 전일 대비율 (PRDY_CTRT)
    private String volume;           // 체결 거래량 (CNTG_VOL)
    private String accumulatedVolume;// 누적 거래량 (ACML_VOL)
    private String accumulatedAmount;// 누적 거래대금 (ACML_TR_PBMN)
}
