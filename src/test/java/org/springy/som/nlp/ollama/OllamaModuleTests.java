package org.springy.som.nlp.ollama;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springy.som.nlp.ollama.internal.OllamaController;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ApplicationModuleTest
class OllamaModuleTests {

	@Autowired
	private OllamaClient ollamaClient;

	@Autowired
	private OllamaController ollamaController;

	@MockitoBean
	private OllamaChatModel chatModel;

	@Test
	void completesARequestWithoutCallingOllama() {
		assertNotNull(ollamaClient);
		assertNotNull(ollamaController);
		when(chatModel.call(any(Prompt.class)))
				.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("ok")))));

		assertEquals("ok", ollamaClient.complete(new OllamaRequest("mistral", null, "Hello", null)));
	}
}
