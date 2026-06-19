package com.blaie.blaie_be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "blaie.auth.access-token-secret=context-test-access-secret-at-least-32-bytes")
class BlaieBeApplicationTests {

	@Test
	void contextLoads() {
	}

}
