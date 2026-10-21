# Cloud Pipeline — Web client

This directory contains the **Cloud Pipeline** browser UI: a React application (MobX, Ant Design, Webpack 4) that talks to the platform REST API.

For the full product overview, see the [repository root README](../README.md) and the [documentation](https://epam.github.io/cloud-pipeline/).

## Prerequisites

- **Node.js** — **v14** is the currently supported version for this client.
- **npm** — dependencies are managed with `package-lock.json` when present.

## Setup

From this directory:

```bash
npm install
```

Create a **`.env`** file (and optionally **`.env.development.local`**) in `client/`. The app loads these via `config/env.js` (same pattern as Create React App: `REACT_APP_*` variables are exposed to the bundle).

Typical development settings:

- **`SERVER`** — base URL of the real Cloud Pipeline deployment (for example `https://your-host/pipeline/`). The dev tooling uses this to know where API traffic should ultimately go.
- Any other **`REACT_APP_*`** or server-side env keys your deployment expects (align with your environment and internal docs).

## Scripts

| Command | Description |
|--------|-------------|
| `npm start` | Development: starts the optional local API proxy (unless disabled), then the webpack dev server (default **http://localhost:3000**). |
| `npm run build` | Production build into `build/` (uses increased Node heap). |
| `npm test` | Runs the unit test suite (Jest, jsdom, no network) — `src/**/*.test.{js,jsx}` and `scripts/**/*.test.js`. |
| `npm run test:watch` | The same, in watch mode. |
| `npm run test:coverage` | The same, with a coverage report under `coverage/`. |
| `npm run test:live` | Runs the live test suite — `src/**/*.live.test.js`, against a real deployment. Configured only by `.env.test.local` (see below); with no such file it skips itself and exits 0. |
| `npm run lint` | ESLint on `src/`, `test/` and `scripts/` (`.js` and `.jsx`), failing only on problems that `.eslintbaseline.json` does not already record. |
| `npm run stylelint` | The same, for `src/**/*.css` and `src/**/*.less`, against `.stylelintbaseline.json`. |
| `npm run lint:all` / `npm run stylelint:all` | Report every problem, the baselined debt included. |
| `npm run lint:baseline` / `npm run stylelint:baseline` | Re-record the baseline — only ever to shrink it, after fixing something. |
| `npm run lint:fix` / `npm run stylelint:fix` | The linters' own `--fix`, over the same files. |
| `npm run serve-build` | Serves the built app locally (via `serve`). |
| `npm run gui-themes-prepare` | Builds GUI theme assets. |
| `npm run gui-themes-update` | Theme development watcher. |

## Local development and API proxy

By default, **`npm start`** starts a small local reverse proxy so the browser can call the remote API from the webpack dev origin without CORS issues. The bundle is configured to use a localhost proxy URL while the proxy forwards to the origin derived from **`SERVER`** (or **`PROXY_TARGET`**).

- To **turn off** the proxy: set `PROXY_DISABLED=1` or `true` (or `SKIP_DEV_PROXY=1` / `true`). You must then handle CORS or same-origin access yourself.
- To run **only** the proxy (for debugging): from `client/`, run `node scripts/dev-proxy.js` (after loading the same `.env` chain as `npm start`).

Full variable reference and behavior: [scripts/dev-proxy.md](scripts/dev-proxy.md).

## Testing

`npm test` needs no setup and no environment file — its defaults are fixed in the test harness
itself, so a green run means the same thing on every machine. See `test/AGENTS.md` for the harness
and the rules test files follow.

`npm run test:live` is opt-in and runs a handful of contract tests against a real deployment. It is
configured by **`.env.test.local`**, in this directory, untracked and never read by `npm test`:

- **`CP_TEST_API_URL`** — the server root of a real deployment (for example
  `https://your-host/pipeline/`).
- **`CP_TEST_API_TOKEN`** — a bearer token for that deployment, if the endpoints under test need
  one.

With no `.env.test.local`, `npm run test:live` skips itself with a message and exits 0 — that is
the expected state for most contributors, not a failure to fix.
