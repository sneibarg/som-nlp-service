package org.springy.som.nlp.ollama;

/**
 * The local Ollama service did not return a usable completion.
 */
public class OllamaException extends RuntimeException {

    public OllamaException(String message) {
        super(message);
    }
}
