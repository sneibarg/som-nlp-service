package org.springy.som.nlp.ollama.internal;

import org.springy.som.nlp.ollama.OllamaClient;
import org.springy.som.nlp.ollama.OllamaCompletion;
import org.springy.som.nlp.ollama.OllamaException;
import org.springy.som.nlp.ollama.OllamaRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Completes a prompt with the local Ollama service.
 */
@RestController
@RequestMapping("/ollama")
public class OllamaController {

    private final OllamaClient client;

    public OllamaController(OllamaClient client) {
        this.client = client;
    }

    /**
     * @param body the model, prompts, and optional JSON schema
     * @return the completion text
     */
    @PostMapping("/completions")
    public OllamaCompletion complete(@RequestBody OllamaCompletionBody body) {
        return new OllamaCompletion(client.complete(toRequest(body)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(OllamaException.class)
    public ResponseEntity<Map<String, String>> upstream(OllamaException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    private static OllamaRequest toRequest(OllamaCompletionBody body) {
        if (body == null) {
            return null;
        }
        return new OllamaRequest(body.model(), body.system(), body.user(), schemaText(body.jsonSchema()));
    }

    private static String schemaText(JsonNode schema) {
        if (schema == null || schema.isNull()) {
            return null;
        }
        if (schema.isString()) {
            return schema.asString();
        }
        return schema.toString();
    }
}
