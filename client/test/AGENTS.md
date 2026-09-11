# client/test/AGENTS.md

The unit-test harness. Every test file's imports look like this and nothing else:

```js
import {render, screen, click, waitFor, fn, stubApi} from '@test';
```

`@test` is a `moduleNameMapper` entry today (`jest.config.base.js`) and will be one
`resolve.alias` entry under Vite — one line per toolchain, which is the whole point. Everything
version- or framework-specific sits behind this module, so a runner or React-version change edits
the files below and no `*.test.js`.

## The harness map

```
client/
├── jest.config.js               the unit suite    ← both replaced wholesale
├── jest.live.config.js          the live suite       at the Vitest switch
├── jest.config.base.js          settings shared by both
├── .env.test.local              untracked; the only file that may name a real server
└── test/
    ├── index.js                 the ONLY module a test file imports from
    ├── config.js                defaults + the CP_TEST_* knobs — env-mechanism-bearing
    ├── render.js                render/cleanup/act      — React-version-bearing
    ├── events.js                click/change/type/hover — React-version-bearing
    ├── providers.js              renderWithStores/renderWithRouter — MobX + router-bearing
    ├── runner.js                 fn/spyOn/useFakeTimers/describeLive — framework-bearing
    ├── http.js                   stubApi() — the Result<T> fetch stub
    ├── factories/index.js        payload builders
    └── setup/
        ├── env.js                setupFiles, unit — the dotenv chain, the fixed env, the network guard
        ├── live.js               setupFiles, live — node-fetch, skip-if-unconfigured
        ├── timezone.js           the timezone pin, shared by both suites
        └── matchers.js           setupFilesAfterEnv, unit only — jest-dom
```

## Two suites, and what separates them

| | `npm test` | `npm run test:live` |
|---|---|---|
| matches | `src/**/*.test.{js,jsx}`, `scripts/**/*.test.js` | `src/**/*.live.test.js` |
| environment | `jsdom` | `node` — no DOM, so no CORS and no proxy |
| network | **impossible** | a real deployment |
| configured by | `config.js`, in code | `.env.test.local`, untracked |
| unconfigured | n/a | skips itself with a message, never fails |
| covers | everything | the model layer only |

The unit suite's defaults are **code, not configuration** — `config.js`'s `unitEnvironment`
fixes `SERVER`, `API_PATH`, `PUBLIC_URL` and `VERSION` unconditionally, and `setup/env.js`
assigns them after the dotenv chain has already run, so a developer's untracked `client/.env`
cannot reach a unit test. Nothing a unit test depends on is untracked, so a green run means the
same thing on every machine.

The live suite's knobs are `CP_TEST_API_URL` (the server root, not the API path — `API_PATH` stays
whatever `client/config/env.js` hardcodes) and `CP_TEST_API_TOKEN`, read from `.env.test.local`
only. Namespacing them away from `SERVER` makes it structurally impossible for someone's `.env` to
influence a test run. Nobody is expected to have this file; its absence is the normal case and
`setup/live.js` turns it into a clean, readable skip rather than a failure — including on a
fresh clone and in CI, if this suite is ever wired into CI (it should not be — see below).

**The network guard**, in `setup/env.js`, is the load-bearing line of the whole arrangement:

```js
global.fetch = function () {
  throw new Error(
    'Network access in a unit test — use stubApi() from @test, or make this a *.live.test.js'
  );
};
```

It turns "a unit test accidentally hit the API" into an immediate, legible failure instead of a
flake that surfaces later, and it makes the two suites structurally separate rather than separate
by naming convention alone.

## The boundary with `e2e/`

A live-API suite here can drift into becoming a second end-to-end suite. It must not: the repo
already has two of those, and they are long-running and never an agent's to run casually.

The niche that is legitimately *not* e2e is the **contract smoke test** — does an endpoint still
return the shape its model reads, nothing about platform behaviour and nothing about a multi-step
flow. Seconds, no browser, no workflow. Concretely:

- A live test always ends `*.live.test.js`. The unit suite's network guard means a misfiled one
  fails immediately with the guard's message rather than quietly making real requests.
- A live test asserts on **response shape only** — that the envelope carries the fields its
  consumers read — never on which records exist, never on a sequence of calls. Rule 13 below.
- Anything that exercises a user flow, a browser, or more than one request belongs in `e2e/`, not
  here.
- The live suite runs in `node`, not `jsdom`: with no DOM there is no origin, so there is nothing
  for a browser's CORS policy to apply to and no reason to involve the dev proxy. The cost is one
  devDependency — Node 14 has no native `fetch`, so `setup/live.js` assigns `node-fetch` to
  `global.fetch` when the suite is configured.

## The thirteen rules

These are the deliverable as much as the tests are. A test file may cite one of these by number
("rule 7") and a reader can follow it.

1. **Import nothing from the framework.** No `jest.fn()`, no `vi.fn()`, no
   `import {vi} from 'vitest'`. `fn`, `spyOn`, `useFakeTimers` and `advanceTimers` come from
   `@test`. A runner switch then edits `runner.js` and no test file.
2. **No module mocking.** `jest.mock()` is hoisted compiler magic whose factory and auto-mock
   semantics differ from `vi.mock()`. Pass collaborators in as props or constructor arguments, or
   stub the network with `stubApi()`. Where a module mock is genuinely unavoidable, say so in a
   comment rather than reaching for one quietly.
3. **No `done` callbacks.** Vitest does not support them. Async tests are `async` and return.
4. **No DOM snapshots.** React 15's server-rendered markup carries `data-reactroot`,
   `data-reactid`, `data-react-checksum` and `<!-- react-text -->` comment wrappers that no later
   React version emits — any DOM snapshot committed today is guaranteed to fail on the React
   upgrade for reasons that have nothing to do with the component. Snapshots of plain transformed
   data are fine; those survive both migrations.
5. **No enzyme, no `shallow`, no `react-test-renderer`.** Enzyme has no adapter past React 17 and
   the shallow renderer is on its way out of React itself. Everything renders into a real
   container, via `render` from `@test`.
6. **Query by role, label or text — never by CSS class or component internals.** `antd`'s class
   names change across its majors; ARIA roles do not. Asserting on a CSS-module class your own
   component owns (`identity-obj-proxy`'s output) is not what this rule forbids — the concern is
   antd's own internals, not your module's.
7. **Always `await` after an interaction.** `await click(...)`, `await waitFor(...)`,
   `await screen.findByText(...)`. React 15 flushes synchronously, so a bare assertion right after
   an event passes today and breaks under React 18's automatic batching. This single rule decides
   more of the suite's survival than any other.
8. **Fake timers are always modern.** `useFakeTimers()` from `@test` always passes `'modern'` to
   the runner — Jest 26 defaults to the legacy implementation, and Vitest only has the modern one.
9. **No bundler features in tests.** No `require.context`, no loader-prefixed imports.
10. **MobX through its public surface only.** No `mobx/lib/...`, no `useStrict`. A plain
    `observable({...})` object is a sufficient test double for a store; it does not need
    `@observable` class fields. MobX 3 decorators become `makeObservable` at MobX 6; public
    methods and `@computed` reads do not change.
11. **Provider and router wiring only via `renderWithStores` / `renderWithRouter`** from `@test`.
    Never a hand-rolled `<Provider>` or `<Router>` in a test file — react-router 3 → 6 rewrites
    those wrappers, and it should rewrite one file, not every test that uses one.
12. **No test file reads `process.env`.** Import `liveApi` and `liveTestsEnabled` from `@test`
    instead. Vite loads env through `loadEnv` with its own `envPrefix` rules rather than dotenv,
    and `config.js` is the one file that absorbs that difference.
13. **A live test asserts on response shape, never on behaviour or a flow.** See *The boundary
    with `e2e/`* above. Wrap every live test file's suite in `describeLive` from `@test`, which
    skips it cleanly when `.env.test.local` is absent.

## Two things that are easy to get wrong here

- **Run `nvm use` inside `client` before any check here, including `test:live`.** A shell's
  default Node is often a newer LTS, not the 14 this app needs (`client/.nvmrc`,
  `.agents/localenv/node.md`). Under the wrong Node, `jest`/`babel-jest` fail every suite —
  unit or live — with `[BABEL] ... Requires Babel "^7.22.0 || ^8.0.0-0", but was loaded with
  "7.2.2"`. That message points at a plugin version mismatch, but the actual cause is the Node
  version; reinstalling or touching `node_modules` will not fix it.
- **Raw `Date` local-time methods (`getHours`, `toString`, ...) still follow the machine's
  timezone**, even though `setup/timezone.js` pins `moment-timezone`'s default to UTC.
  Assigning `process.env.TZ` does not work either — Node reads the zone once at startup, before any
  setup file runs. Assert through the application's own date helpers (which go through
  `moment-timezone`), or explicitly in UTC.
- **`mouseenter`/`mouseleave` do not reach a component under test.** They do not bubble, so
  React's single document-level listener never sees them; React synthesises `onMouseEnter` from
  `mouseover` itself. `hover`/`unhover` in `events.js` fire the `mouseover`/`mouseout` pair
  instead — the same pair `@testing-library/user-event` uses, so this survives the migration.
  Anything built on `rc-trigger` (antd's `Tooltip`, `Dropdown`, `Popover`) depends on this.

## The migration checklist

What a later Vite/React task has to touch, and nothing else:

| At the Vitest switch | At the React upgrade |
|---|---|
| delete both jest configs, add `test` + `projects` to `vite.config.js` | `render.js` → re-export `@testing-library/react` |
| `moduleNameMapper` → `resolve.alias` (`@test`) | `events.js` → `@testing-library/user-event` |
| `identity-obj-proxy` → Vite's native CSS-module handling | `providers.js` → react-router 6 wrappers |
| `runner.js`: `jest.*` → `vi.*` | `providers.js` → MobX 6 `makeObservable` stores |
| `setup/matchers.js` → `@testing-library/jest-dom/vitest` | |
| `config.js`: dotenv → Vite's `loadEnv` / `envPrefix`. The filenames do not change — `.env.test.local` is what Vitest reads natively | |
| `node-fetch` becomes redundant once tests run on Node 18+ | |
| legacy decorators need `@vitejs/plugin-react`'s `babel` option — esbuild cannot transpile them | |

The last row is the one trap worth writing down: Vitest transforms through Vite, Vite transforms
through esbuild, and esbuild does not support the legacy decorators this codebase is built on. The
Vite config must route JSX/JS through `@vitejs/plugin-react` with the existing `babel.config.js`
plugins, or every `@observable` in `src/` fails to parse.

**No test file appears in either column.** That is the point of this harness.
