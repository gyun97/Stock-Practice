package com.project.demo;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import com.project.demo.integration.TestConfig;
import com.project.demo.integration.AbstractIntegrationTest;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestConfig.class)
class StockApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
