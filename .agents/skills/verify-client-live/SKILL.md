---
name: verify-client-live
description: Open a user-visible client/ change in a browser against a real Cloud Pipeline deployment, or a dev sandbox. Use when the diff changes UI layout, routing, client state or rendered data — not for tests, the test/ harness, lint/build config, or non-visual edits. Needs a deployment someone has already set up, or a dev sandbox running its services, and a browser tool in the session.
---

# Browser-checking a client/ change

**Don't wait to be asked** where the change is user-visible — layout, styling, routing, client state,
or rendered data.

It needs a deployment to point at and a browser tool in this session. Where either is missing, say
which, skip the check, and say in the hand-back that nobody looked at the change in a browser. Never
set one up: deploying the platform is never an agent's job, and a browser stack is opt-in under
`configure-dev-environment`.

**A dev sandbox is the other deployment to point at**, where one runs its services already
(`dev-sandbox` skill, `sandbox.sh status`), or someone asks for one to check the change against.
`.agents/skills/dev-sandbox/scripts/sandbox.sh ui`, in the background, takes the place of steps 1
and 2: it runs the checkout's GUI against the sandbox's API, as its admin, on the same ports, and
waits for the first compile. Then step 3; `sandbox.sh ui --stop` once the check is over.

## 1. Check availability

```bash
.agents/skills/verify-client-live/scripts/check-ui-local-available.sh
```

It prints `available`, or what is missing and how to fix it. Where it is not available, pass its message
on to the user and skip the check. Don't go hunting for the configuration yourself, and don't offer
another way to supply it.

## 2. Start it

```bash
.agents/skills/verify-client-live/scripts/start-ui-for-review.sh
```

Run this **in the background** — it is a long-running dev server — and kill it once the check is over.
It uses port `3100`, with its own proxy on `9199`, so it does not collide with a developer's own
`npm start`.

**The first compile takes several minutes.** Wait for the dev server to report that it is ready
before opening the page. Silence is not a failure, and a page that will not load before then means
nothing.

## 3. Look at it

Open `http://localhost:3100` with **whatever browser tool this session has** — the IDE's browser,
a Playwright MCP if it is already connected, a screenshot tool. Check the actual change: the golden
path and the edge case it touches, not just that the page loads.

If the session has no browser tool, skip and say so.
