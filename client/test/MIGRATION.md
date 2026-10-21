# client/test harness — runner and React upgrades

What a later Vite/React task has to touch, and nothing else. **No test file appears in either
column**: test files import only from `@test`, the rule `AGENTS.md` in this directory carries.

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

The last row is the trap: Vitest transforms through Vite, Vite through esbuild, and esbuild does
not support the legacy decorators this codebase is built on. The Vite config must route JSX/JS
through `@vitejs/plugin-react` with the existing `babel.config.js` plugins, or every `@observable`
in `src/` fails to parse.
