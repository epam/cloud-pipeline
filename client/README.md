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
| `npm test` | Runs the Jest test runner. |
| `npm run lint` / `npm run lint:fix` | ESLint on `src/`. |
| `npm run stylelint` / `npm run stylelint:fix` | Stylelint on `src/**/*.css`. |
| `npm run serve-build` | Serves the built app locally (via `serve`). |
| `npm run gui-themes-prepare` | Builds GUI theme assets. |
| `npm run gui-themes-update` | Theme development watcher. |

## Local development and API proxy

By default, **`npm start`** starts a small local reverse proxy so the browser can call the remote API from the webpack dev origin without CORS issues. The bundle is configured to use a localhost proxy URL while the proxy forwards to the origin derived from **`SERVER`** (or **`PROXY_TARGET`**).

- To **turn off** the proxy: set `PROXY_DISABLED=1` or `true` (or `SKIP_DEV_PROXY=1` / `true`). You must then handle CORS or same-origin access yourself.
- To run **only** the proxy (for debugging): from `client/`, run `node scripts/dev-proxy.js` (after loading the same `.env` chain as `npm start`).

Full variable reference and behavior: [scripts/dev-proxy.md](scripts/dev-proxy.md).
