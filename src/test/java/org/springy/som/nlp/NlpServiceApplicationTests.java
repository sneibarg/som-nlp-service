package org.springy.som.nlp;

import org.junit.jupiter.api.Test;
import org.springy.som.nlp.judge.internal.JudgeRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = NlpServiceApplication.class)
class NlpServiceApplicationTests {

	/**
	 * The test profile does not open a Mongo client. The catalog still needs a repository bean.
	 */
	@MockitoBean
	private JudgeRepository judgeRepository;

	@Test
	void contextLoads() {
	}

}
