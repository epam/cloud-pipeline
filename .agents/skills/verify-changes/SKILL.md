---
name: verify-changes
description: Find and run the checks a change owes, from what it touches and what that can reach. Use after editing any file, when about to call a change finished, or when asked whether something passes.
---

# Verifying a change

The Verify step. Decide what to run from the diff itself — the paths in it, and what those paths can reach.
Reach comes in two kinds and only one of them has a command:

- **A Gradle module's dependents** owe their tests as well as the module's own. Their `build.gradle`
  is what names them:
  ```bash
  grep -rl 'project(.:core.)' --include=build.gradle .
  ```
  Some declare it as `project(path: ":x", configuration: "y")` instead, so treat that as a starting
  list rather than a complete one.
- **Build-time and runtime couplings** — `client/` into the API jar's static resources,
  `workflows/pipe-common/` into every job container — have no check that covers them, and the
  artifact tasks that would exercise one are off-limits. Reading the diff against what the coupling
  forbids is the verification; `client/AGENTS.md` → "Pitfalls" lists them for `client`, and root
  `AGENTS.md` → "Repository structure" names the other coupled directories.

**Never the whole platform.** `buildAll`, `buildAllFast`, `distZip`/`distTar`, `buildDoc`, the
`bootRepackage`/`buildUI`/`buildPipe` artifact tasks and everything under `e2e/` cost between an hour
and half a day, and belong to post-merge CI or to release time by hand. Never escalate to a heavier
check because a lighter one was unavailable.

**A partial toolchain is the normal case here.** Most contributors have one language's tooling and not
the others, so you satisfy a check by running it *or* by recording that the tool was absent. A declared
skip is a correct outcome; a silent one is not.

| Changed | Run | More |
|---|---|---|
| `client/` | `cd client && npm run lint`, and `npm run stylelint` for CSS, plus the whole test suite — that runner is `client/AGENTS.md`'s to name, and it takes seconds. Then the `verify-client-live` skill, to check the change in a browser | `client/AGENTS.md` |
| a Java module | `./gradlew :<module>:test`, then `./gradlew checkstyleMain checkstyleTest pmdMain pmdTest` | `api` and `elasticsearch-agent` tests need a live PostgreSQL - see `api/src/test/resources/test-application.properties` |
| `pipe-cli/` | `./gradlew :pipe-cli:test` | |
| `workflows/pipe-common/`, `scripts/` | `cd workflows/pipe-common && export PYTHONPATH=$PYTHONPATH:$PWD && python -m pytest` | |
| any `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.cursor/`, `.github/instructions/`, `.agents/` | `.agents/skills/commit-changes/scripts/verify-docs.sh` | |

A path in no row has no check to run, and nothing else will check it later — `deploy/`, `docs/` and
`e2e/` are the large cases — so reading the diff is the verification.
