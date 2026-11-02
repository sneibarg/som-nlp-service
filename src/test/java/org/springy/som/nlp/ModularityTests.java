package org.springy.som.nlp;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

	@Test
	void verifiesModuleStructure() {
		ApplicationModules.of(NlpServiceApplication.class).verify();
	}
}
