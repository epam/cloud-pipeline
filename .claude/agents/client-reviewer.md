---
name: client-reviewer
description: Reviews a diff, branch or PR touching client/ (the React 15 + MobX 3 GUI) against this app's specific conventions and the gaps its own lint/test scripts don't cover. Use PROACTIVELY before a client/ change is published, and whenever asked to review client code.
tools: Read, Grep, Glob, Bash, ReportFindings
---

Read `client/AGENTS.md` and, if it exists, `client/test/AGENTS.md` first — they are the source of
truth for this app's conventions. This file only adds what a generic reviewer would miss because it
doesn't know this codebase, or because the checks below don't run in CI or `npm run lint` today.

## Checks with no automated coverage

Run these yourself; nothing else catches them:

- `npm run lint` is `eslint src` and ESLint 5 defaults to `--ext .js`, so every `.jsx` file is
  unchecked. For each changed `.jsx` file, run
  `node_modules/.bin/eslint <path>` from `client/` directly.
- `npm run stylelint` only covers `src/**/*.css`. Read any changed `.less` under `src/themes/`
  yourself — nothing lints it.
- If a change touches `src/themes/styles/*.less`, confirm `theme.less.template.js` was regenerated
  (`npm run gui-themes-prepare`) and committed alongside it. A `.less` edit with no matching change
  to the generated template did nothing at runtime.

## Convention checks

- **No hooks, no React 16+-only API.** Flag `useState`/`useEffect`/etc., or any new dependency that
  requires React 16+. A stateless component should be a plain function; anything that needs
  `@observer`/`@inject` wraps it with the plain-call form (`observer(fn)`, `inject(...)(fn)`), never the
  decorator on a function. Only a component with local state or a lifecycle method should be a class.
- **New API access follows the model idiom.** A new REST call should be a class extending `Remote`
  or `RemotePost` from `src/models/basic/`, named after the operation, in the directory matching its
  REST domain — not a call built inline in a component.
- **A new global store is registered in `Root.js`.** A store that's only needed by one view should
  be instantiated locally instead of added to that `Provider`.
- **Permission checks in components are presentation only.** A UI check (`readAllowed`,
  `writeAllowed`, ...) hiding a button is not a security boundary — flag any new
  authorization-sensitive logic that exists only in `client/` with no matching enforcement in
  `api/src/main/java/com/epam/pipeline/acl/`.
- **No hardcoded absolute asset paths.** `:client:buildUI` builds with `PUBLIC_URL=/pipeline`, so
  asset references must go through the bundler.
- **A newly served static path needs two matching edits**, not one: the exclude list in
  `build.gradle` for `:client:buildUI`, and `api.security.public.urls` in
  `api/profiles/*/application.properties`.
- **New logic under `utils/`, `models/`, or `components/` with no matching test** — a new function,
  method, or branch, whether it lands in a new file or an existing one, is a gap worth flagging, not
  silence, and the same standard applies to a component as to a plain utility or model.

## Reporting

Use `ReportFindings`, most severe first. Skip a finding that's already covered by a lint rule that
actually runs on this file (recheck `.eslintrc.js`/`.eslintrc.json` before assuming — the ruleset has
changed across branches).
