package org.springy.som.nlp.km.internal;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class KmEngineBridge {

    private static final String ENGINE = "som.km.engine";
    private static final Keyword KB = Keyword.intern("kb");
    private static final Keyword QUERY = Keyword.intern("query");
    private static final Keyword VALUE = Keyword.intern("value");

    private final IFn status;
    private final IFn session;
    private final IFn evaluate;
    private final IFn format;
    private final IFn loadLibrary;

    public KmEngineBridge() {
        var source = Thread.currentThread().getContextClassLoader().getResource("som/km/engine.clj");
        log.info("Initializing KM runtime from {}", source);
        try {
            Clojure.var("clojure.core", "require").invoke(Clojure.read(ENGINE));
            Clojure.var("clojure.core", "require").invoke(Clojure.read("som.kb"));
        } catch (RuntimeException ex) {
            log.error("KM runtime initialization failed for {}", source, ex);
            throw new IllegalStateException("KM runtime at " + source
                    + " could not initialize. The KM artifact must include som/kb.clj and som.kb/load-lib.", ex);
        }
        status = Clojure.var(ENGINE, "status");
        session = Clojure.var(ENGINE, "session");
        evaluate = Clojure.var(ENGINE, "evaluate");
        format = Clojure.var("som.km.print", "km-format");
        loadLibrary = Clojure.var("som.kb", "load-lib");
    }

    public Status status() {
        IPersistentMap result = (IPersistentMap) status.invoke();
        return new Status(string(result, "name"), string(result, "km-version"),
                string(result, "license"), string(result, "copyright"),
                string(result, "evaluator"), strings(result, "interfaces"));
    }

    public Session newSession() {
        return new Session((IPersistentMap) session.invoke());
    }

    /**
     * Evaluates trusted KM text, which can invoke the engine's filesystem commands.
     * The caller carries the returned session into its next evaluation.
     */
    public Evaluation evaluate(Session currentSession, String expression) {
        Objects.requireNonNull(currentSession, "Session is required");
        IPersistentMap result = (IPersistentMap) evaluate.invoke(expression, currentSession.state);
        return evaluation(currentSession, result);
    }

    /**
     * Loads a component directory through the library API and returns the resulting session.
     * This is the same loader that handles the KM load-lib command.
     */
    public Evaluation loadLibrary(Session currentSession, String directory) {
        Objects.requireNonNull(currentSession, "Session is required");
        if (directory == null || directory.isBlank()) {
            throw new IllegalArgumentException("Component directory cannot be null or empty");
        }
        IPersistentMap result = (IPersistentMap) loadLibrary.invoke(currentSession.state.valAt(KB),
                currentSession.state.valAt(QUERY), directory, Keyword.intern("silent"), true);
        // The loader returns state and diagnostics, without the evaluator's :ok envelope.
        return evaluation(currentSession, result.assoc(Keyword.intern("ok"),
                result.valAt(Keyword.intern("error")) == null));
    }

    private Evaluation evaluation(Session currentSession, IPersistentMap result) {
        Object kb = result.valAt(KB);
        // The engine also returns state for reasoning failures that made partial changes.
        Session nextSession = kb == null ? currentSession
                : new Session(PersistentArrayMap.createAsIfByAssoc(new Object[]{KB, kb, QUERY, result.valAt(QUERY)}));
        String answer = result.containsKey(VALUE) ? (String) format.invoke(result.valAt(VALUE)) : null;
        List<String> errorExpressions = values(result, "error-structures").stream()
                .map(value -> (String) format.invoke(value))
                .toList();
        return new Evaluation(nextSession, flag(result, "ok"), string(result, "input"), answer,
                string(result, "error"), string(result, "message"), flag(result, "incomplete"),
                strings(result, "errors"), errorExpressions, strings(result, "warnings"),
                string(result, "text"), string(result, "statistics"));
    }

    private static String string(IPersistentMap result, String key) {
        return (String) result.valAt(Keyword.intern(key));
    }

    private static boolean flag(IPersistentMap result, String key) {
        return Boolean.TRUE.equals(result.valAt(Keyword.intern(key)));
    }

    private static List<?> values(IPersistentMap result, String key) {
        return (List<?>) result.valAt(Keyword.intern(key), List.of());
    }

    private static List<String> strings(IPersistentMap result, String key) {
        return values(result, key).stream().map(String.class::cast).toList();
    }

    public static final class Session {
        private final IPersistentMap state;

        private Session(IPersistentMap state) {
            this.state = state;
        }
    }

    public record Status(String name, String kmVersion, String license, String copyright,
                         String evaluator, List<String> interfaces) {
    }

    /**
     * The answer and error expressions use KM's printed syntax. A missing answer has a null
     * value, and an empty answer produced by the interpreter is printed as nil.
     */
    public record Evaluation(Session nextSession, boolean ok, String input, String answer,
                             String error, String message, boolean incomplete, List<String> errors,
                             List<String> errorExpressions, List<String> warnings, String text,
                             String statistics) {
    }
}
