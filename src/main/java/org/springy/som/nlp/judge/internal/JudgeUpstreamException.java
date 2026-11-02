package org.springy.som.nlp.judge.internal;

/**
 * The model call failed, or the model returned a verdict this service will not accept.
 */
public class JudgeUpstreamException extends RuntimeException {

    public JudgeUpstreamException(String message) {
        super(message);
    }
}
