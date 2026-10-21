---
name: implement-task
description: Start here before the first edit of any file — a one-liner and a docs change included. Holds the steps a task passes through, the issue number and branch it needs, the tests and docs it owes, and where the branch ends up. The plan-change, verify-changes and commit-changes skills are steps within it.
---

# Implementing a task

Ten steps. Every task passes all of them, and most collapse to a sentence on a small change —
**Verify and Diff review never collapse.** They are internal structure rather than a script to
recite: name a step in prose where you need to ("the Verify step"), never a bare number, and don't
narrate the walk through them at whoever asked.

| # | Step | Leave it when | Mechanics |
|---|---|---|---|
| 0 | Intake | you can state the task, the area(s) it touches, and its issue number without guessing at any of the three — **Issue number**, below | `gh issue view` |
| 1 | Orient | you can name the files that will change *and* the ones that constrain them | the subtree's `AGENTS.md`, the module's `README.md` |
| 2 | Plan | **attended:** the person asking has agreed to it · **unattended:** it is written down, to go in the pull request body | `plan-change` skill |
| 3 | Workspace | the branch exists, on a base you established rather than assumed, and you are working where you were told to — **Workspace**, below | `git worktree add` |
| 4 | Implement | the change is complete, including the tests and docs it owes — **Tests** and **Docs**, below | `CONTRIBUTIONS.md` |
| 5 | Verify | the checks you owe for everything the diff touches, and everything it reaches, have run — or are recorded as skipped, and what was missing | `verify-changes` skill |
| 6 | Diff review | you have read your own diff (no debug leftovers, no unrelated files, nothing outside the scope agreed at Plan), and an agent that did not write it has reviewed it — **Fresh-eyes review**, below | a fresh subagent |
| 7 | Publish | **attended:** you were told to · **unattended:** the trigger is the instruction, and the pull request is a **draft** | `commit-changes` skill |
| 8 | Review | every comment answered, checks re-run, pushed to the same pull request — never a second one | `gh api .../pulls/{n}/comments` (inline review comments), `gh pr checks` |
| 9 | Aftercare | the issue's labels match reality — its `state/*` one, and `intel/artificial 🤖` — and the branch has gone where it was meant to — **Handing the branch back**, below | `gh issue edit` |

**Plans live under `.agents/plans.local/` (gitignored) or `.agents/plans/` (shared), each with a
`.ledger.md` beside it.** A branch you did not create may carry some, and the ledger is the only record
of where that work stopped — so read them at Intake rather than re-deriving the task.

**Unattended when you cannot get an answer before the next step** — no user in the loop, empty stdin,
or `CI`/`GITHUB_ACTIONS` set. Where a step below would have you ask, the unattended answer is given
alongside it; where none is given, stop and report rather than guess. A stopped task is a useful
result; a wrong base or an invented issue number is not.

Skip only what this table names; an unstated skip is still an oversight.

| Kind | May skip |
|---|---|
| Question / read-only | this whole skill |
| Instruction, skill or comment text only — no product behaviour | tests, live browser, user-manual docs |
| `client/` with no user-visible pixel, route or client state | the `verify-client-live` skill |
| Small, one module, no new behaviour, attended | a plan file |

**The hand-back is what you say when the work is done**, and it is the same four things whether the
reader is a terminal or a pull request body: **what changed**, and why where the diff doesn't say so;
**checks**, which ran, what they said, which were skipped and what was missing; **assumptions**, every
point where you decided something the request left open; and **left out**, anything in scope you did
not do. An empty "left out" is a claim about the work rather than a formality — make sure it is true.

## Issue number

**A task has an issue number, and Intake is where you get it.** Attended, ask for it whenever the
request arrives without one, and ask before the branch exists — the branch name embeds the number, and
renaming a branch that already carries commits is worse than a question.

If the answer is that no issue exists, **say that the task needs one and offer to file it** (the
`file-issue` skill): the `state/*` labels, the branch, the pull request and the verification that
follows all key off the number, so work without one is invisible to everyone not in the room. Only once
the person declines does the change proceed unnumbered, on a `feature/` or `fix/` branch, and the
hand-back records that as an assumption.

Unattended, the trigger names the number or states that there is none. Neither: stop and report.

**Never invent a number**, and never reach for a plain descriptive subject to sidestep the question — a
number belonging to an unrelated issue silently attaches your change to someone else's work.

## Workspace

**A task gets its own branch.** What varies is whether you ask first.

| You are on | Attended | Unattended |
|---|---|---|
| `develop` matched exactly, `release/*`, `stage/*` | new branch, no question — nothing gets committed on a shared branch | new branch |
| any other branch | **ask** whether to cut a new branch from it; if the answer is no, keep working on it | new branch |

**The base is an exact ref name, or the current `HEAD`.** A version like "0.16", a release line or a
description is not one — ask, or stop and report where you cannot. Never fall back to `develop`: it is
the base only when it is what is checked out.

`CONTRIBUTIONS.md` → "Branch naming" owns the name. The rule to get right is that the area is a
`_<area>` **suffix and never a second path segment**: `issue_4538/tags/<area>` makes `issue_4538/tags`
permanently unusable. Given a name, use it exactly.

**Where that branch already exists and it is this task's branch, it is the branch** — a re-run continues
on it rather than inventing a `-2`. Where you cannot tell whose branch an existing one is, treat it as
someone else's and pick another name.

Once the branch exists, add `state/underway` to the issue if it doesn't carry it already.

### Worktree

**A new branch is what gets a worktree.** **Attended, ask whether to work in one**, together with the
branch question rather than as a second round; staying in this checkout is a legitimate answer, not the
default you assume. Unattended, always use one.

**Its path is `.worktrees/<branch>`**, which is gitignored. A vendor's own worktree action picks its own
location and names the branch itself, so use it only if it can be pointed at that path — the name is
`CONTRIBUTIONS.md`'s to decide.

**Copy or link the gitignored working state a check needs** — installed dependencies, build caches,
`.agents/localenv/`, local environment files — from the main checkout instead of reinstalling it. Copy
an environment file without opening it: those are among the files agents must not read.

Remove the worktree once the branch has been handed back.

## Tests

**Every code change needs a test.** Test every file you changed, not the easiest one: if you move
logic into a new helper and edit the files that call it, the helper's test covers the helper, and the
callers still need their own. If nothing in the module is tested at that level today, that is the gap,
not a reason to skip.

Where the change is testable but the module has no runner for it, **the runner is part of the work.**
Attended, say what is missing and ask whether to stand it up, since it is larger than the change that
revealed it and may be wanted as its own task; where the answer is no, the test you could not write is a
stated skip in the hand-back. Unattended, make the change and record the missing runner in the pull
request body as a skip. Never stand one up silently, and never let its absence quietly cost the test.

Where the thing you changed has no behaviour to test at all, say so in the hand-back. An unstated skip
reads as an oversight, and stating it is the only way anyone can tell the two apart.

The test you owe is for *what you changed*, never for the area's accumulated coverage debt: broadening
past that is scope creep, and Diff review should catch it.

## Docs

**A user-visible change updates the manual page covering it** — under `docs/md/manual/`, as its own
`Docs: <what>` commit on the same branch. **That the functionality is undocumented today is not a reason
to leave it undocumented**; it is the gap itself. Where no page covers it, attended, ask which page it
belongs in or whether to add one; unattended, add the page and say so in the pull request body. A new
page also goes into the nav in `docs/mkdocs.yml`, which lists every page by hand — one that isn't there
is not in the built manual.

**Where the page already describes the behaviour correctly** — a fix restoring what the manual says all
along — the change owes nothing but the sentence saying so. That is the third answer, and reaching it is
not the same as deciding a change needs no documentation.

The release note a change owes is `CONTRIBUTIONS.md` → "Documenting". The notes are kept per version
under `docs/md/release_notes/`.

A change with no user-visible surface — internal refactoring, build wiring, agent instructions — owes
neither. Say so in the hand-back rather than leaving documentation unmentioned: deciding on your own
that a change needs none, and staying quiet about it, is what this rule exists to catch.

## Fresh-eyes review

**The diff is reviewed by an agent that did not write it** — spawn a subagent with its own context,
never a fork, and hand it the diff rather than the conversation.

Give it the range — the branch against its base, or the pull request number — and tell it to read the
`AGENTS.md` covering every path in that diff. **Give it no checklist of conventions**; those files are
the checklist.

Where the session cannot spawn one, review the diff yourself as its own pass, reading those
`AGENTS.md` files rather than trusting your memory of having written it, and say in the hand-back that
no second agent saw it — weaker, not a skip.

It reports findings back as a **short list** (`none` is allowed). The implementing agent decides what
to act on, and acting on one owes the Verify step again.

Attended, this runs before the branch is published, and again after a push that changes the diff.
Unattended, once before the draft pull request opens.

## Handing the branch back

Attended, once the work is done and its checks pass, **ask what happens to the branch**: merged into the
one you cut it from, or left as it is for a pull request. Where merging is asked for and the new branch
has never been pushed, **rebase it onto that branch and fast-forward** — linear history, and nothing
published gets rewritten. Once the branch has been pushed, merge instead: rewriting published history
invalidates the review comments already on it, and force-pushing is out of the question either way.

Unattended, do what the task asked for and nothing more. No instruction means the branch stays as it is,
with its draft pull request — finishing the work does not imply merging it.

Then the issue's labels, which the `commit-changes` skill → "Hand back" covers.
