package com.project.demo.domain.portfolio.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RankingResponse {
    
    private Long userId;
    private String userName;
    private long totalAsset;
    private double returnRate;
    private int rank;
}

