---
name: align-repository
description: Reports the proposed changes that would make this repository meet the standards in AGENTS.md and the skills that file names. The only valid target repository is som-knowledge-machine. Files the alignment report and does not change code. Use when the user runs /align-repository or asks to align the repository, bring the repo up to its standards, or report the changes that would match its conventions.
---

# Align repository

Write a report of the proposed changes that would make this repository satisfy `AGENTS.md`, and file it at the path under Report. That report is the whole task. `AGENTS.md` and the skills that file names are the standards. Read them. Do not copy those standards into this skill or into a second checklist.

Work from the repository that contains this skill. Do not edit, move, format, compile, test, or commit code.

## Target repositories

The only valid target repository is:

- `som-knowledge-machine`

If the user names any other repository, stop. Say that `som-knowledge-machine` is the only valid target.

## Find the gaps

Read `AGENTS.md` at the repo root. Include every section. When it names a skill, read that skill and apply it as a standard. The alignment report is the only file you write.

Compare `src/` with those standards. Skip `target/`. When a standard applies to a file outside `src/`, record that gap too.

A proposal preserves behavior except where a standard requires a move or a file rename. When a standard requires a move, record the type, the destination path, and each importer.

## Report

File the report in `docs/alignment`. Create the folder when it is missing. Name the file with the target repository from Target repositories and the local time it is filed, in 24-hour form: `<repository>_<YYYY-MM-DDTHHMMSS>.md`.

For each recorded gap, name the file, the standard it addresses, and the proposed change, including any move and importer notes. If the comparison finds no gap, say there is nothing to propose. If a gap cannot be stated precisely, name the file and what is still unknown.
