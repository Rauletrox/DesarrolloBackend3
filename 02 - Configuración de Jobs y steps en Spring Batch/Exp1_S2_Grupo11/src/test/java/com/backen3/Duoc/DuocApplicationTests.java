package com.backen3.Duoc;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
// Se usa el perfil aislado para cargar el contexto sin depender de MySQL local.
@ActiveProfiles("test")
class DuocApplicationTests {

	@Test
	void contextLoads() {
	}

}
