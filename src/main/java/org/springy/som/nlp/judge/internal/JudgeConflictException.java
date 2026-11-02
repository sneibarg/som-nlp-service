package org.springy.som.nlp.judge.internal;

/**
 * A request type or verdict name is already in use.
 */
public class JudgeConflictException extends RuntimeException {

    public JudgeConflictException(String message) {
        super(message);
    }
}
