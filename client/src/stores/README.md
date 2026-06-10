# `src/stores/`

Zustand stores for **client/UI state** and specialty polling — not for server API caching.

## Server API cache → `src/queries/`

Shared, cacheable API reads use **TanStack React Query** in [`src/queries/`](../queries/). Each domain module exports:

- `xxxKeys` — hierarchical query key factory
- `xxxQueryOptions(...)` — reusable `queryOptions` for `useQuery` / `fetchQuery`
- `fetchXxx(...)` — non-React helpers via the singleton `queryClient`
- Domain-specific derived hooks (e.g. `useCloudRegion`, `useLibrarySubTree`) where needed

See [`src/queries/query-client.ts`](../queries/query-client.ts) for default `staleTime` (5 min).

## What belongs in `src/stores/`

| Directory        | What it stores                                                      |
| ---------------- | ------------------------------------------------------------------- |
| `misc/`          | Miscellaneous UI toggles (library expanded, etc.)                   |
| `notifications/` | System notifications                                                |
| `preferences/`   | Preference name constants and derived config (`named-preferences/`) |
| `pipelines/`     | Shared types only                                                   |
| `runs/`          | Active run counts (polling)                                         |
| `themes/`        | UI theme definitions and application                                |
| `ui-navigation/` | Navigation structure                                                |
| `users/`         | Authenticated user, impersonation                                   |

## What does NOT belong here

Do not create a Zustand store for:

- **Shared API collections** — use `src/queries/` with React Query
- **Search / autocomplete results** — local `useState` + API call
- **Mutation responses** — handler + local loading/error state
- **Single-screen reads** — `useEffect` + API with `AbortController`
- **Paginated or filtered lists** — local state + API call
- **Transient UI state** — `useState`

For the full decision guide, see the `use-zustand-stores` skill.
