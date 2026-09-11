---
name: plan-change
description: Plan the work before you edit — its phases, the model and effort each takes, and whether it is worth a plan file with a ledger beside it. Also when resuming a plan, or told to continue one.
---

# Planning a change

A plan is two files: the plan, and a **ledger** beside it recording what actually happened. **Most tasks
need neither** — state the plan, get a yes, start editing; nothing goes to disk. Write the files whenever
you are asked, and when the work spans more than one session, branch or pull request. **Resuming beats
re-deriving**, so look first: the prompt normally names the plan, and on a bare "implement the plan"
search both directories below, asking which if several match.

| Path | Tracked |
|---|---|
| `.agents/plans.local/<key>/<slug>.md`, its `<slug>.ledger.md` beside it | no — the default |
| `.agents/plans/<key>/<slug>.md`, its `<slug>.ledger.md` beside it | yes — once shared |

`<key>` is whatever identifies the work — `issue_<n>`, the branch name (its `/` nests as a path), or a
short feature slug — and it is a **directory, not a filename**: one key can carry several plans, each
with its own ledger. **Share one** — `git mv` both files into `.agents/plans/`, then `git add` — when it
outlives the branch or someone else must read it; attended, ask first. Unattended, leave it local: the
plan goes in the pull request body. **Never retire a shared plan on your own initiative**; a merged pull
request does not mean the issue is done.

## The plan

Frontmatter, then **phases, in order.** The body is otherwise whatever the change needs.

```yaml
---
issue: 4538                                    # or `none`, with why in the body
branch: issue_4538/restore_storage_prompt_gui  # exact, per CONTRIBUTIONS.md
base: develop-agentic @ 7b2cc2ccce             # `tbd` until Workspace establishes it
area: gui                                      # say in the body which phases leave it
author: Cloud Pipeline Agent (Claude Opus 5)
model: claude-opus-5                           # `none` when a human wrote it
cadence: checkpoint                            # continue · checkpoint · delegated — see below
location: local
---
```

All eight are required **of a plan you write** — `tbd` and `none` are answers, a missing key is not.
**A plan a person wrote owes none of this:** read it as it is, and never restructure it to fit.

**Each phase opens by advising a model and effort** — `model · claude-sonnet-5 · effort medium —
mechanical, one file per test target`. Advice, not a decision: the cheapest tier that plausibly does it,
effort one of `low`, `medium`, `high`, `xhigh`, `max`.

**Amend the plan in place** when a decision makes a later phase's text wrong, and say so in the ledger:
the plan stays the thing you can execute, the ledger stays why it changed. The plan holds what you
assumed while planning; the ledger holds what working proved.

**How it gets executed is part of it, so ask while writing it**, attended, and record the answer in
`cadence:`. Every mode is one commit per phase, plus the ledger's own commit last.

| `cadence:` | |
|---|---|
| `continue` | the phases run back to back, no stop between them |
| `checkpoint` | after each phase, report and **stop**, ending with a one-line prompt to resume: `continue <plan path> phase <n>` |
| `delegated` | one sub-agent per phase, launched one at a time, each on a fresh context; no stop |

**Unattended, use `delegated`** — no asking, no stopping to report. A delegated sub-agent gets the plan's
path and its phase number and nothing else, since the plan is the brief; it takes the phase through
Verify and makes its code commit, and **the supervisor writes the ledger row and commits it**.

**Nothing in the diff refers to the plan** — no code comment, commit message, doc line or release note
naming a plan file, a phase, or a plan's numbering. A local plan is gitignored and the code's readers
never see either one, so write the reason itself (`// storage IDs are stable across a restore`), never
where it was decided (`// per phase 2 of restore-prompt.md`).

## The ledger

```markdown
| Phase | Name | Status | Model / effort | Commit | Notes |
|---|---|---|---|---|---|
| 1 | schema | done | claude-sonnet-5 · medium | a1b2c3d | |
| 2 | service and DAO | in progress | claude-opus-5 · high | | split from phase 1 |
```

One per plan, and **every phase gets its row when the ledger is created** — `pending`, with **Model /
effort** prefilled from the phase's advice, then corrected to what actually ran. Status is `pending`,
`in progress`, `done` or `blocked`. **Commit** is empty until the phase has one, then its **SHA** (the
last, where a phase took several) and/or a **`#<n>`** pull request reference; the row lands as its own
commit, last in the phase, since a commit cannot contain its own SHA. **Notes** is a phrase, usually
empty.

Below the table, one line each for what a reader would otherwise guess — the set is open:

```
assumption · storage IDs are stable across restore, per issue comment 4538#12
decision · base is the current HEAD, agreed attended at Workspace
deviation · used the existing modal, not a new page — see a1b2c3d
skip · npm absent, client lint not run
blocked · client lint crashes on pre-existing code; each file this diff touches linted individually
```

**One line per note, never a paragraph, and the whole file stays short.** A ledger records where the work
stopped and what a resumer could not work out alone, not what was done: the commits hold that, so point at
them rather than restate them, and a note that will not fit on one line belongs in a commit message.
A check that passed needs no line — `done` claims it; one that failed needs its `skip` or `blocked` line
even where the phase is otherwise complete.
