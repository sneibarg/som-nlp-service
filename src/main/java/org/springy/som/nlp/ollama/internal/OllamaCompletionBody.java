package org.springy.som.nlp.ollama.internal;

import tools.jackson.databind.JsonNode;

/**
 * JSON body for {@code POST /ollama/completions}.
 * {@code jsonSchema} may be a schema object or a schema string.
 * @param model the Ollama model name, or blank to use the configured default
 * @param system the system prompt, or blank when the request has none
 * @param user the user prompt
 * @param jsonSchema the JSON schema the answer must match, or blank for plain text
 */
public record OllamaCompletionBody(String model, String system, String user, JsonNode jsonSchema) {
}
