# SOM-KM API architecture and alignment proposal

## Decision

Run the Clojure KM engine as a versioned library inside the Spring Boot NLP service. Give the Spring host one direct application module, `org.springy.som.nlp.km`, and let Spring MVC own the API on port 9081. Keep the Clojure repo as the source of truth for KM semantics, the Lisp specification, the prompt, and the engine tests. This design assumes a single application instance and trusted callers for the initial rollout. A separate worker process becomes appropriate if callers need stronger isolation or independent scaling.

```text
Client → Spring MVC /km/v1 → KM service and session registry
                                     ↓
                              JVM bridge → som.km.engine/evaluate
KM> prompt ────────────────────────────────────────────┘
```

Place the controller, service, session registry, bridge, and HTTP records in `km.internal` for the first release. Promote an entry point to the module base package when a Java caller actually needs one. The module has zero dependencies on `corenlp`, `wordnet`, `judge`, and `ollama`. KM expressions go straight to the engine. Use the existing Modulith BOM, core and test starters, `ApplicationModules.verify()`, and a standalone KM module test. The design needs no named interfaces, module annotations, events, registry starter, nested or open modules, runtime module graph, or documentation generator. [Spring Modulith verification](https://docs.spring.io/spring-modulith/reference/verification.html)

Publish a thin, versioned Maven JAR from SOM-KM, with its own POM, explicit runtime dependencies, classpath namespaces, and Simplified BSD notice. Keep the Clojure CLI workflow. Run a packaging spike first. Start the repackaged Boot JAR with `java -jar` from an unrelated working directory, load only the bridge namespaces, and exercise status, evaluation, and `(license)`. The Java adapter can resolve and cache the bridge functions with `clojure.java.api.Clojure` and `IFn`. Keep Clojure values behind the adapter. Reuse one Clojure encoder that yields JSON-compatible values for Jackson 3. [Clojure Java API](https://clojure.github.io/clojure/javadoc/clojure/java/api/Clojure.html) [Spring Boot executable JAR restrictions](https://docs.spring.io/spring-boot/specification/executable-jar/restrictions.html)

## HTTP and state

| Proposed route | Contract |
| --- | --- |
| `GET /km/v1/status` | Engine build information from `som.km.engine/status`. |
| `POST /km/v1/sessions` | Create a fresh vocabulary and query. Return 201, an opaque ID, and the session location. |
| `POST /km/v1/sessions/{id}/query` | Evaluate one UTF-8 KM expression as `text/plain`. Return the engine's public result as JSON. |
| `DELETE /km/v1/sessions/{id}` | Close the session and release its state. Return 204. |

Reuse the current outcome mapping. A completed form can return HTTP 200 with reasoning errors or an empty answer. Empty and unreadable expressions return 400. Unported forms return 501. Preserve the term encoding for lists, tagged symbols, keywords, ratios and dotted conses, and large integers. Preserve explanations, warnings, and diagnostics. Omit `:kb` and `:query` from all HTTP output. Use the host's `{"error":"<message>"}` envelope for adapter failures such as 404 for an unknown session, 415 for an unsupported media type, and 500 for an unexpected evaluator failure. Engine replies retain their existing `error` code and `message`.

Store the knowledge base and query as one opaque in-memory value per session, with TTL and capacity limits. Bind sessions to the authenticated caller where the endpoint serves multiple callers. Serialize the entire read, evaluate, and replace operation for a session, including close and expiry coordination. A slot read can finish unification or clone a prototype. Save both returned state values whenever the result carries a knowledge base, including engine aborts that return partial state. An empty or unreadable request has no replacement state. Avoid automatic retries because repeating an expression can apply it twice. Different sessions can use a bounded shared execution pool. Apply backpressure, and keep a session occupied until an evaluation has actually stopped, even if its request has timed out. Check the intended workloads before choosing body, execution, and session limits.

These sessions expire on process restart. Single-instance routing is part of the first release. Durable sessions or multiple replicas need a separate decision on ownership, a versioned state representation, and recovery. KM text exported by `save-kb` omits some runtime flags, so an export cannot serve as a complete session snapshot.

## Gaps and proposed changes

| File | Standard and proposed change |
| --- | --- |
| `src/som/km/server.clj`, `src/som/km/repl.clj`, `src/som/km/engine.clj` | One implementation of each behavior. Both adapters implement nearly identical `evaluate-in` state transitions. Put the pure evaluate-and-return-next-state operation in `som.km.engine` or a focused `som.km.session` namespace. The existing server and prompt, and the new JVM bridge, use it. Each adapter keeps its own session storage and locking. Keep `test/som/km/server_test.clj`, `test/som/km/repl_test.clj`, and the other `som.km.engine` importers on their current entry points. |
| `src/som/km/server.clj`, `test/som/km/server_test.clj` | Effects at the edges and one wire encoding. Move `client-body` and its private encoding helpers to a transport-neutral namespace such as `som.km.result`. The server and JVM bridge import the new namespace. Keep `server/client-body` as a delegating compatibility function during migration or update its direct importer `som.km.server-test` to call `som.km.result/client-body`. Keep the standalone start and stop functions with their socket adapter until cutover. |
| `src/som/km/loadkb.clj`, `src/som/km/trace.clj`, `src/som/km/interpreter.clj` | Effects at the edges. The current evaluator can read or write process-accessible paths through load, save, and trace commands. Pass explicit file capabilities from the adapter, enforced at the actual I/O boundary for nested evaluation and aliases as well. Keep filesystem support in the trusted prompt adapter. The Spring adapter starts with filesystem access disabled for query text. Exercise permitted commands and rejected effects through the same engine function. |
| `src/som/km/interpreter.clj`, `src/som/km/loadkb.clj`, `src/som/km/stats.clj` | Explicit inputs. Clock reads make statistics vary and currently originate in the evaluator. Pass a clock through the evaluation context with a production default owned by the adapter or public entry point. Preserve statistics output and use a fixed clock in targeted tests. |
| `src/som/km/license.clj`, `LICENSE`, `deps.edn` | Preserve the license and resolve dependencies through Maven Central. Include the full SOM-KM notice as a namespaced classpath resource and read it from there in packaged builds so `(license)` returns the full text. `som.km.interpreter` and `som.km.trace-test` continue importing `som.km.license`. Declare artifact metadata and runtime dependencies for the Maven consumer. |
| `README.md`, `docs/reference/Architecture.md`, `docs/migration/README.md` | Record behavior and adapter boundaries. The README still describes read-and-print and says the adapters hold no knowledge base. Update it to reflect evaluation and session ownership. When implementation changes the public boundary, update Architecture.md and add a migration note in the same change, following the current migration order. |
| Spring `src/main/resources/application.yml` | The only observed use of `ontology.km-server` is a declaration pointing to `http://malboji:8080/km`. The checked-in JDK adapter serves `/status` and `/query` at its root. Identify the deployed service, route prefix, and clients before using that setting for cutover or removing it. |

## Implementation sequence and acceptance

1. Capture the existing engine and wire contract with the Clojure suite. Extract shared session transitions and the public result encoder, then prove the library works in the executable Boot JAR. Keep Lisp parity work in its existing migration sequence.
2. Add the Spring module and versioned routes. Test session isolation, serialized updates, state-changing reads, legacy term encodings, close/expiry races, reasoning errors and abort state, and denial of filesystem effects reached through nested commands. Run `clojure -M:test`, the Spring `ModularityTests`, a standalone `@ApplicationModuleTest`, and MockMvc controller contract tests. Compare representative inference and explanations with the existing server, ignoring elapsed time in the statistics string.
3. Inventory callers and confirm whether they need the old shared session or any retained data. If compatibility is required, host the verified old routes in Spring for a transition with a deliberately scoped legacy session. Migrate callers to explicit sessions, verify any data transfer against their workloads, and retire the old listener after cutover.

This report is based on the checked-in sources and docs. The deployed listener, callers, and packaged runtime still require verification.
