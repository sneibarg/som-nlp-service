package org.springy.som.nlp.judge.internal;

/**
 * The judge cannot call the model because it is not configured.
 */
public class JudgeUnavailableException extends RuntimeException {

    public JudgeUnavailableException(String message) {
        super(message);
    }
}
