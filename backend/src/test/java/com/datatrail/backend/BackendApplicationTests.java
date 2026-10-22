package com.datatrail.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = "datatrail.capture.enabled=false")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
