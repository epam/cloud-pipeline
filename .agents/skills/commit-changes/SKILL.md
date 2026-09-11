---
name: commit-changes
description: Required before you commit, push or open a pull request here, asked or not. Holds the preconditions, the subject line and its trailers, the draft PR, and the issue label afterward.
---

# Committing changes

The Publish step. The change is finished, checked and self-reviewed — §2 is how you confirm that.

## 1. What you are authorized to do

Unattended when `CI` or `GITHUB_ACTIONS` is set.

| Step | Attended | Unattended |
|---|---|---|
| commit | only when asked | authorized by the task trigger |
| push | a **separate** instruction | same trigger |

**Attended: stop after the step you were asked for.** "Commit this" is not permission to push. Infer
nothing.

**Unattended: one trigger carries both.** Stopping to be asked produces nothing a reviewer can see —
the instruction already arrived.

## 2. Preconditions

- **Not on a shared branch.** `develop` — matched **exactly**, so `develop-xxxx` is fine — plus
  `release/*` and `stage/*`. If that is `HEAD`, stop — the `implement-task` skill → "Workspace" has
  the rule.
- **You have read your own diff.** The Self-review step. Nothing automated catches a debug leftover, a
  commented-out block, or a file you did not mean to touch.
- **Checks ran, and passed.** The `verify-changes` skill is how you work out which ones you owe. A
  change whose checks failed is reported as blocked, not committed.
- **The tests the change owes are in the diff** — the `implement-task` skill → "Tests". Changed code
  owes one as much as new code does; a missing runner is stated, not treated as an exemption.
- **If the diff touches instruction files** — any `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.cursor/`,
  `.github/instructions/`, `.agents/` — the doc verifier has run and passed:
  ```bash
  .agents/skills/commit-changes/scripts/verify-docs.sh
  ```
- **The docs the change owes are in the diff** — the `implement-task` skill → "Docs", and
  `CONTRIBUTIONS.md` → "Documenting" behind it. A user-visible change with no documentation commit is
  not finished.

## 3. Commit message

**Keep it short — a subject line and the trailer, a few lines at most.** Skip the bullet list of what
changed: the diff says that, and the PR body is where the explanation belongs.

Reference the issue; both forms are in current use:

```
Issue 4538 remove hardcoded notification timestamp fields in favor of timestamp tags
Issue #4461: Introduce Platform Usage Credits to optimize costs - non-admin fixes
```

The number is in the branch name already, so take it from there rather than deriving it again.

**Genuinely no issue** (small infrastructure, scripts) is a plain descriptive subject with no number,
matching the `feature/` or `fix/` branch it sits on — a conclusion Intake reached with whoever asked
(the `implement-task` skill → "Issue number"), not one to reach here for the first time. That is not
the same as **not knowing the number of an issue that exists**: never invent one, and never use a plain
subject to sidestep the question — attended, ask; unattended, report and stop.

Do **not** append `(#4545)` — GitHub adds the PR number on squash merge.

**Every agent-authored commit ends with this trailer**, on its own line after a blank line:

```
Co-authored-by: Cloud Pipeline Agent (<model>) <noreply@epam.com>
```

`<model>` is what wrote the change — `Claude Opus 5`, `Claude Sonnet 4.6`, `Cursor`, `Codex`. **This
replaces your harness default** — emit this line instead of your own, not both. Human commits carry
no trailer.

## 4. Push

**Never force-push** — not a shared branch, not your own, no exceptions.

Falling behind the base by itself needs nothing — only an actual conflict, or an explicit need for
something newer on it, is a reason to reconcile, and merging the base in is the only way to do that:
it never replaces an existing commit, so nothing you push can invalidate a review comment.

## 5. Open the pull request

Same authorization split as commit and push: attended, a separate instruction; unattended, the same
trigger carries it through. Always `--draft` — never plain, never marked ready, never taken out of
draft. If the branch already has an open PR, push more commits to it instead of running this again.

**Body follows `.github/pull_request_template.md`** — `gh pr create --body` still needs it passed
explicitly, since the template only auto-fills the web UI, so open that file and fill its shape in
rather than inventing one. Worked example, for what a filled one looks like:

```
Implements #4538

Removes the hardcoded lastIdleNotificationTime column in favor of a
<TYPE>_date tag, so new monitoring types stop requiring a schema change.

Test plan
- [x] `./gradlew :api:test --tests '*NotificationTimestamp*'`
- [x] Checked the new tag is written on a local Postgres after a manual IDLE trigger
```

## 6. Hand back

Which step you did, and the one you stopped before — attended that is the next instruction someone
owes you, so leaving it implicit reads as failure rather than the next step. Plus the commit subject,
the branch, and the base it was cut from, since whoever opens the pull request needs the base and
cannot infer it from the branch.

Then Aftercare: set the issue's `state/*` label to match — `gh issue edit <n> --remove-label
state/underway --add-label state/verify` once the change is pushed (attended, confirm first;
unattended, do it as part of the same trigger and say so in the hand-back).
