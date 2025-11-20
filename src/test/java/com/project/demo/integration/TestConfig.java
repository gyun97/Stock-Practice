package com.project.demo.integration;

import com.project.demo.domain.stock.service.InitStockSubscribe;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 통합 테스트용 설정
 * 외부 API 호출을 하는 컴포넌트를 Mock으로 대체
 */
@TestConfiguration
@Profile("test")
public class TestConfig {

    @MockitoBean
    private InitStockSubscribe initStockSubscribe;
}

