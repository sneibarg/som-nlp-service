package org.springy.som.nlp.ollama;

/**
 * The text returned by the local Ollama service.
 * @param text the completion. When the request included a JSON schema, this is that JSON document.
 */
public record OllamaCompletion(String text) {
}
