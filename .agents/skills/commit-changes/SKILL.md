---
name: commit-changes
description: Required before you commit, push or open a pull request here, asked or not. Holds the preconditions, the subject line and its trailers, the draft PR, and the issue label afterward.
---

# Committing changes

The Publish step. The change is finished, checked and reviewed — §2 is how you confirm that.

## 1. What you are authorized to do

Unattended when you cannot get an answer before the next step (see the `implement-task` skill).

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
- **You have read your own diff, and an agent that did not write it has reviewed it.** The Diff review
  step — the `implement-task` skill → "Fresh-eyes review".
- **Checks ran, and passed.** The `verify-changes` skill is how you work out which ones you owe. A
  change whose checks failed is reported as blocked, not committed.
- **The tests the change owes are in the diff** — the `implement-task` skill → "Tests". Changed code
  owes one as much as new code does; a missing runner is stated, not treated as an exemption.
- **If the diff touches instruction files** — any `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.cursor/`,
  `.github/instructions/`, `.agents/` — the instruction verifier has run and passed:
  ```bash
  .agents/skills/modifying-instructions/scripts/verify-instructions.sh
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
matching the `feature/` or `fix/` branch it sits on — a conclusion Intake reached with whoever asked,
not one to reach here for the first time. Not knowing the number of an issue that exists is a different
thing, and the `implement-task` skill → "Issue number" has what to do about it.

Do **not** append a pull request number like `(#4545)` to the subject.

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
something newer on it, is a reason to reconcile, and merging the base in is how you do that.

## 5. Open the pull request

Same authorization split as commit and push: attended, a separate instruction; unattended, the same
trigger carries it through. **Always `--draft`, never plain.** Marking it ready is the author's call
and needs its own instruction; unattended it stays a draft. If the branch already has an open PR, push
more commits to it instead of running this again.

**Label it `intel/artificial 🤖`** — `CONTRIBUTIONS.md` → "Authorship labels". One that already exists
gets the label just the same, whoever opened it.

**Body follows `.github/pull_request_template.md`** — nothing applies it for you, so open that file and
fill its shape in rather than inventing one.

## 6. Hand back

Which step you did, and the one you stopped before — attended that is the next instruction someone
owes you, so leaving it implicit reads as failure rather than the next step. Plus the commit subject,
the branch, and the base it was cut from, since whoever opens the pull request needs the base and
cannot infer it from the branch, plus `intel/artificial 🤖` for the pull request they open.

Then Aftercare, once the change is pushed: add `intel/artificial 🤖` to the issue, plus `state/has-doc`
if the diff added docs, and `state/has-case` / `state/has-e2e` only if it added those. Leave
`intel/natural 🧐` alone where the issue carries it — `CONTRIBUTIONS.md` → "Authorship labels". Never
set `state/verify` or `state/ready`. Attended, confirm first; unattended, do it as part of the same
trigger and say so in the hand-back.
