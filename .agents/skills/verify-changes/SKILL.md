---
name: verify-changes
description: Find and run the checks a change owes, from what it touches and what that can reach. Use after editing any file, when about to call a change finished, or when asked whether something passes.
---

# Verifying a change

The Verify step. Decide what to run from the diff itself — the paths in it, and what those paths can reach.

**A Gradle module's dependents** owe their tests as well as the module's own. Their `build.gradle` is
what names them:

```bash
grep -rl 'project(.:core.)' --include=build.gradle .
```

Some declare it as `project(path: ":x", configuration: "y")` instead, so treat that as a starting list
rather than a complete one.

**Never the whole platform.** `buildAll`, `buildAllFast`, `distZip`/`distTar`, `buildDoc`, the
`bootRepackage`/`buildUI`/`buildPipe` artifact tasks and everything under `e2e/` cost between an hour
and half a day, and belong to post-merge CI or to release time by hand. Never escalate to a heavier
check because a lighter one was unavailable.

**A partial toolchain is the normal case here.** Most contributors have one language's tooling and not
the others, so you satisfy a check by running it *or* by recording that the tool was absent. A declared
skip is a correct outcome; a silent one is not.

**Ask the module for its check.** `settings.gradle` lists every Gradle subproject: run
`./gradlew :<module>:test` where its `build.gradle` gives it a `test` task, and
`./gradlew :<module>:checkstyleMain :<module>:checkstyleTest :<module>:pmdMain :<module>:pmdTest`
where it has Java sources — **each task carries the module prefix**; unprefixed, those four are the
whole platform. Several Python modules have neither — their runner is named in that module's
`AGENTS.md` or `README.md`.

What departs from that:

**`client/`** — the runners in `client/AGENTS.md` → "Checks", then the `verify-client-live` skill
where it applies.

**`client/src/themes/`** — also `npm run gui-themes-prepare && git diff --exit-code -- src/themes`,
and commit what it regenerates.

**`api`, `elasticsearch-agent`** — tests need a live PostgreSQL, configured in
`api/src/test/resources/test-application.properties` and
`elasticsearch-agent/src/test/resources/test-application.properties`.

**`workflows/pipe-common/`, `scripts/`** —
`cd workflows/pipe-common && export PYTHONPATH=$PYTHONPATH:$PWD && python -m pytest`

**Any `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.cursor/`, `.github/instructions/`, `.agents/`** —
`.agents/skills/modifying-instructions/scripts/verify-instructions.sh`

Where that search finds nothing, nothing else will check the path later either — `deploy/`, `docs/`
and `e2e/` are the large cases — so reading the diff is the verification.
