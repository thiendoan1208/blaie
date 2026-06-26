package com.blaie.blaie_be;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"blaie.auth.access-token-secret=context-test-access-secret-at-least-32-bytes",
		"blaie.email.provider=log",
		"blaie.email.from=Blaie <no-reply@test.local>",
		"blaie.email.web-base-url=http://localhost:3000",
		"blaie.email.api-base-url=http://localhost:8080/api/v1",
		"blaie.email.verification-ttl=24h"
})
class BlaieBeApplicationTests {

	@Test
	void contextLoads() {
	}

}
