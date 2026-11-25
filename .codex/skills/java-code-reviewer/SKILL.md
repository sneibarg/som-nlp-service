---
name: java-code-reviewer
description: "Review Java code against modern software engineering practices and write a report of findings and recommendations. It does not change the code. Use when reviewing Java changes, classes, modules, or pull requests for design, APIs, error handling, concurrency, testing, and maintainability, or when the user runs /java-code-reviewer."
---

# Java code review

Review the Java the user named. Write the report and stop. Do not edit source, tests, or build files.

## Scope

1. Use the files, module, diff, or pull request the user named.
2. If they did not name a scope, review the uncommitted `.java` changes. If there are none, ask what to review.
3. Read the build file and record the Java release. Read the code in scope and the tests that cover it. Judge against that release and against the practices the project already uses.

## Practices

Report a finding only when the code breaks one of these and you can point at the line. Skip taste, formatting, and a rewrite into syntax the surrounding file does not use.

- **Language.** Use a feature of the project's Java release when it removes real boilerplate: records for pure data, sealed types for a closed set, switch expressions, pattern matching for `instanceof`, text blocks for multi-line text.
- **API.** Keep the public surface to what callers need. Validate inputs at the boundary. Return an unmodifiable view instead of a live collection. Do not expose a field that callers can mutate.
- **Errors.** Throw a specific exception with a message the caller can act on. Keep the cause. Do not catch and ignore, and do not catch `Exception` or `Throwable` to hide a failure.
- **Resources and text.** Close every `AutoCloseable` with try-with-resources. Use `java.time`. Name the charset when converting bytes and text.
- **Concurrency.** Do not share mutable state across threads without a concurrent type or a lock that is visible at the field. Do not start an unbounded thread per call.
- **Dependencies.** Use the JDK or a library already on the build. Do not add a dependency for a few lines.
- **Tests.** A behavior that can fail has a test that asserts the outcome. Do not ask for a test that only mirrors the implementation.

## Report

```markdown
# Java review

**Scope:** <what was read, and the Java release>

## Findings

### 1. <short title>
- **Severity:** high | medium | low
- **Where:** `path` — `Symbol.method`
- **Finding:** <what is wrong and why it fails the practice>
- **Recommendation:** <the change to make>

## Summary

<count by severity, or "No findings.">
```

Order findings by severity, then by file. High means a defect or a failure a caller can hit. Medium means a practice break that will cost the next change. Low means a small, concrete improvement.

If nothing qualifies, write the scope line, omit Findings, and set the summary to "No findings."
