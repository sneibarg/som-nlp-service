package org.springy.som.nlp.km.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class KmEngineService {

    private final KmEngineBridge bridge;
    private final AtomicReference<KmEngineBridge.Session> session;

    public KmEngineService(KmEngineBridge bridge, KmKnowledgeBase knowledgeBase) {
        this.bridge = bridge;
        this.session = new AtomicReference<>(knowledgeBase.startupSession());
    }

    /**
     * Returns the evaluation of one KM expression against the service's current knowledge base.
     * Successful queries retain inference state just as assertions retain their facts.
     */
    public synchronized KmResult evaluate(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }
        try {
            var evaluation = bridge.evaluate(session.get(), expression);
            // A failed inference can return a partially changed KB; keep the previous value.
            if (evaluation.ok() && evaluation.errors().isEmpty()) {
                session.set(evaluation.nextSession());
            }
            return KmResult.from(evaluation);
        } catch (RuntimeException ex) {
            log.error("Knowledge base evaluation failed", ex);
            throw new KmEngineException(ex);
        }
    }
}
