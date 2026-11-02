package org.springy.som.nlp.ollama;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Sends a prompt to the local Ollama service. A request can be plain text or a JSON schema,
 * so the same client can serve a judge request and any other request type.
 */
@Service
@Slf4j
public class OllamaClient {

    static final String USER_REQUIRED = "User prompt cannot be null or empty";
    static final String MODEL_REQUIRED = "Ollama model cannot be null or empty";
    static final String EMPTY_RESPONSE = "Ollama returned an empty response";
    static final String UPSTREAM_FAILURE = "Ollama could not complete the request";

    private final OllamaChatModel chatModel;

    public OllamaClient(OllamaChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Completes the request and returns the model text.
     * @param request the model, prompts, and optional JSON schema
     * @return the completion text
     */
    public String complete(OllamaRequest request) {
        if (request == null || request.user() == null || request.user().isBlank()) {
            throw new IllegalArgumentException(USER_REQUIRED);
        }
        Prompt prompt = new Prompt(messages(request), options(request));
        try {
            ChatResponse response = chatModel.call(prompt);
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new OllamaException(EMPTY_RESPONSE);
            }
            String text = response.getResult().getOutput().getText();
            if (text == null || text.isBlank()) {
                throw new OllamaException(EMPTY_RESPONSE);
            }
            return text;
        }
        catch (OllamaException ex) {
            throw ex;
        }
        catch (RuntimeException ex) {
            log.warn("Ollama call failed: {}", ex.getMessage());
            throw new OllamaException(UPSTREAM_FAILURE);
        }
    }

    /**
     * Completes the request and converts the answer into {@code type}.
     * A schema on the request is sent as-is. Otherwise the schema is taken from {@code type}.
     * @param request the model, prompts, and optional JSON schema
     * @param type the answer type
     * @param <T> the answer type
     * @return the converted answer
     */
    public <T> T complete(OllamaRequest request, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Response type cannot be null");
        }
        if (request == null) {
            throw new IllegalArgumentException(USER_REQUIRED);
        }
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        String schema = request.jsonSchema();
        if (schema == null || schema.isBlank()) {
            schema = converter.getJsonSchema();
        }
        String text = complete(new OllamaRequest(request.model(), request.system(), request.user(), schema));
        try {
            T value = converter.convert(text);
            if (value == null) {
                throw new OllamaException(EMPTY_RESPONSE);
            }
            return value;
        }
        catch (OllamaException ex) {
            throw ex;
        }
        catch (RuntimeException ex) {
            log.warn("Ollama response did not match {}: {}", type.getSimpleName(), ex.getMessage());
            throw new OllamaException(UPSTREAM_FAILURE);
        }
    }

    private List<Message> messages(OllamaRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(new SystemMessage(request.system()));
        }
        messages.add(new UserMessage(request.user()));
        return messages;
    }

    private OllamaChatOptions options(OllamaRequest request) {
        OllamaChatOptions.Builder options = OllamaChatOptions.builder().model(model(request));
        String schema = request.jsonSchema();
        if (schema != null && !schema.isBlank()) {
            try {
                options.outputSchema(schema).disableThinking();
            }
            catch (RuntimeException ex) {
                throw new IllegalArgumentException("JSON schema is not valid");
            }
        }
        return options.build();
    }

    private String model(OllamaRequest request) {
        if (request.model() != null && !request.model().isBlank()) {
            return request.model().trim();
        }
        String configured = chatModel.getOptions() == null ? null : chatModel.getOptions().getModel();
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException(MODEL_REQUIRED);
        }
        return configured;
    }
}
