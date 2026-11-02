package org.springy.som.nlp.judge.internal;

import org.junit.jupiter.api.Test;
import org.springy.som.nlp.judge.JudgeVerdict;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XaiVerdictModelTest {

    private final OpenAiChatModel chatModel = mock(OpenAiChatModel.class);

    @Test
    void refusesToCallWhenTheKeyIsUnset() {
        XaiVerdictModel model = new XaiVerdictModel(chatModel, "_", XaiVerdictModel.DEFAULT_MODEL);

        JudgeUnavailableException failure = assertThrows(JudgeUnavailableException.class,
                () -> model.judge("system", "user"));

        assertEquals("Set XAI_API_KEY to call the LLM judge", failure.getMessage());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void parsesAStructuredVerdict() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            OpenAiChatOptions options = (OpenAiChatOptions) prompt.getOptions();
            assertEquals("grok-4.7", options.getModel());
            assertTrue(options.getResponseFormat().getJsonSchema().contains("\"supported\""));
            String json = """
                    {"verdict":"contradicted","score":0.8,"rationale":"The text says the opposite."}
                    """;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
        });
        XaiVerdictModel model = new XaiVerdictModel(chatModel, "test-key", "grok-4.7");

        JudgeVerdict verdict = model.judge("system", "user");

        assertEquals("contradicted", verdict.verdict());
        assertEquals(0.8, verdict.score());
        assertEquals("The text says the opposite.", verdict.rationale());
    }

    @Test
    void reportsAnUpstreamFailureWhenTheCallThrows() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("connection reset"));
        XaiVerdictModel model = new XaiVerdictModel(chatModel, "test-key", "grok-4.7");

        JudgeUpstreamException failure = assertThrows(JudgeUpstreamException.class,
                () -> model.judge("system", "user"));

        assertEquals("The LLM judge could not complete the request", failure.getMessage());
    }
}
