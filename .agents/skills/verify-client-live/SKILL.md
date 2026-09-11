---
name: verify-client-live
description: Run client/ against a real Cloud Pipeline deployment and open the change in a browser. Use for every client/ change, except ones that only touch test files, the test/ harness, or build, lint and script config — and whenever asked to visually check, screenshot, or open a client/ change in a browser. Needs a deployment someone has already set up, and a browser tool in the session.
---

# Browser-checking a client/ change

**Every `client/` change gets this.** The only exceptions are changes that touch nothing but
`*.test.js`, the harness under `test/`, or build, lint and script config. Don't wait to be asked,
and don't ask for permission.

It needs two things: a deployment to point at, and a browser tool in this session. Steps 1 and 3
check for them. If one is missing, say which, skip the check, and say in the hand-back that nobody
looked at the change in a browser.

Never set up a deployment for this. Deploying the platform is never an agent's job.

## 1. Check availability

```bash
.agents/skills/verify-client-live/scripts/check-ui-local-available.sh
```

Run it from the repository root. It prints `available`, or what is missing and how to fix it.

If it says not available: pass its message on to the user and skip the check. Don't go hunting for
the configuration yourself, and don't offer another way to supply it.

## 2. Start it

```bash
.agents/skills/verify-client-live/scripts/start-ui-for-review.sh
```

Run this **in the background** — it is a long-running dev server, not a one-shot command. It uses
port `3100`, with its own proxy on `9199`, so it does not collide with a developer's own `npm start`.

**The first compile takes several minutes.** Wait for the dev server to report that it is ready
before opening the page. Silence is not a failure, and a page that will not load before then means
nothing.

## 3. Look at it

Open `http://localhost:3100` in a browser and check the actual change — the golden path and the
edge case it touches, not just that the page loads. Use whatever browser or screenshot tool this
session has. If it has none, skip the check and say so — installing one belongs to the
`configure-dev-environment` skill, and it is the user's call.

## 4. Stop it

Kill the background process once you're done. Leaving a dev server running against someone's real
deployment after the check is over is the kind of thing that gets noticed and not in a good way.
