package org.springy.som.nlp.km.internal;

class KmEngineException extends RuntimeException {

    KmEngineException(Throwable cause) {
        super("Knowledge base evaluation failed", cause);
    }
}
