package org.springy.som.nlp.ollama;

/**
 * One completion sent to the local Ollama service.
 * @param model the Ollama model name, or blank to use the configured default
 * @param system the system prompt, or blank when the request has none
 * @param user the user prompt
 * @param jsonSchema a JSON schema the answer must match, or blank for plain text
 */
public record OllamaRequest(String model, String system, String user, String jsonSchema) {
}
