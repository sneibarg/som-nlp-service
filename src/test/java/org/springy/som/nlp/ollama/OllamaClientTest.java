package org.springy.som.nlp.ollama;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.ThinkOption;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OllamaClientTest {

    private final OllamaChatModel chatModel = mock(OllamaChatModel.class);

    private final OllamaClient client = new OllamaClient(chatModel);

    @Test
    void rejectsABlankUserPrompt() {
        assertThrows(IllegalArgumentException.class,
                () -> client.complete(new OllamaRequest("mistral", "system", "  ", null)));

        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void usesTheConfiguredModelWhenTheRequestOmitsOne() {
        when(chatModel.getOptions()).thenReturn(OllamaChatOptions.builder().model("mistral").build());
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            assertEquals("mistral", options.getModel());
            assertNull(options.getFormat());
            assertNull(options.getThinkOption());
            assertEquals(1, prompt.getInstructions().size());
            assertEquals("Hello", prompt.getInstructions().get(0).getText());
            return text("ok");
        });

        assertEquals("ok", client.complete(new OllamaRequest(null, "  ", "Hello", null)));
    }

    @Test
    void sendsASchemaForAStructuredRequest() {
        when(chatModel.call(any(Prompt.class))).thenAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            OllamaChatOptions options = (OllamaChatOptions) prompt.getOptions();
            assertEquals("llama3.2", options.getModel());
            assertEquals(ThinkOption.ThinkBoolean.DISABLED, options.getThinkOption());
            assertEquals("Be brief.", prompt.getInstructions().get(0).getText());
            assertEquals("Grade this.", prompt.getInstructions().get(1).getText());
            return text("{\"label\":\"good\"}");
        });

        String schema = """
                {"type":"object","properties":{"label":{"type":"string"}},"required":["label"]}
                """;
        String text = client.complete(new OllamaRequest(" llama3.2 ", "Be brief.", "Grade this.", schema));

        assertEquals("{\"label\":\"good\"}", text);
    }

    @Test
    void reportsAnEmptyResponse() {
        when(chatModel.call(any(Prompt.class))).thenReturn(text("  "));

        OllamaException failure = assertThrows(OllamaException.class,
                () -> client.complete(new OllamaRequest("mistral", null, "Hello", null)));

        assertEquals(OllamaClient.EMPTY_RESPONSE, failure.getMessage());
    }

    @Test
    void reportsAnUpstreamFailureWhenTheCallThrows() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("connection reset"));

        OllamaException failure = assertThrows(OllamaException.class,
                () -> client.complete(new OllamaRequest("mistral", null, "Hello", null)));

        assertEquals(OllamaClient.UPSTREAM_FAILURE, failure.getMessage());
    }

    @Test
    void convertsAStructuredAnswer() {
        when(chatModel.call(any(Prompt.class))).thenReturn(text("{\"label\":\"good\"}"));

        Tone tone = client.complete(new OllamaRequest("mistral", null, "Grade this.", null), Tone.class);

        assertEquals("good", tone.label());
    }

    private static ChatResponse text(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    public record Tone(String label) {
    }
}
