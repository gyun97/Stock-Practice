package com.project.demo.domain.candle;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "candles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ticker;   // 종목코드 (예: 005930)
    private String date;     // 영업일자 (yyyyMMdd)
    private String time;     // 체결시간 (HHmmss)

    private Long open;       // 시가
    private Long high;       // 고가
    private Long low;        // 저가
    private Long close;      // 종가
    private Long volume;     // 거래량
}