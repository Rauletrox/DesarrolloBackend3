package com.backen3.Duoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.batch.autorun=false",
		"spring.datasource.url=jdbc:h2:mem:duoc_test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.sql.init.mode=never",
		"spring.batch.jdbc.initialize-schema=embedded"
})
class DuocApplicationTests {

	@Test
	void contextLoads() {
	}

}
