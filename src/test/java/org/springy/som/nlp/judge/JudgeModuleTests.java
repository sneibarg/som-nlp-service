package org.springy.som.nlp.judge;

import org.junit.jupiter.api.Test;
import org.springy.som.nlp.judge.internal.JudgeRepository;
import org.springy.som.nlp.judge.internal.JudgeUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ApplicationModuleTest
class JudgeModuleTests {

	@Autowired
	private JudgeService judgeService;

	@Autowired
	private JudgeCatalog judgeCatalog;

	@MockitoBean
	private JudgeRepository judgeRepository;

	@Test
	void exposesTheJudge() {
		assertNotNull(judgeService);
		JudgeUnavailableException failure = assertThrows(JudgeUnavailableException.class,
				() -> judgeService.judge("Sophia works at Stanford.", "Sophia works at Stanford."));
		assertEquals("Judges are defined in the judges collection", failure.getMessage());
		verifyNoInteractions(judgeRepository);
	}

	@Test
	void storesARequestTypeWithoutMongo() {
		assertNotNull(judgeCatalog);
		when(judgeRepository.existsByName("tone")).thenReturn(false);
		when(judgeRepository.save(any(RequestType.class))).thenAnswer(invocation -> invocation.getArgument(0));

		RequestType request = new RequestType();
		request.setId("client-id");
		request.setName(" tone ");

		RequestType stored = judgeCatalog.createRequest(request);

		assertEquals("tone", stored.getName());
		assertNull(stored.getId());
	}
}
