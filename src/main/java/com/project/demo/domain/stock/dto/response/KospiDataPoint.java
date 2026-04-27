package com.project.demo.domain.stock.dto.response;

import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KospiDataPoint {
    private String date;   // YYYYMMDD (일별) 또는 HHmm (분별)
    private double value;  // 지수값
}
