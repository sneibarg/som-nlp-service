# Goal reasoning API alignment proposal

Target: `E:/IdeaProjects/som-knowledge-machine`. Filed 2026-11-22 at 21:07:29 America/New_York.

## Scope and evidence

This is a proposal, not an API implementation. The requested draft is actually `src/resources/som-component-library/README.md`. Read the target's complete `AGENTS.md`, all three skills it names, `docs/reference/Architecture.md`, the migration index, and the component architecture's README, vocabulary, and reasoning notes. Inspected the engine, HTTP and prompt boundaries, component loader, relevant component definitions, goal acceptance tests, HTTP tests, dependencies, and JAR build. This is a focused alignment review of the requested API and its delivery path, not a claim that every interpreter function or imported component has been audited.

The invoked skill is `som-nlp-service/.codex/skills/align-repository/SKILL.md`; it explicitly targets som-knowledge-machine. Its report destination is the repository containing that skill, which explains this report's location. The target's `.grok/skills/align-repository/SKILL.md` is stale: its target list excludes its own repository. The user's explicit request to update AGENTS.md takes precedence over the skill's report-only write restriction. No implementation, build, dependency, component, or test changes are part of this work.

## Recommended contract

Expose a versioned, structured adapter over the existing engine. Preserve `POST /query`, `GET /status`, the prompt, KM case, and existing result semantics. The game-specific routes below are additions; they do not change the legacy query language or turn the interpreter into a game controller.

The draft supports **one caller-supplied level goal and a directive for one event**. It does not generate a new long-term goal. A directive is an ordered set of catalog attempts, not a game command already executed. A met goal, a stale clock, no matching actionable hunt, and an explicit hold have different meanings. Preserve all four.

| Proposed endpoint | Purpose and state contract |
| --- | --- |
| `POST /v1/sessions` | Create an adapter-owned session for one player, loading the named `som-level-v1` profile from a configured library source. Return a session ID and profile version. The profile is a server-known name, not a client filesystem path. |
| `PUT /v1/sessions/{sessionId}/goals/{goalId}` | Assert or replace a complete level-goal input record. Return its new revision and normalized defaults. Require `expectedRevision` on updates; creation uses revision 0. |
| `GET /v1/sessions/{sessionId}/goals/{goalId}` | Return the stored goal inputs, revision, and latest evaluated status. This reads a stored result; it does not run inference. Before evaluation, status is `unevaluated`. |
| `PUT /v1/sessions/{sessionId}/goals/{goalId}/projections/{eventId}` | Assert one complete event projection and its ordered mobile readings against an explicit goal revision. Treat event IDs as immutable: identical retries return the same acknowledgement; different content under that ID is a conflict. No inference yet. |
| `POST /v1/sessions/{sessionId}/goals/{goalId}/evaluations` | Evaluate a stored `eventId`, or atomically assert an inline projection and evaluate it. Return the view, directive, carry-forward suggestions, goal revision, event ID, and profile version. This is the usual one-call game loop. |
| `POST /v1/sessions/{sessionId}/queries` | Query selected supported slots on a goal or an evaluated event view. Require goal revision and, for a view, event ID. Evaluate on an isolated reconstructed value. This cannot advance a player's clock or update their current goal. |
| `DELETE /v1/sessions/{sessionId}` | Release the session's stored inputs, snapshots, and retry records. Return 204; document that initial sessions are in memory and disappear on restart. |

Use JSON for these routes. The assertions are the typed goal and projection PUTs, plus the inline assertion in evaluations. A second arbitrary-triple assertion endpoint is unnecessary for this first client: it would let the caller overwrite conclusions and complicate validation. Keep unrestricted KM expression evaluation on the existing compatibility route. If general KM sessions are later needed, specify them separately rather than silently sharing the agent session with `/query`.

### Goal assertion

Example request to `PUT /v1/sessions/s1/goals/g1`:

```json
{
  "expectedRevision": 0,
  "kind": "level",
  "targetLevel": 5,
  "riskCeiling": "bold",
  "budgetMinutes": 10,
  "scoreEvery": 15
}
```

Map these to `Som-Level-Goal`: kind to `som-kind-in`, target to `som-target-level`, ceiling to `som-risk-ceiling`, budget to `som-budget-minutes`, and cadence to `som-score-every-in`. Omitted kind defaults to `level`, omitted budget becomes numeric 0, and omitted cadence becomes 15. Initially accept only `level`; do not promise behavior for other goal kinds just because a slot can store them. Require a positive integer target and cadence, one of the three documented ceiling words, and a finite nonnegative budget.

An accepted update changes the goal revision even when kind and target stay the same but budget, ceiling, or cadence changes. Reject an evaluation against an old revision. Clear the latest-result pointer on update. Do not carry cached conclusions forward under a revised goal. The caller must acknowledge the new revision and provide the new clock baseline; the server must not guess how much time or progress to retain after a policy change.

### Projection assertion and evaluation

An inline evaluation can carry the complete input record:

```json
{
  "eventId": "tick-42",
  "eventSequence": 42,
  "goalRevision": 1,
  "projection": {
    "event": "view",
    "clockKind": "level",
    "clockTarget": 5,
    "startLevel": 1,
    "startFraction": 0,
    "level": 1,
    "expToLevel": 1000,
    "expSpan": 1000,
    "elapsedMinutes": 0,
    "scoreCount": 1,
    "priorNotch": "cautious",
    "cooldownSteps": 0,
    "saves": 1,
    "scoreSteps": 0,
    "areasKnown": true,
    "untriedExits": [],
    "readings": []
  }
}
```

The remaining optional JSON field names map directly to the caller-owned slots in `docs/reference/som-component-architecture/vocabulary.md`: `expEstimate`, `scorePageExp`, `firstScore`, `scoreForced`, `metIn`, `deadlineIn`, `maxHit`, `hit`, `position`, `fighting`, `repopRoom`, `roomStale`, `gearGate`, and `fleeFloor`. Mobile entries carry `name`, optional `outlook`, `fresh`, and `player`, in caller order. Publish this entire mapping in the eventual API schema, including each field's type and conditions of use.

Require the counters and numeric operands that the documented rules read; validate finite numeric values and nonnegative counters before evaluation. A positive budget requires elapsed minutes. A known `maxHit` must be positive before division; missing hit data remains unknown. Allow an experience span of 0, which means unknown fraction. Define start fractions with exact ratio support. Validate events as `score`, `death`, or `view`, and notch and outlook words against the documented sets. Validate directions against the player integration's agreed vocabulary; that direction list is not specified by this repository yet.

Preserve absence. In particular, false or missing `areasKnown` maps to an absent `som-areas-known` slot, because that rule tests for *any value*. Map true flags to KM `t`; represent false by absence where the existing rules test presence or `= t`. Missing outlook means no consider yet; `unknown` means a consider with that outcome. Arrays become one KM `:seq`, including an empty array. Do not turn `readings` into multiple slot values. Reject caller-supplied conclusion fields such as pace, notch, licensedRule, and licensedDirective. Do not accept `opponentOutlook`: the draft explicitly leaves it empty and has no rules 5 or 6.

Construct KM terms through the term/reader APIs with explicit types. Do not interpolate unescaped names or strings into executable KM source. Keep symbol spelling and string-versus-symbol distinctions, especially the string category `information / communication`. Validate the entire request before constructing its trial state.

### Result and query shape

Use the engine's keyword-keyed domain result envelope: `:ok`, `:value`, `:errors`, `:error-structures`, `:warnings`, optional `:error` and `:message`, with `:kb` and `:query` retained internally when appropriate. The new domain operation may put a structured view map under `:value`; the HTTP layer serializes it rather than inventing an independent result protocol. Slot queries should reuse the existing term encoder for exact ratios, symbols, keywords, and ordered lists.

The view should expose fraction, levels earned, pace, notch, notch movement, next cooldown, met, deadline missed, score due, checkpoint due, stale clock, licensed rule, and directive. Preserve empty conclusions as null/unknown rather than deriving false or zero. A directive includes role, `allowSocial: false` translated from the rule's `no`, and an ordered array of attempts with `role`, `category`, `preference`, and `argument`. Preserve each attempt's own role; rule 12 has a travel attempt followed by a survey attempt.

Return `directive: null` for rules 1 and 14 and for a stale clock. Return a directive with `attempts: []` for a real hold. Include the licensed rule and stale indicator so the client can distinguish these cases. Expose a `carryForward` map of suggested next values, including the inferred notch and cooldown. Score counters and checkpoint acknowledgements remain caller-owned facts; a save directive is not evidence that a checkpoint succeeded. The server neither decrements cooldown nor advances elapsed time.

For slot queries, accept an explicit selection such as `{"goalId":"g1","goalRevision":1,"eventId":"tick-42","slots":["som-pace","som-notch","som-licensed-rule"]}`. Allow only published readable slots and the relevant goal/view target. Rebuild from that event's inputs and evaluate selected slots strictly, threading the returned `:kb` and `:query` between reads. Discard the scratch result afterward. Report errors from every evaluation step; the final step's empty error list must not hide an earlier failure.

Do not introduce `nextGoal` as an alias for a directive. When `som-met` is true, return the goal status and let the caller assert another goal through the PUT endpoint. A successor-goal policy needs separate requirements for the candidate set, priorities, prerequisites, and authority to replace the active goal. Those requirements are unknown here. Do not invent an automatic level increment, a general planner, or CLIB Goal/Event integration in this change.

## Alignment findings and proposed changes

### 1. Separate player sessions and keep each event's inference disposable

Files: `src/som/km/server.clj:115` (`evaluate-in`), `:198` (`start`); `src/resources/som-component-library/README.md:104`; `docs/reference/som-component-architecture/README.md`.

Standards: AGENTS.md Project Direction (caller-owned knowledge-base values and explicit concurrency), Coding Conventions (immutable state, strict traversal, effects at the edges), and the component README's fresh projection contract.

The existing shared, locked session is documented legacy behavior, not itself a standards defect. It becomes a mismatch if used directly for multiple players or consecutive goal projections. Add a registry owned by the HTTP adapter; each player session holds its own immutable inputs and last accepted result. Initialize an immutable loaded-library baseline once for a known profile. For each event, start from that baseline, assert the selected goal, new projection and readings, create a new view, and query the requested conclusions. Never use the preceding event's inferred KB as the next baseline. Ordinary slot reads may mutate inference state, so a route named query is not evidence of engine purity.

Serialize all operations on one session, including goal revisions, projection assertion, evaluation, duplicate detection, and result publication. Different sessions must have separate locks. Do not wrap effectful evaluation in a retrying `swap!` function or use a lazy map for updates. Use strict reductions to carry returned state through the assertion and query steps. Publish the stored inputs/result only after all steps succeed. With immutable scratch KBs a failed trial can be discarded; this does not promise rollback of arbitrary KM file I/O. Typed routes must not execute filesystem or trace commands.

### 2. Put orchestration in the library, preserve one rule implementation

Files: `src/som/km/engine.clj:79`; `src/som/km/repl.clj:100`; `src/som/km/server.clj:136`; the nine `.km` files in `src/resources/som-component-library`.

Standards: AGENTS.md Project Direction (one implementation), Coding Conventions (focused namespaces and domain result maps), Change Discipline (same behavior through prompt and HTTP).

Add a focused namespace such as `som.km.agent-goals` with plain functions for validating inputs, constructing terms, evaluating an event, and selecting slots. Functions accept the baseline/session values and return maps; HTTP retains sockets, registry mutation, and JSON translation. Rules for pace, notch, and first matching hunt remain in the components. Do not port them a second time into Clojure conditionals or the player client. Reuse `engine/evaluate` and the component loader rather than introducing a second interpreter. Add public docstrings describing returned values.

The prompt can already perform the underlying assertions and slot reads, but a new aggregate library operation also needs a documented prompt command through the same function. Keep session-management verbs as adapter concerns. Do not add protocols or records without an actual second implementation. No existing namespace move is required for this proposal; add call sites in the engine/prompt dispatcher and HTTP adapter, with an explicit dependency check to avoid an interpreter -> orchestration -> engine -> interpreter cycle. Resolve a thin command handler at dispatch time, as existing loader integration does, or pass the evaluator into the aggregate function; choose and document one approach during implementation.

### 3. Define revisions, retries, and failure semantics before accepting updates

Files: `src/som/km/server.clj:115-134`; `src/som/km/engine.clj:56-77`; `test/som/km/server_test.clj`.

Standards: AGENTS.md Project Direction (explicit state ownership), Coding Conventions (domain refusals are maps; adapters format them), and component README caller-owned clock semantics.

`engine/present` can return `:ok true` with `:errors`, and current HTTP commits any result carrying `:kb`. Preserve that legacy behavior. For the new aggregate operation, an engine error or refused step makes the aggregate an unsuccessful domain result and prevents publication; retain all diagnostics. Empty conclusions without errors are legitimate successful answers.

Require caller-assigned monotonic `eventSequence` within a session. Under the same lock, check retry identity before checking sequence ordering. The same event ID, revision, and normalized input return the saved response without applying a death or score again. Different payload under an existing ID, a stale revision, or a lower/new duplicate sequence is a 409 domain refusal. Read-only slot queries do not advance that sequence. Store profile version with retry records; a profile upgrade requires a new session rather than changing the baseline underneath existing retries. Evict retry records only under a documented session retention policy; forgotten events must not silently be replayed as new.

Proposed HTTP mappings: malformed JSON/unknown fields/invalid inputs 400, unknown session/goal/event 404, revision/order/identity conflict 409, unsupported content type 415, aggregate inference error 422, not-ported engine refusal 501, unexpected failure 500. A stale clock is a successful domain answer with no directive, not an HTTP failure. A goal revision conflict is an API refusal before inference. These are new endpoint policies, not changes to legacy `/query`, where syntax errors may remain HTTP 200. Return stable error codes/messages through the common result shape. Extend `exchange->request` if preconditions are transported as headers; it currently copies only Content-Type.

### 4. Package the loader and library so the API can run outside a checkout

Files: `build.clj:23`; `src/som/km/interpreter.clj:56`; `src/som/kb.clj:34-50,178`; `src/som/km/license.clj:24-51`; `docs/reference/Architecture.md` JVM library section.

Standards: AGENTS.md Project Direction (engine is a reusable library and license retained), Reference (architecture is authoritative), and Coding Conventions (documented behavior and effects at boundaries).

The build copies only `src/som/km`; the interpreter requires `som.kb`, which lives outside that directory. It also does not package the new component files. Include `src/som/kb.clj` and an explicit resource manifest for the nine game components. Add a loader path for manifest-listed classpath resources; the current loader expects real directories and cannot enumerate JAR URLs. Preserve its existing filesystem `(load-lib)` behavior and shipped-library default. Do not simply include resources and assume directory traversal will work inside a JAR. A manifest reader should yield source text into the same load/evaluation path.

The license resource is already packaged, but `license/source-license` and its fallback only inspect filesystem locations. Use the packaged full license resource for library delivery so `(license)` keeps its promised text outside the checkout. Keep the copyright and license unchanged. These are delivery gaps grounded in the inspected source, not observed packaged-runtime failures; this report did not build the artifact.

### 5. Make the API contract and integration boundary lasting documentation

Files: `src/resources/som-component-library/README.md:63-111`; `docs/reference/Architecture.md`; `docs/reference/som-component-architecture/{README,vocabulary,reasoning}.md`; `README.md:7`; `docs/migration/README.md`.

Standards: AGENTS.md Reference, Project Direction, and Change Discipline.

Add the endpoint and JSON schema contract under `docs/reference/`, link it from the component README, and document state ownership, term encoding, input replacement, retry lifetime, and the no-successor-policy limit. The example in the component README omits clock kind/target and several numeric inputs; use a complete tested example matching the vocabulary or label it as partial. Update Architecture with the aggregate public functions, profile baseline, session registry, and delivery mechanism. README currently says neither adapter stores a KB of its own, which conflicts with Architecture and server `start`; describe each adapter's owned session accurately.

Record the API implementation as the next agreed migration/integration step after the current index through step 26, with a plain-English note, Lisp/component provenance, what remains absent, and runnable examples. Do not mark this proposal as a completed migration. Preserve the component split, `Som-Level-Goal` as a Thing subclass, rule priority, omitted rules 5/6, and the caller's clock, safety checks, ordering, catalog choice, and execution. No configuration, credentials, or game data import change is required.

### 6. Repair the AGENTS.md skill reference requested by the user

Files: `AGENTS.md:17`; `.grok/skills/align-repository/SKILL.md`.

Standard: AGENTS.md Skills (follow the named skill without duplicating its steps).

The entry exists, but its local skill only permits four other targets. Update the entry to reference the valid shared skill at `../som-nlp-service/.codex/skills/align-repository/SKILL.md` and explicitly name this repository. This is a sibling-checkout dependency; disclose it in the entry. A future self-contained installation could replace the stale local skill and restore the local reference, but that skill rewrite is outside the requested AGENTS.md edit. Do not copy workflow instructions into AGENTS.md.

## Acceptance criteria for a later implementation

Use `clojure.test` and the existing `som.km.som-agent-goal-test` fixtures as the behavioral oracle. Add focused library and adapter tests covering:

- Fresh-event equivalence to the baseline-based fixture, with no leaking mobile, conclusion, or clock from a previous event, and isolation across two players.
- Engine/library, prompt, and HTTP agreement for the same complete projection; ordered readings, ordered exits, symbol/string distinctions, exact ratios, and empty versus unknown values.
- First-score behavior, score replacement of estimates, pace boundaries at 4/5 and 6/5, zero budget, zero span, death to cautious with cooldown 30, no repeated notch step on a view, and stale-clock withholding.
- Rule priority, rule 1 and 14 null directives, explicit empty hold, rule 12 mixed attempt roles, no invented rules 5/6, and rejection of asserted conclusion slots.
- Goal policy changes invalidate revisions; duplicate death/score returns the prior response; conflicting retry, old sequence, and interleaved goal update cannot publish an inconsistent result.
- Aggregate diagnostics retain errors from earlier steps; failed validation or inference leaves published inputs/results unchanged; no socket or filesystem work occurs in pure orchestration functions.
- HTTP request/response and status mapping; any socket test starts its own server on port 0 and stops it in cleanup.
- A packaged-artifact test from outside the source checkout can require the engine and loader, load the game profile, evaluate one event, and print the full license.

Run `clojure -M:test` before declaring the later implementation complete. The current report-only task intentionally did not compile, test, build, or change code.

## Lint and remaining uncertainty

The required `clj-kondo --lint src/som test build.clj deps.edn` invocation could not run because `clj-kondo` is not on PATH. No dependency was installed and no lint findings or clean result are claimed. Lint analysis stopped there; the source-grounded proposal above remains reviewable. No AGENTS.override.md was found at the target root.

The agentic-player implementation was not present among the nearby checkouts inspected. Its exact field naming, authentication/session ownership mechanism, direction vocabulary, reconnect/restart requirements, and any successor-goal policy remain unverified integration requirements. The proposed routes and defaults beyond the existing component semantics are design recommendations, not existing functionality or requirements imposed by AGENTS.md.
