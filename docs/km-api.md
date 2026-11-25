# Knowledge Machine HTTP API

The service on port 9081 exposes the configured KM knowledge base through:

| Endpoint | JSON body | Purpose |
| --- | --- | --- |
| `POST /km/assertions` | `{"expression":"(Fred has (color (red)))"}` | Assert facts using a KM expression. |
| `POST /km/query` | `{"expression":"(the color of Fred)"}` | Query using a KM expression. |
| `POST /km/players/{player}/assertions` | `{"expression":"(*Goal has (instance-of (Som-Level-Goal)) (som-target-level (5)))"}` | Assert facts in one agentic player's situation. |
| `POST /km/players/{player}/query` | `{"expression":"(the som-pace of *View)"}` | Query in that player's situation, retaining inferred state. |
| `POST /km/baseline` | `{"expressions":["(*Goal has (instance-of (Som-Level-Goal)))","(the som-notch of *View)"]}` | Evaluate one question from the loaded library and discard the derived frames. |

Send `Content-Type: application/json`. All routes call the packaged KM engine.
The query above returns `answer: "(red)"` in a JSON result with `ok`, `input`,
`errors`, `errorExpressions`, `warnings`, and optional engine diagnostics.
Answers use KM's printed syntax; an empty answer is the string `nil`.
The Clojure session and knowledge-base internals are not serialized.

KM expressions are written to a separate UTF-8 log at `logs/km-expressions.log`,
relative to the application's working directory. Set `KM_EXPRESSION_LOG_FILE`
or `km.expression-log.file` to change the file location. The log rotates daily
and at 10 MB, compresses archives, retains up to 30 days, and caps archives at
1 GB. Expression entries do not also appear in the console log.

Each engine evaluation writes a timestamped `event=started` entry containing a
unique `id`, the situation context, and the expression. A matching
`event=completed` records `success`, engine error code and message, reasoning errors, and
warnings; an unexpected exception produces `event=failed` with the exception
type and message. Line breaks, tabs, and backslashes are escaped so multiline
expressions remain one log entry. `situation=current` means the caller's
current engine context; player evaluations name the requested situation.
Startup loads, optional KB loads, baseline expressions, and generated player
declarations are included. Baseline entries record evaluations even though
their state is discarded; a successful generated player declaration can also
be discarded when the player's subsequent expression fails. These entries
trace engine evaluation, not durable KB commits. Validation failures rejected
before reaching the engine do not produce expression entries.

For example, assert a goal with `POST /km/players/PlayerA/assertions` and query
it with `POST /km/players/PlayerA/query`, using
`{"expression":"(the som-target-level of *Goal)"}`. The answer is `(5)`.
The first successful call creates `*PlayerA` as a `Som-Agentic-Player` in
`*Global`. `PlayerA` and `*PlayerA` select the same situation. Player names
contain ASCII letters, digits, underscores, and hyphens, starting with a letter
or digit, with an optional leading `*`. `Global` is reserved. Names preserve
case and must be sent consistently.

Player routes require a packaged engine containing `Som-Agentic-Player` and
the fluent SOM slot declarations. Repackage and publish the engine dependency
after adding those components; the service does not supply substitute classes.
Each expression runs inside `(in-situation *PlayerA ...)`, with the previous
situation restored afterward. Fluent values and computed answers remain in
that player's situation; global class rules are still visible. The same frame
names, such as `*Goal` and `*View`, may be used by different players. Non-fluent
slots follow KM's normal sharing rules.

Player creation and expression evaluation are serialized with all other KM
calls. Both commit together on success; read or reasoning errors discard both.
The original expression appears in the result's `input`. Responses and HTTP
errors use the same format as `/km/query`. Player state is in memory and is
lost on restart. `/km/baseline` remains independent of player assertions.
These routes accept trusted KM expressions, including explicit situation
changes and file commands; situation selection is not an access-control boundary.

Before the API service is created, startup uses the same configuration parser
and ordered load expressions as `clojure -M:configured`. It calls
`som.km.configured/read-shipped-config` and `som.km.configured/libraries`, then runs
each generated `(load-lib "path" :silent t)` expression through the engine,
carrying the returned session into the next entry. Every entry must complete
without recorded errors before the API endpoints become available.

The engine's bundled `resources/config.edn` selects the CLIB build `all`
in order: `core`, `specs`, `science/shared`, `science/biology`, `science/physics`,
`science/chemistry`, and `bsp`, followed by the SOM component library. It leaves
out the alternative halo and SME builds. Spring uses this packaged selection
rather than maintaining another copy. A selected directory such
as `specs` may contain no `.km` files; a successful empty load is allowed.

This is stricter than the KM prompt: the prompt can return after `(load-lib)`
with recorded errors and retain the resulting session. Its normal reply prints
a general reasoning warning rather than every diagnostic. Reaching `KM>` again
does not establish that the library loaded without errors. The service requires
an empty error list from each load before it exposes either endpoint.

The configuration, component files, and classpath resource index come from the
KM engine dependency. The engine's `load-lib` and `loadkb` resolve those packaged
resources directly, including when the engine is nested in the executable
Spring Boot JAR. The integration does not rewrite library paths, extract files,
or require either source checkout on the deployment machine.

There is no `km.config-file` or `km.working-directory` setting. Remove the old
`KM_CONFIG_FILE`, `KM_WORKING_DIRECTORY`, `KM_COMPONENT_LIBRARY`, and
`SOM_COMPONENT_LIBRARY` overrides. To change the selection or component content,
repackage and publish the engine JAR, then update the dependency and rebuild the
NLP service. The NLP service carries no production copy of the engine's profile.

A missing or invalid bundled configuration, empty selection, missing component
resource, or recorded load error prevents API initialization. Startup logs the
engine resource URL and each configured library as it loads. Strict startup
continues to check every result before passing the session to the API service.

For a Maven launch with the same 4 GiB heap as `clojure -J-Xmx4g -M:repl`, use:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.5"
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Xmx4g'
```

For IntelliJ, the heap belongs in the run configuration's VM options. A setting
such as `-Xmx8G` already exceeds 4 GiB. Taxonomy diagnostics and refused expressions
are distinct from a JVM out-of-memory failure; increasing the heap does not
change the component declarations or command parsing.

An optional `km.knowledge-base` (`KM_KNOWLEDGE_BASE`) file loads after all configured entries.
The API service receives that initialized inference engine session and KB.
Calls are serialized. Successful assertions persist across subsequent calls;
queries also retain the engine's inferred state. Failed evaluations leave the
previous knowledge-base value in place. Restarting restores the configured
libraries and optional startup file rather than persisting runtime assertions.

`/km/query` and `/km/assertions` provide the same KM evaluator under names that
express caller intent. They accept trusted KM text, including the engine's file
commands; `/km/query` is not a read-only sandbox. Discarding a failed
evaluation's KB does not undo filesystem effects from KM commands.

`POST /km/baseline` evaluates its expressions in order, each against the session
produced by the previous expression, starting from the startup session. That
session is the configured component libraries plus the optional startup file.
The call does not read or replace the session kept by `/km/query` and
`/km/assertions`. Derived frames are discarded, including when a later
expression records an error. A successful body is `{"ok": true, "results": [...]}`.
Each result has the same fields as a successful query. A blank list, a blank
expression, a read failure, or a reasoning error uses the same error status and
`{"error":"<message>"}` body as the other two routes.

Missing or blank expressions, malformed JSON, read failures, and reasoning
errors return 400. A not-ported expression returns 501. An unexpected engine
failure returns 500 with a stable message. Error responses use
`{"error":"<message>"}`. Successful empty answers remain HTTP 200.
