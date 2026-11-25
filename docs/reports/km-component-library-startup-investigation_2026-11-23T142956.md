# KM component-library startup failure: investigation and agent handoff

Filed: 2026-11-23, America/New_York. Investigation and measurements below were completed before filing.

Primary target: `E:/IdeaProjects/som-knowledge-machine`.

Consumer: `E:/IdeaProjects/som-nlp-service`.

Purpose: give the receiving agent enough evidence to reproduce, distinguish, and repair the component-library problems that prevent the NLP service from exposing its KM query and assertion endpoints under strict startup validation.

This is an incident and investigation report, not a completed repair of the inference engine or the imported ontology. It does not authorize suppressing errors, removing ontology links arbitrarily, enabling embedded Lisp, or changing the user's startup policy. The user explicitly chose **strict error-free startup**.

All unqualified repository paths in this report refer to `som-knowledge-machine`. Paths prefixed with `som-nlp-service/` refer to the sibling consumer repository. Line numbers are navigation aids captured during investigation; recheck them after concurrent edits. Both repositories have had changes during this conversation.

## 1. Findings at a glance

1. The current startup failure is caused by recorded KM load errors. The generic Spring `UnsatisfiedDependencyException` is a consequence of the KB initializer refusing to publish that result.
2. With the same installed `0.1.1-SNAPSHOT` runtime and the same component directories, both direct `som.kb/load-lib` and command-based `som.km.engine/evaluate` of `(load-lib ...)` produce the **same diagnostic messages with the same occurrence counts**. Their diagnostic ordering differs.
3. The reported `Ionic-Compound-Substance` cycle can be constructed directly from superclass declarations in different shipped chemistry variants. It is not evidence that Spring itself invented a taxonomy edge.
4. The supplied `Version` frame declaration is misrouted to the `version` command handling. This happens in plain Clojure through both load paths. The refusal does not establish that the component file contains executable Common Lisp.
5. The prompt retains a KB with recorded errors and normally prints a general reasoning warning, rather than every detailed diagnostic. Returning to `KM>` therefore does not establish an error-free load.
6. The SOM component library contributes **zero load errors** in the measured full runs, after the KM library has loaded with errors. This does not certify the combined KB as valid or every future SOM query as safe.
7. The user launches the external prompt with `clojure -J-Xmx4g -M:repl` and runs `(load-lib)` at `KM>`. The inspected IntelliJ NLP run configuration already uses `-Xmx8G`. No out-of-memory exception was present in the supplied failure. A larger heap does not resolve contradictory superclass declarations or command-name parsing.
8. All **92 consumer service tests pass** against the current installed runtime and the small test component libraries. That is separate from, and does not override, failure to load the full production library without errors.

## 2. User requirements and constraints that must be retained

The user requested HTTP endpoints in the NLP service for querying the KM engine and asserting to its knowledge base. The endpoints now exist:

| Endpoint | Example request body |
| --- | --- |
| `POST /km/assertions` | `{"expression":"(Fred has (color (red)))"}` |
| `POST /km/query` | `{"expression":"(the color of Fred)"}` |

The user subsequently required startup to finish loading both the KM component library and the SOM component library before queries or assertions are permitted. The initialized inference engine and its KB must be the ones exposed to the API service.

When asked whether Spring should accept the prompt's behavior of retaining a KB after a load completes with recorded errors, the user answered:

> Keep strict error-free startup

Accordingly, do not make the service start by merely changing `!result.errors().isEmpty()` into a warning. Do not expose an empty vocabulary, test-fixture KB, partially loaded KB, or a different permissive session as an API fallback.

Earlier, the user chose using a **newer published artifact** rather than switching the service to a locally installed replacement. A temporary locally built JAR was used for packaging verification during the investigation; that is not evidence that the corrected consumer dependency was published or deployed. The current measured consumer dependency is `org.springy.som:som-knowledge-machine:0.1.1-SNAPSHOT`.

The producer's `AGENTS.md` remains authoritative for implementation work. In particular, use the Lisp 2.5.45 source as the behavioral specification, preserve case, keep state explicit, preserve one implementation of behavior, and leave the license and copyright intact. A diagnostic report is not a reason to introduce a second interpreter in Java.

## 3. Environment and inputs used for the controlled comparison

| Item | Value |
| --- | --- |
| OS/shell | Windows / PowerShell |
| JDK | `C:/Program Files/Java/jdk-17.0.5` |
| Clojure dependency | `1.12.6` |
| Consumer runtime dependency | `org.springy.som:som-knowledge-machine:0.1.1-SNAPSHOT` |
| Installed runtime JAR | `C:/Users/scott/.m2/repository/org/springy/som/som-knowledge-machine/0.1.1-SNAPSHOT/som-knowledge-machine-0.1.1-SNAPSHOT.jar` |
| Runtime JAR SHA-256 at filing | `6AD4EA06A2CF55C873663F371E1D4C29DF7599D000BB6DD5FEF7D6168A614F9D` |
| KM component directory | `E:/IdeaProjects/som-knowledge-machine/src/resources/km-component-library/rkf-clib/components` |
| SOM component directory | `E:/IdeaProjects/som-knowledge-machine/src/resources/som-component-library` |
| Library order | KM first, SOM second, carrying returned `:kb` and `:query` forward |
| Initial state per comparison run | Fresh `som.km.engine/session` |

The runtime resource URL printed by both comparison processes was:

```text
jar:file:/C:/Users/scott/.m2/repository/org/springy/som/som-knowledge-machine/0.1.1-SNAPSHOT/som-knowledge-machine-0.1.1-SNAPSHOT.jar!/som/km/engine.clj
```

The packaged text of `som/kb.clj`, `som/km/engine.clj`, `som/km/interpreter.clj`, `som/km/taxonomy.clj`, and `som/km/loadkb.clj` was compared with the corresponding producer source, normalizing CRLF to LF. All five matched at the time of the controlled comparison. This was a check of those files, not a hash comparison of every namespace or every ontology file.

The comparison ran plain `clojure.main` in separate JVMs using the consumer dependency classpath. It did not create a Spring application context. Spring dependencies being present on that classpath do not make the reproduction a Spring-managed load.

The full comparison used explicit absolute library paths and `:silent true` for the direct API / `:silent t` for the command. The user's interactive invocation uses the no-path `(load-lib)` default and normal output. The inspected loader uses the same shipped component directory in a source checkout; silence controls load text, not a different ontology selection. The controlled experiment did not capture the user's exact interactive session or prove that every independently installed checkout uses the same default directory. Recheck directory resolution if that remains in doubt.

## 4. Full-library comparison results

| Measurement | Direct library API | `(load-lib ...)` through evaluator |
| --- | ---: | ---: |
| `.km` files processed across both libraries | 4,924 | 4,924 |
| KM component names returned | 4,915 | 4,915 |
| KM error occurrences | 86,543 | 86,543 |
| SOM error occurrences | 0 | 0 |
| Distinct taxonomy-cycle diagnostic strings | 414 | 414 |

The frequency maps of all KM error strings were equal. The ordered vectors were not equal. The SOM error vectors were equal and empty.

Do not report this as byte-identical entire results or KB equality: the comparison recorded diagnostics, file counts, selected metadata, and runtime origin, not equality of the complete KB/query values. The evidence establishes diagnostic equivalence for this corpus and runtime. It does not prove that all internal counters, bindings, timestamps, or every intermediate state are identical.

The 86,543 occurrences break down as follows. Classification here was by the indicated message substring, not by a deeper semantic categorization of every error:

| Message substring/category | Occurrences |
| --- | ---: |
| `cycle in the taxonomy` | 86,535 |
| `A pair should have exactly two elements` | 2 |
| `should evaluate to an instance!` | 5 |
| `Embedded Lisp is refused` | 1 |
| Other messages under these substring rules | 0 |

There were **420 distinct complete error strings**. The 414 distinct cycle strings are not necessarily 414 independent graph cycles: the same underlying cycle can be reported during different traversals, with different prefixes or enclosing paths. Likewise, the total occurrence count is not a count of independent defects or failed files.

A representative cycle appeared in both full runs:

```text
ERROR! You have a cycle in the taxonomy (not allowed)!
(Ionic-Compound-Substance -> Strong-Electrolyte -> Electrolyte -> Ionic-Compound-Substance)
```

The exact supplied `Version` refusal also appeared in both:

```text
Embedded Lisp is refused: (Version has (superclasses (KM-Class)) (description ("Tue Oct 12 16:54:46 CDT 2010"))).
```

Representative additional errors, which must not be overlooked after fixing the cycle or `Version`, include:

```text
ERROR! (:pair 5.6E-10): A pair should have exactly two elements!
```

and a failed expression reading `has-basic-structural-unit` through the `Chemical input` of `_Quantity-Value13`, where an instance was expected and the expression evaluated to nil. Investigate these separately rather than assuming the first two fixes guarantee a clean corpus.

## 5. Confirmed source of the named taxonomy cycle

The full KM directory recursively includes several chemistry variants. Their declarations do not all describe a compatible single hierarchy when loaded together.

The three edges needed for the reported closed walk are literal declarations in the input files:

| Child | Superclass | File and observed line |
| --- | --- | --- |
| `Ionic-Compound-Substance` | `Strong-Electrolyte` | `src/resources/km-component-library/rkf-clib/components/science/chem-halo/Ionic-Compound-Substance.km:5-6` |
| `Strong-Electrolyte` | `Electrolyte` | `src/resources/km-component-library/rkf-clib/components/science/chem-halo/Strong-Electrolyte.km:5-6` |
| `Electrolyte` | `Ionic-Compound-Substance` | `src/resources/km-component-library/rkf-clib/components/science/chemistry/Electrolyte.km:5-6` |

The last edge is also declared in `science/SME-chem/Electrolyte.km:11`.

Relevant excerpts:

```lisp
;; science/chem-halo/Ionic-Compound-Substance.km
(Ionic-Compound-Substance has
  (superclasses (Strong-Electrolyte))
  (test-case ()))

;; science/chem-halo/Strong-Electrolyte.km
(Strong-Electrolyte has
  (superclasses (Electrolyte))
  (test-case ()))

;; science/chemistry/Electrolyte.km
(Electrolyte has
  (superclasses (Ionic-Compound-Substance)))
```

The variants also disagree in other relevant superclass declarations:

- `science/chem-halo/Electrolyte.km` declares `Electrolyte` under `Chemical`.
- `science/chemistry/Ionic-Compound-Substance.km` declares `Ionic-Compound-Substance` under `Chemical`.
- `science/SME-chem/Ionic-Compound-Substance.km` declares it under `Chemical` as well.

Loading the root does not select one of these variants. `som.kb/traverse-directory` visits nested files and `load-into` loads every visited lowercase `.km` file. Class assertions from different variants then coexist in the same KB. The minimal reproduction below demonstrates that those three edges are sufficient for the reported cycle.

This particular finding is grounded in the corpus. Do not change the taxonomy walker to accept the cycle, drop cycle diagnostics, or discard an arbitrary edge merely to pass startup. Determine which library profile or mutually compatible component set is intended. If the producer is supposed to support loading the entire distributed root, define how conflicting variants are handled and validate that choice against the Lisp specification and component-library provenance.

The investigation does not establish which chemistry variant should be canonical. That remains a domain/input-selection decision, not an implementation fact established by this report. Other cycle strings also need review; fixing this named loop alone is not a proof that every cycle is gone.

## 6. `Version` declaration versus `version` command

The input file is `src/resources/km-component-library/rkf-clib/components/core/Version.km`:

```lisp
(Version has
   (superclasses (KM-Class))
   (description ("Tue Oct 12 16:54:46 CDT 2010"))
)
```

This is structurally a frame assertion. The runtime reports it as refused embedded Lisp because of the command dispatch path:

1. `src/som/km/interpreter.clj`, `head-name` around line 152, lowercases the list's head symbol.
2. `command` around line 493 dispatches embedded-Lisp/command names before the ordinary goal/handler-table path.
3. The `lisp-exprs` set includes `version`.
4. `lisp-command` around line 455 has a `version` branch that accepts the singleton `(version)` form and calls `embedded-refusal` for extra elements.
5. The head `Version` is therefore treated as `version`, and `has`, `superclasses`, and `description` are treated as unexpected command arguments.

A tiny library containing only that declaration reproduces the same refusal through both direct `som.kb/load-lib` and command-based `(load-lib ...)`. Spring is not needed.

A likely correction belongs in case-aware command recognition or the precedence/shape of frame-assertion dispatch. Validate it against the original Lisp rather than implementing a Java exception for this one filename. The original `src/knowledge-machine/packages/interpreter.lisp` defines `*km-lisp-exprs*`, constructs `*downcase-km-lisp-exprs*`, and uses symbol membership to recognize those commands. The Clojure port's unconditional lowercase-name matching is broader than exact membership in those sets for a mixed-case concept name. That source comparison strongly indicates the cause; no original Common Lisp runtime was executed during this investigation to verify the final intended fix.

Regression expectations for a reviewed fix should include:

- A mixed-case `Version` frame can be asserted and its `description` and `superclasses` read as domain data.
- `(version)` continues to return the KM banner/result already covered by the port.
- Command case handling follows the Lisp specification, including whichever uppercase forms it accepts.
- Actual unsupported embedded Lisp, such as an `eval` form, remains refused.
- Other frame names that resemble command names are considered; avoid a one-off special case that leaves the general collision unresolved.

Do not enable arbitrary Common Lisp evaluation to make this frame load.

## 7. Why the prompt appears to work while Spring refuses startup

### 7.1 Prompt behavior

The user starts the external prompt with:

```powershell
clojure -J-Xmx4g -M:repl
```

and enters:

```text
KM> (load-lib)
```

`src/som/km/repl.clj` makes two relevant choices:

- `evaluate-in` around line 87 retains returned `:kb` and `:query` whenever a result has a KB; it does not require an empty `:errors` vector.
- `reply-text` around line 67 prints the normal result and, around line 82, adds `; (WARNING: Errors occurred during reasoning)` when `:errors` is nonempty. It does not print the entire diagnostic vector in the normal prompt reply.

The separate `som.km.startup` entry point is not the command the user reported using, but its behavior is consistent with the distinction: `load-expression` retains returned state, and `load-summary` prints an error count without turning that count into a startup exception.

Consequently, a prompt that returns after a completed load can hold a KB that recorded the very same cycle messages. The user's observation that startup did not stop, or that the detailed cycle was not displayed, does not by itself demonstrate that no diagnostic was recorded. The controlled comparison inspected the actual `:errors` data.

### 7.2 Spring behavior

`som-nlp-service/src/main/java/org/springy/som/nlp/km/internal/KmKnowledgeBase.java`:

- Loads the KM directory first and the SOM directory second in its constructor.
- Carries the first returned session into the second load.
- At `loadLibrary`, around lines 48-52, rejects a result when it is not OK, contains any errors, or has a missing/`nil` answer.
- Only assigns the final `startupSession` after the required loads and any optional startup file finish successfully.

`KmEngineService` receives that initialized session. The controller depends on the service. A thrown KB-initialization exception therefore prevents the API beans from becoming usable.

The expected exception chain is:

```text
kmController
  -> kmEngineService
    -> kmKnowledgeBase constructor
      -> IllegalStateException: Could not load KM component library ...
         <recorded engine diagnostics>
```

This is strict startup doing its job. The top-level dependency exception is not evidence of a missing Spring annotation, an injection cycle, or a need to mock the production engine.

The optional `km.knowledge-base` / `KM_KNOWLEDGE_BASE` file is loaded only after both component libraries. The failure under investigation occurs in the first component-library load, before that optional file can repair or alter anything.

### 7.3 Engine result semantics

`som.km.engine/present` documents that `:ok` is false for `not-ported`, while a syntax error can remain `:ok true` with entries in `:errors`.

`som.km.loadkb/load-one` can turn an inner not-ported result into a recorded load error, remove the inner refusal envelope, and continue under the query's default `:on-error :continue` policy. `som.kb/load-lib` can therefore return a list of loaded component names despite recorded failures within individual expressions.

Do not test load success solely by checking `:ok`, a non-nil list of component names, or the fact that `(load-lib)` returned. The service's separate error-vector check is necessary for the user's strict policy.

The raw direct loader does not use the evaluator's full `:ok` envelope. `KmEngineBridge.loadLibrary` adds an OK flag based on the raw `:error` field, preserves the returned errors, and leaves strict validation to `KmKnowledgeBase`. That normalization is not intended to certify an error-free load.

## 8. Direct versus command entry points and diagnostic ordering

Current Spring path:

```text
KmKnowledgeBase
  -> KmEngineBridge.loadLibrary(currentSession, directory)
    -> som.kb/load-lib(kb, query, directory, :silent true)
      -> load-into
        -> load-component
          -> som.km.loadkb/load-file
            -> som.km.interpreter/km-int for expressions in the file
```

Command path:

```text
engine/evaluate("(load-lib ... :silent t)", session)
  -> interpreter/interpret
    -> interpreter/km-int
      -> command
        -> som.kb/load-command
          -> load-into
            -> load-component
              -> load-file
                -> km-int for expressions in the file
```

The paths share the actual component walk and evaluator. The command route also has an outer top-level evaluation.

`src/som/km/taxonomy.clj` defines stack-scoped `*cycle-errors*` and `attach-cycle-errors` around lines 38-74. `interpreter/km-int`, around lines 4998-5022, creates that collection at its outermost call and drains it when the outer call returns. A direct library call and a command that wraps the whole library walk can therefore attach cycle diagnostics at different boundaries.

That is a plausible source-grounded explanation for the observed ordering difference. The experiment measured equal diagnostic frequency maps; it did not instrument every attachment event or establish equality of every internal KB field. Tests comparing these paths should compare the diagnostics appropriate to the contract rather than assuming chronological error-vector equality.

The loader also resets inference state at each `load-file`. Read `loadkb/reset-for-load` before changing state initialization. Do not infer that a direct FFI call is automatically state-equivalent in all cases merely because this corpus produced matching diagnostic frequencies.

## 9. Minimal reproducible cases

These fixtures write only under `target/`. They do not require a Spring context or modification of the shipped component files. Run from the producer root with the current Clojure CLI. If using a published JAR instead, ensure the source checkout is not also on the classpath and print the actual engine resource URL.

### 9.1 `Version` refusal through both paths

Save the following as `target/reproduce-version-load.clj`:

```clojure
(require '[som.km.engine :as engine]
         '[som.kb :as kb])

(let [dir "target/version-load-fixture"]
  (.mkdirs (java.io.File. dir))
  (spit (str dir "/Version.km")
        "(Version has (superclasses (KM-Class)) (description (\"Tue Oct 12 16:54:46 CDT 2010\")))")
  (doseq [mode [:direct :command]]
    (let [session (engine/session)
          result (if (= mode :direct)
                   (kb/load-lib (:kb session) (:query session) dir :silent true)
                   (engine/evaluate
                     (str "(load-lib " (pr-str dir) " :silent t)")
                     session))]
      (println mode (pr-str (:errors result))))))
```

Run:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.5"
clojure -J-Xmx4g -M target/reproduce-version-load.clj
```

Observed before any dispatch repair: both modes return the supplied `Embedded Lisp is refused: (Version has ...)` message.

### 9.2 The three superclass links reproduce the cycle

Save as `target/reproduce-chemistry-cycle.clj`:

```clojure
(require '[som.km.engine :as engine]
         '[som.kb :as kb])

(let [dir "target/chemistry-cycle-fixture"]
  (.mkdirs (java.io.File. dir))
  (spit (str dir "/A.km")
        "(Ionic-Compound-Substance has (superclasses (Strong-Electrolyte)))")
  (spit (str dir "/B.km")
        "(Strong-Electrolyte has (superclasses (Electrolyte)))")
  (spit (str dir "/C.km")
        "(Electrolyte has (superclasses (Ionic-Compound-Substance)))")
  (doseq [mode [:direct :command]]
    (let [session (engine/session)
          loaded (if (= mode :direct)
                   (kb/load-lib (:kb session) (:query session) dir :silent true)
                   (engine/evaluate
                     (str "(load-lib " (pr-str dir) " :silent t)")
                     session))
          queried (engine/evaluate
                    "(the all-superclasses of Ionic-Compound-Substance)"
                    (select-keys loaded [:kb :query]))]
      (println mode
               "load error count:" (count (:errors loaded))
               "query errors:" (pr-str (:errors queried))))))
```

Run with the same Java setup:

```powershell
clojure -J-Xmx4g -M target/reproduce-chemistry-cycle.clj
```

Observed result for both modes:

```text
load error count: 0
query errors:
ERROR! You have a cycle in the taxonomy (not allowed)!
(Strong-Electrolyte -> Electrolyte -> Ionic-Compound-Substance -> Strong-Electrolyte)
```

The reporting rotation of a closed walk is not a different cycle. This fixture also shows that a cycle can remain undiagnosed until a later traversal; an empty error vector while asserting these three facts does not establish a globally acyclic KB.

### 9.3 Full comparison harness

Save the following as `target/compare-library-loads.clj`. It retains each library's error vector separately because the next top-level load can initialize a new diagnostic collection. It runs both loads even if the first records errors **for investigation only**; this is not the production strict-startup policy.

```clojure
(require '[som.km.engine :as engine]
         '[som.kb :as kb]
         '[som.km.loadkb :as loadkb]
         '[som.km.utils :as utils]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(let [[mode] *command-line-args*
      _ (assert (contains? #{"direct" "command"} mode))
      core-dir "src/resources/km-component-library/rkf-clib/components"
      som-dir "src/resources/som-component-library"
      original-load-file loadkb/load-file
      files (atom 0)
      run-load (fn [state directory]
                 (if (= mode "direct")
                   (kb/load-lib (:kb state) (:query state)
                                directory :silent true)
                   (engine/evaluate
                     (str "(load-lib " (pr-str directory) " :silent t)")
                     (select-keys state [:kb :query]))))]
  (println "Engine resource:" (str (io/resource "som/km/engine.clj")))
  (flush)
  (with-redefs [loadkb/load-file
                (fn [& args]
                  (let [result (apply original-load-file args)
                        n (swap! files inc)]
                    (when (zero? (mod n 500))
                      (println "Processed" n "files; last:" (second args))
                      (flush))
                    result))]
    (let [core (run-load (engine/session) core-dir)
          core-errors (vec (:errors core))
          som (run-load core som-dir)
          all-errors (concat core-errors (:errors som))
          report {:mode mode
                  :runtime (str (io/resource "som/km/engine.clj"))
                  :files @files
                  :core-component-count
                  (count (utils/elements (:value core)))
                  :core-errors core-errors
                  :som-errors (vec (:errors som))
                  :cycle-errors
                  (vec (distinct
                         (filter #(str/includes? % "cycle in the taxonomy")
                                 all-errors)))
                  :version-errors
                  (vec (distinct
                         (filter #(str/includes? % "(Version has")
                                 all-errors)))}]
      (spit (str "target/load-" mode ".edn") (pr-str report))
      (println "Finished" mode
               "core errors:" (count core-errors)
               "SOM errors:" (count (:errors som)))
      (flush))))
```

Run the modes in separate JVMs to avoid shared process state:

```powershell
clojure -J-Xmx4g -M target/compare-library-loads.clj direct
clojure -J-Xmx4g -M target/compare-library-loads.clj command
```

Then compare the parsed EDN results using:

```clojure
(require '[clojure.edn :as edn])
(let [direct (edn/read-string (slurp "target/load-direct.edn"))
      command (edn/read-string (slurp "target/load-command.edn"))]
  {:core-frequency-equality
   (= (frequencies (:core-errors direct))
      (frequencies (:core-errors command)))
   :ordered-core-equality
   (= (:core-errors direct) (:core-errors command))
   :som-equality
   (= (:som-errors direct) (:som-errors command))})
```

Measured result: frequency equality `true`, ordered core equality `false`, SOM equality `true`.

These loads are expensive. The investigation printed progress every 500 files; the full runs took several minutes. Do not mistake a long CPU-active load for a deadlock or repeatedly start extra JVMs without checking the existing runs. The temporary progress wrapper forwards the original arguments and result; it does not select different components or change loader error policy.

## 10. Heap and runtime launch details

The user identified `-J-Xmx4g` on the external Clojure launch. The inspected IntelliJ `NlpServiceApplication` run configuration has `VM_PARAMETERS` set to `-Xmx8G` in the consumer's ignored `.idea/workspace.xml`.

No heap reduction was made. No `OutOfMemoryError` was present in the supplied startup trace or in the controlled reproductions. The comparisons were not a controlled heap-size experiment, and no numerical claim about minimum required memory is established here.

For reproducing a 4 GiB Maven-launched service, the consumer documentation now gives:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17.0.5"
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Xmx4g'
```

Heap tuning can still matter for load duration and resource usage. It should be investigated separately if an actual memory failure appears. It is not a substitute for resolving the measured diagnostic data.

## 11. Earlier failures that are distinct from the current corpus failure

Several previous startup failures occurred before a valid loader/runtime combination was available. Avoid treating their old logs as the current root cause.

### 11.1 Stale IntelliJ runtime

An earlier user log explicitly reported loading:

```text
som-knowledge-machine-0.1.0-20261114.003708-1.jar!/som/km/engine.clj
```

That older runtime did not implement the expected `(load-lib)` path. An old snapshot could report the expression as invalid KM syntax. Rebuilding NLP did not itself refresh IntelliJ's selected dependency.

### 11.2 Missing loader in rebuilt producer JARs

Published `0.1.0` snapshot builds `20261123.024211-4` and `20261123.024759-5` were examined during the investigation. Their JARs omitted `som/kb.clj`, while the newer interpreter required `som.kb`. The result was a `FileNotFoundException` for `som/kb__init.class`, `som/kb.clj`, or `som/kb.cljc`.

The producer packaging rule in `build.clj` was corrected from copying `src/som/km` into `som/km` to copying `src/som` into `som`. This includes both `som/kb.clj` and the `som/km` namespaces. Packaging notes in `docs/migration/21-maven-library.md` and the JVM-library section of `docs/reference/Architecture.md` were updated. A locally built corrected JAR was checked outside the producer source classpath.

A local packaging verification, an installation into a developer Maven cache, and publication to Nexus are different actions. This report does not claim that the investigation published a corrected snapshot. The current comparison uses the installed `0.1.1-SNAPSHOT`, whose relevant source files match the producer checkout.

### 11.3 Consumer initialization changes already made

`KmEngineBridge` now logs the engine resource URL during initialization and requires the `som.kb` loader. Initialization invokes the direct `som.kb/load-lib` API rather than constructing a KM command string. The direct API avoids a command-parser dependency for startup and returns the same loader's state/diagnostics. These changes do not remove the strict readiness gate.

The current corpus errors are therefore a later problem than the stale-runtime and missing-resource problems. Clearing Maven caches or repeatedly rebuilding the same ontology cannot resolve the literal conflicting class declarations.

## 12. Verification record and its limits

| Verification | Result | Limit |
| --- | --- | --- |
| Plain-Clojure `Version` fixture, direct and command modes | Same refusal in both | Diagnoses dispatch behavior; not a repaired case |
| Plain-Clojure three-edge cycle fixture, direct and command modes | Same taxonomy cycle on superclass traversal | Does not identify every invalid relationship in the full corpus |
| Full installed-runtime comparison, KM then SOM | Equal diagnostic frequency maps; different ordering; KM 86,543 errors; SOM 0 | Does not prove equality of the complete KB or every query result |
| Consumer `mvnw.cmd -o test` against installed `0.1.1-SNAPSHOT` | 92 tests, 0 failures, 0 errors, 0 skipped | Tests use small fixture component directories, not the full production root |
| Real-engine REST assertion then query test | Passes and returns the asserted value | Demonstrates wiring/state retention with valid fixture inputs |
| Historical producer `clojure -M:test` run during the earlier packaging investigation | 366 tests, 3,110 assertions, 1 failure, 0 errors | Historical baseline, not a fresh producer run for this report |

The historical producer failure was `load-lib-loads-the-shipped-component-library` in `test/som/kb_test.clj`, reported at line 294 at that time. Its assertion expected all nonblank load text lines to start with `Loading of `, but actual text included taxonomy cleanup/tree output such as `Removing redundant superclasses...` and `Computing subclasses of Thing...`. That failure should be assessed on its own terms. Do not weaken it just to claim a green suite, and do not confuse it with an assertion that the full corpus has zero engine errors.

No new full producer test pass is claimed. No standalone Common Lisp parity execution was performed. No chemistry variant was removed or chosen as canonical. No cycle detection was disabled. No permissive production startup mode was introduced.

## 13. Evidence artifacts on this machine

These are in the consumer's gitignored `target/` and may disappear on `mvn clean`. This report includes the essential results and reproduction code so the receiving agent does not depend on them surviving.

| Artifact under `E:/IdeaProjects/som-nlp-service/target/` | Contents |
| --- | --- |
| `km-load-comparison-summary.edn` | Compact counts, runtime URL, frequency equality, ordering difference, representative cycle, and Version refusal |
| `km-load-direct-report.edn` | Direct-run diagnostics and measured metadata; approximately 14 MB |
| `km-load-command-report.edn` | Command-run diagnostics and measured metadata; approximately 14 MB |
| `km-load-direct-compare.log` | Runtime origin, progress every 500 files, final direct result summary |
| `km-load-command-compare.log` | Runtime origin, progress every 500 files, final command result summary |
| `km-load-compare.clj` | Actual controlled-comparison script using explicit absolute paths |
| `km-version-compare.clj` | Small Version reproduction |
| `km-cycle-compare.clj` | Small cycle reproduction |
| `km-load-summary.clj` | Comparison of saved reports |
| `km-error-categories.clj` | Diagnostic substring/category counts |
| `km-current-runtime-tests.log` | Passing 92-test consumer run, finished 2026-11-23 at 14:22:14 America/New_York |
| `km-runtime-classpath.txt` | Classpath used for the installed-runtime comparison |

The consumer's permanent HTTP/state/readiness documentation is `docs/km-api.md`. Its KM code and tests are under `src/main/java/org/springy/som/nlp/km/internal` and `src/test/java/org/springy/som/nlp/km/internal`.

## 14. Recommended work for the receiving agent

### 14.1 Establish the baseline before changing behavior

Read the producer's current `AGENTS.md`, `docs/reference/Architecture.md`, and migration index. Recheck the current dependency version, engine resource URL, component directories, and source/JAR consistency. Do not assume a previous `0.1.0` timestamped build is the one being used.

Run the small reproductions first. Capture the current full-library diagnostic baseline if files have changed. Keep KM and SOM diagnostics separate. Prefer counts and deduplicated examples for routine output; the current failure can otherwise produce an enormous exception message that hides the useful first cause.

### 14.2 Correct the command/frame collision with specification-backed tests

Trace how the Lisp distinguishes mixed-case domain symbols from embedded commands. Repair the port's recognition rules or dispatch precedence at the appropriate shared interpreter boundary. Test ordinary frame assertions, the actual `(version)` command, and genuine refused Lisp forms. Keep prompt, library, and HTTP evaluation on the same behavior.

Do not solve the issue by renaming every imported `Version` frame, skipping `Version.km`, allowing arbitrary embedded Lisp, or hard-coding a filename exception in the Java service.

### 14.3 Resolve component-profile compatibility

Determine whether recursively loading every science variant is the intended supported library configuration. The current default loads conflicting declarations from `chemistry`, `chem-halo`, and `SME-chem` together.

Choose and document an authoritative compatible profile, or fix a specification-backed merge/load issue if one is established. Preserve imported source provenance and explain any intentional selection or migration. A decision to omit a variant changes the loaded knowledge base and must be explicit; it is not merely a performance optimization.

Do not delete one side of a cycle or disable taxonomy checks without establishing the intended ontology semantics. Re-run the complete selected profile and account for all remaining cycle diagnostics, not only the example in this report.

### 14.4 Address the remaining non-cycle failures

After the profile/dispatch work, reproduce the malformed pair and nil-instance errors in isolation. Establish whether each is an imported-format incompatibility, a reader/evaluator defect, a missing dependency between components, or a consequence of the conflicting profile. The investigation has not determined those causes.

### 14.5 Validate and deliver a usable artifact

Run the producer's required tests. Inspect the packaged JAR for `som/kb.clj`. From outside the producer source classpath, require the engine, load both selected component libraries, and verify that each has an empty error vector.

Validate the consumer with that artifact using its real engine tests. A small-fixture test pass alone is insufficient acceptance evidence for the full production profile. Any published artifact must contain the corrected runtime sources; source-checkout success alone cannot establish that.

Publishing a snapshot, installing a local artifact, changing the consumer version, and refreshing an IDE are separate deployment steps. Preserve the user's preference for a published dependency. This report requests no automatic publication or communication to other users.

## 15. Acceptance criteria for closing this incident

- The chosen KM component profile and the SOM component library both complete loading with **zero recorded errors** under the producer and the consumer's supported entry points.
- The supported profile is documented. Its superclass graph contains no unintended cycles, including the named Ionic-Compound-Substance/Strong-Electrolyte/Electrolyte loop.
- The `Version` frame is processed as intended by the KM specification; supported version commands continue to work; unsupported embedded Lisp remains refused.
- The pair-arity and expected-instance diagnostics have been resolved or excluded through a deliberate, documented supported-profile decision, not hidden by logging or severity changes.
- Strict Spring startup is preserved. Neither endpoint can use a session before both required component libraries have passed validation. Failure of either library prevents publication of the API session.
- Tests cover a failed first load, failed second load, initialization order, state carried between loads, and successful assertion/query against the resulting real engine session.
- The runtime JAR's contents and provenance are verified outside the source checkout, and the consumer actually resolves the corrected published artifact.
- Producer and consumer test results are reported accurately, including any remaining baseline failures.

At filing, the incident remains open: service wiring works with valid fixtures, but the complete currently selected KM corpus is not error-free and strict production startup correctly refuses it.
