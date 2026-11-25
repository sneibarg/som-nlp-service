package org.springy.som.nlp.km.internal;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentArrayMap;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;

@Slf4j
@Component
public class KmEngineBridge {

    private static final String ENGINE = "som.km.engine";
    private static final Logger EXPRESSIONS = LoggerFactory.getLogger("km.expressions");
    private static final Keyword KB = Keyword.intern("kb");
    private static final Keyword QUERY = Keyword.intern("query");
    private static final Keyword VALUE = Keyword.intern("value");

    private final IFn status;
    private final IFn session;
    private final IFn evaluate;
    private final IFn format;
    private final IFn readShippedConfig;
    private final IFn configuredLibraries;
    private final IFn readTerm;
    private final IFn kmList;

    public KmEngineBridge() {
        var source = Thread.currentThread().getContextClassLoader().getResource("som/km/engine.clj");
        log.info("Initializing KM runtime from {}", source);
        try {
            Clojure.var("clojure.core", "require").invoke(Clojure.read(ENGINE));
            Clojure.var("clojure.core", "require").invoke(Clojure.read("som.km.configured"));
        } catch (RuntimeException ex) {
            log.error("KM runtime initialization failed for {}", source, ex);
            throw new IllegalStateException("KM runtime at " + source
                    + " could not initialize. The KM artifact must include som.km.configured startup support.", ex);
        }
        status = Clojure.var(ENGINE, "status");
        session = Clojure.var(ENGINE, "session");
        evaluate = Clojure.var(ENGINE, "evaluate");
        format = Clojure.var("som.km.print", "km-format");
        readShippedConfig = Clojure.var("som.km.configured", "read-shipped-config");
        configuredLibraries = Clojure.var("som.km.configured", "libraries");
        readTerm = Clojure.var("som.km.read", "read-term");
        kmList = Clojure.var("som.km.term", "km-list");
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
        return evaluateLogged(currentSession, "current", expression,
                () -> (IPersistentMap) evaluate.invoke(expression, currentSession.state));
    }

    /**
     * Evaluates one parsed expression in a player's situation and restores the prior situation.
     * The situation name must be validated by the caller.
     */
    public Evaluation evaluateInSituation(Session currentSession, String situation, String expression) {
        return evaluateLogged(currentSession, situation, expression, () -> {
            IPersistentMap parsed = (IPersistentMap) readTerm.invoke(expression);
            if (!flag(parsed, "ok")) {
                return parsed.assoc(Keyword.intern("input"), expression);
            }
            // Wrap the parsed term rather than raw text so parentheses or comments cannot escape the body.
            Object body = kmList.invoke(Clojure.read("in-situation"), Clojure.read(situation), parsed.valAt(VALUE));
            Object scoped = kmList.invoke(Clojure.read("in-situation"), Clojure.read("*Global"), body);
            IPersistentMap result = (IPersistentMap) evaluate.invoke(format.invoke(scoped), currentSession.state);
            return result.assoc(Keyword.intern("input"), expression);
        });
    }

    private Evaluation evaluateLogged(Session currentSession, String situation, String expression,
                                      Supplier<IPersistentMap> operation) {
        String id = UUID.randomUUID().toString();
        EXPRESSIONS.info("event=started id={} situation={} expression={}", id, singleLine(situation), singleLine(expression));
        try {
            Evaluation result = evaluation(currentSession, operation.get());
            EXPRESSIONS.info("event=completed id={} success={} error={} message={} errors={} warnings={}", id,
                    result.ok() && result.errors().isEmpty(), singleLine(result.error()),
                    singleLine(result.message()), singleLine(result.errors().toString()), singleLine(result.warnings().toString()));
            return result;
        } catch (RuntimeException ex) {
            EXPRESSIONS.error("event=failed id={} exception={} message={}", id,
                    ex.getClass().getName(), singleLine(ex.getMessage()));
            throw ex;
        }
    }

    private static String singleLine(String value) {
        if (value == null) {
            return "null";
        }
        return value.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n")
                .replace("\t", "\\t").replace("\u0085", "\\u0085")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }

    /**
     * Returns the engine's bundled configured startup plan without rewriting its resource paths.
     */
    public List<Library> configuredLibraries() {
        IPersistentMap config = (IPersistentMap) readShippedConfig.invoke();
        Keyword componentLibraries = Keyword.intern("component-libraries");
        List<?> configuredPaths = (List<?>) config.valAt(componentLibraries);
        List<?> entries = (List<?>) configuredLibraries.invoke(config);
        return IntStream.range(0, entries.size())
                .mapToObj(index -> {
                    IPersistentMap entry = (IPersistentMap) entries.get(index);
                    String directory = (String) configuredPaths.get(index);
                    if (directory.isBlank()) {
                        throw new IllegalArgumentException("Component directory cannot be null or empty");
                    }
                    return new Library(directory, string(entry, "label"), string(entry, "expression"));
                })
                .toList();
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

    public record Library(String directory, String label, String expression) {
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
