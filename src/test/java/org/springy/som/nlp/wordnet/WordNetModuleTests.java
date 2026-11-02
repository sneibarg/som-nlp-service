package org.springy.som.nlp.wordnet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ApplicationModuleTest
class WordNetModuleTests {

	@Autowired
	private WordNetParser wordNetParser;

	@Test
	void exposesTheDictionary() {
		assertNotNull(wordNetParser);
	}
}
