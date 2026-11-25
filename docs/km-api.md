# Knowledge Machine HTTP API

The service on port 9081 exposes the configured KM knowledge base through:

| Endpoint | JSON body | Purpose |
| --- | --- | --- |
| `POST /km/assertions` | `{"expression":"(Fred has (color (red)))"}` | Assert facts using a KM expression. |
| `POST /km/query` | `{"expression":"(the color of Fred)"}` | Query using a KM expression. |

Send `Content-Type: application/json`. Both routes call the packaged KM engine.
The query above returns `answer: "(red)"` in a JSON result with `ok`, `input`,
`errors`, `errorExpressions`, `warnings`, and optional engine diagnostics.
Answers use KM's printed syntax; an empty answer is the string `nil`.
The Clojure session and knowledge-base internals are not serialized.

Before the API service is created, startup synchronously calls `som.kb/load-lib`,
the library API behind `(load-lib)`,
for the KM component library, then for the SOM component library, carrying the
returned session from the first load into the second. Both loads must complete
without engine errors. Missing, empty, unreadable, or failed libraries prevent
startup, so neither assertions nor queries can run against a partially loaded KB.

This is stricter than the KM prompt: the prompt can return after `(load-lib)`
with recorded errors and retain the resulting session. Its normal reply prints
a general reasoning warning rather than every diagnostic. Reaching `KM>` again
does not establish that the library loaded without errors. The service requires
an empty error list from each load before it exposes either endpoint.

Configure `km.component-library` (`KM_COMPONENT_LIBRARY`) and
`km.som-component-library` (`SOM_COMPONENT_LIBRARY`) as filesystem directories.
Defaults point into the sibling `som-knowledge-machine` checkout: respectively
`src/resources/km-component-library/rkf-clib/components` and
`src/resources/som-component-library`. Deployments must provide these directories
and a KM engine artifact implementing `(load-lib)`; the directories are not
bundled in this service JAR. Startup logs the engine resource URL so the actual
runtime JAR can be identified. A missing `som.kb` loader fails initialization
with that URL and an explicit artifact requirement.

For a Maven launch with the same 4 GiB heap as `clojure -J-Xmx4g -M:repl`, use:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.5"
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Xmx4g'
```

For IntelliJ, the heap belongs in the run configuration's VM options. A setting
such as `-Xmx8G` already exceeds 4 GiB. Taxonomy diagnostics and refused expressions
are distinct from a JVM out-of-memory failure; increasing the heap does not
change the component declarations or command parsing.

An optional `km.knowledge-base` (`KM_KNOWLEDGE_BASE`) file loads after both libraries.
The API service receives that initialized inference engine session and KB.
Calls are serialized. Successful assertions persist across subsequent calls;
queries also retain the engine's inferred state. Failed evaluations leave the
previous knowledge-base value in place. Restarting restores the configured
libraries and optional startup file rather than persisting runtime assertions.

The two routes provide the same KM evaluator under names that express caller
intent. They accept trusted KM text, including the engine's file commands;
`/km/query` is not a read-only sandbox. Discarding a failed evaluation's KB
does not undo filesystem effects from KM commands.

Missing or blank expressions, malformed JSON, read failures, and reasoning
errors return 400. A not-ported expression returns 501. An unexpected engine
failure returns 500 with a stable message. Error responses use
`{"error":"<message>"}`. Successful empty answers remain HTTP 200.
