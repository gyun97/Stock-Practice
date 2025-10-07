package com.project.demo.domain.stock.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CandleResponse {
    private String date;   // yyyyMMdd
    private String time;   // HHmmss
    private int open;
    private int high;
    private int low;
    private int close;
    private long volume;
}
