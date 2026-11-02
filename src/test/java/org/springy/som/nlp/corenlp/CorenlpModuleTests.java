package org.springy.som.nlp.corenlp;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ApplicationModuleTest
class CorenlpModuleTests {

	@Autowired
	private StanfordCoreNLP stanfordCoreNLP;

	@Test
	void exposesThePipeline() {
		assertNotNull(stanfordCoreNLP, "StanfordCoreNLP bean should be created");
	}
}
