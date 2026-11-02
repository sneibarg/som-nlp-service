package org.springy.som.nlp.judge.internal;

/**
 * A request type or verdict is not in the judges collection.
 */
public class JudgeNotFoundException extends RuntimeException {

    public JudgeNotFoundException(String message) {
        super(message);
    }
}
