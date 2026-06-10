# Cloud Pipeline — Web client

This directory contains the **Cloud Pipeline** browser UI: a React application (MobX, Ant Design, Vite) that talks to the platform REST API.

For the full product overview, see the [repository root README](../README.md) and the [documentation](https://epam.github.io/cloud-pipeline/).

## Prerequisites

- **Node.js** — **v24** (or current LTS) is the supported version for this client.
- **npm** — dependencies are managed with `package-lock.json` when present.

## Setup

From this directory:

```bash
npm install
```

Create a **`.env`** file (and optionally **`.env.development.local`**) in `client/`. The app loads these via [`config/vite-env.ts`](config/vite-env.ts) (`REACT_APP_*` variables are exposed to the bundle via Vite `define`).

Typical development settings:

- **`SERVER`** — base URL of the real Cloud Pipeline deployment (for example `https://your-host/pipeline/`). The dev server proxies `/pipeline` to this upstream.
- Any other **`REACT_APP_*`** or server-side env keys your deployment expects (align with your environment and internal docs).

## Scripts

| Command | Description |
|--------|-------------|
| `npm start` | Development: Vite dev server (default **http://localhost:3000**). Proxies `/pipeline` to the upstream from `.env`. |
| `npm run build` | Production build into `build/`. Gradle `client:buildUI` uses this with `PUBLIC_URL=/pipeline`. |
| `npm run preview` | Serves the production build locally (`vite preview`). |
| `npm test` | Runs Vitest in watch mode (no tests yet). |
| `npm run test:run` | Runs Vitest once (CI-friendly). |
| `npm run lint` / `npm run lint:fix` | ESLint on `src/`. |
| `npm run stylelint` / `npm run stylelint:fix` | Stylelint on `src/**/*.css`. |
| `npm run serve-build` | Serves the built app locally (via `serve`). |
| `npm run gui-themes-prepare` | Builds GUI theme assets. |

## Local development and API proxy

The Vite dev server proxies **`/pipeline`** to the upstream origin from **`SERVER`** (or **`PROXY_TARGET`**) in `.env`, so the bundle can use `SERVER=/pipeline` (same-origin) instead of calling the remote host directly.

| Variable | Role |
|----------|------|
| `SERVER` | Real deployment URL; upstream origin is parsed from it unless `PROXY_TARGET` is set. |
| `PROXY_TARGET` | Optional explicit upstream origin (overrides `SERVER`). |
| `PROXY_INSECURE=1` | Disable strict TLS verification for the upstream. |
| `PROXY_BEARER_TOKEN` | `Authorization: Bearer …` on proxied requests (HTTP and WebSocket). |
| `PROXY_COOKIE_<Name>=value` | Merged into upstream `Cookie` (e.g. `PROXY_COOKIE_SESSION=…`). |
| `PROXY_DISABLED` / `SKIP_DEV_PROXY` | `1` or `true` — no proxy; bundle uses raw `SERVER` from `.env` (you must handle CORS yourself). |

Proxy logic lives in [`config/vite-env.ts`](config/vite-env.ts) (`buildDevProxy()`).

## Production build

Gradle copies the Vite output from `client/build/` into `api/src/main/resources/static/`:

```bash
PUBLIC_URL=/pipeline VERSION=<version> npm run build
```

Or from the repo root:

```bash
./gradlew client:buildUI
```

Asset paths in the bundle use `/pipeline/` when `PUBLIC_URL=/pipeline` is set at build time.
