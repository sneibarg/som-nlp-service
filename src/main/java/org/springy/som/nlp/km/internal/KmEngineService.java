package org.springy.som.nlp.km.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class KmEngineService {

    private final KmEngineBridge bridge;
    private final KmEngineBridge.Session baseline;
    private final AtomicReference<KmEngineBridge.Session> session;

    public KmEngineService(KmEngineBridge bridge, KmKnowledgeBase knowledgeBase) {
        this.bridge = bridge;
        this.baseline = knowledgeBase.startupSession();
        this.session = new AtomicReference<>(baseline);
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

    /**
     * Creates an agentic player's situation if needed and evaluates there atomically.
     * Both facts and inferred state are retained only after a successful evaluation.
     */
    public synchronized KmResult evaluatePlayer(String player, String expression) {
        if (player == null || player.isBlank()) {
            throw new IllegalArgumentException("Player cannot be null or empty");
        }
        if (!player.matches("\\*?[A-Za-z0-9][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Player must contain only letters, digits, underscores or hyphens, with an optional leading *");
        }
        String situation = player.startsWith("*") ? player : "*" + player;
        if ("*Global".equalsIgnoreCase(situation)) {
            throw new IllegalArgumentException("Player cannot be Global");
        }
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }
        try {
            var created = bridge.evaluate(session.get(), "(in-situation *Global (" + situation
                    + " has (instance-of (Som-Agentic-Player))))");
            if (!created.ok() || !created.errors().isEmpty()) {
                return KmResult.from(created);
            }
            var evaluation = bridge.evaluateInSituation(created.nextSession(), situation, expression);
            if (evaluation.ok() && evaluation.errors().isEmpty()) {
                session.set(evaluation.nextSession());
            }
            return KmResult.from(evaluation);
        } catch (RuntimeException ex) {
            log.error("Knowledge base player evaluation failed", ex);
            throw new KmEngineException(ex);
        }
    }

    /**
     * Evaluates one question from the startup session and discards the derived frames.
     * Assertions made through {@link #evaluate(String)} stay on the service session.
     */
    public synchronized List<KmResult> evaluateBaseline(List<String> expressions) {
        if (expressions == null || expressions.isEmpty()) {
            throw new IllegalArgumentException("Expressions cannot be null or empty");
        }
        for (String expression : expressions) {
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException("Expression cannot be null or empty");
            }
        }
        try {
            var current = baseline;
            var results = new ArrayList<KmResult>();
            for (String expression : expressions) {
                var evaluation = bridge.evaluate(current, expression);
                results.add(KmResult.from(evaluation));
                if (!evaluation.ok() || !evaluation.errors().isEmpty()) {
                    return results;
                }
                current = evaluation.nextSession();
            }
            return results;
        } catch (RuntimeException ex) {
            log.error("Knowledge base evaluation failed", ex);
            throw new KmEngineException(ex);
        }
    }
}
