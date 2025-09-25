package com.project.demo.domain.stock.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandleResponse {
    private long time;     // epoch seconds
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}


