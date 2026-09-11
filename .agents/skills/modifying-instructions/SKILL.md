---
name: modifying-instructions
description: Required before adding or changing anything an agent reads — a convention in AGENTS.md, a procedure as a skill, a pattern-scoped rule. Holds which of those a thing belongs in, the wiring each needs before it loads at all, and what to keep out of them.
---

# Modifying agent instructions

## Where a thing goes

| The thing you have | Where |
|---|---|
| A fact or convention, true for a whole subtree | `AGENTS.md`, or `<dir>/AGENTS.md` |
| A multi-step procedure, needed occasionally | a skill — `.agents/skills/<name>/SKILL.md` |
| A constraint tied to a file *pattern* no directory can express | `.claude/rules/`, plus a vendor twin per client |
| A subagent definition | `.claude/agents/` — Claude-only, so prefer a skill where the task allows |
| A script a client invokes automatically | a hook, wired in `.claude/settings.json` — Claude-only, and never one that writes to a file |
| A script an agent runs deliberately | `scripts/` inside the skill that runs it |
| Setup and usage detail for humans | the module's `README.md` |
| The team's own process — stages, labels, naming, what a change owes | `CONTRIBUTIONS.md` |

**`CONTRIBUTIONS.md` is written for people.** Agents read the conventions it holds and point at it, and
an agent-only rule never goes into it — not the steps an agent walks, not what it asks for, not what it
does unattended. Those live in `AGENTS.md` or in a skill, and `CONTRIBUTIONS.md` describing them back is
a second copy that will drift.

**Keep vendor mechanics out of it too** — no per-client resolution rules, no `.claude/`, `.cursor/` or
`.github/` paths, no procedure for one client, no table of what each supports. Naming a client to make
a sentence concrete is fine; anything that has to track the tooling as it changes belongs in a skill,
with `CONTRIBUTIONS.md` pointing at it.

`AGENTS.md` loads on every task in the repository; a skill loads only on the tasks matching its
description. So prefer a skill whenever the content is a procedure, and in `AGENTS.md` treat deletion
as the default: cut anything derivable from the file it describes, any list that has to track
something else, and anything about the document rather than the repository. When you cut something a
reader still needs, **move** it — the failure to avoid is a fact that lived in one place and now lives
in none.

## AGENTS.md

Root `AGENTS.md` is the source of truth for conventions. Every other instruction file points at it or
scopes it to a pattern; none of them restate it.

Any directory may carry one, and they nest. A module earns one when it **departs from the repository
default** — a live database, a different test runner, a suite that only partly runs. Most Java modules
need none. Keep the root file's claims about a subtree thin: if the two disagree the subtree file wins,
which makes the root copy a liability.

**Every `AGENTS.md` needs a `CLAUDE.md` beside it** whose entire contents are the import:

```
@AGENTS.md
```

Claude Code does not read `AGENTS.md`, so without the twin the file is silently invisible — no error,
no warning. This is the most common way to break this setup. The other vendors read `AGENTS.md` and
`<dir>/AGENTS.md` natively and need nothing per directory.

## Skills

`.agents/skills/` is the source of truth: the only repository path Codex scans, and read by Cursor and
Copilot as well. Claude Code reads only `.claude/skills/`, which is a **symlink to that directory**, so
a new skill is visible the moment you create it — there is no per-skill linking step. The link itself
is committed; recreate it only if a checkout loses it:

```bash
ln -s ../.agents/skills .claude/skills
```

Frontmatter takes **only** `name`, `description`, `license`, `compatibility`, `metadata` and
`allowed-tools`; anything else is a hard error when a skill is packaged. `name` must equal the
directory name.

**The `description` is the whole index.** Until the skill loads it is all any agent sees, so it is a
routing decision rather than a summary: name the situations that should trigger it, in the words a
task would arrive in. One that describes the contents but not the occasion never loads.

Keep `SKILL.md` short — a skill that must be read in full before its first step is too long. Write
what this repository does differently, and nothing else: not how git, npm or gradle work, not the
reasoning behind a rule the agent will follow anyway, and not what someone else's automation runs.

## Pattern-scoped rules

**A vendor rule scoped to a directory is redundant, and there are none here for that reason.** Cursor
and Copilot both resolve `<directory>/AGENTS.md` themselves — Cursor combining parent into child,
Copilot taking the nearest — so a `.mdc` or `.instructions.md` pointing at one buys nothing and
becomes a second copy to keep true. The whole vendor layer is two always-applied pointers at root
`AGENTS.md`: `.cursor/rules/00-agents.mdc` (`alwaysApply: true`, no `globs:`) and
`.github/copilot-instructions.md`. Copilot's nearest-wins resolution can leave the root file out of
context when a subdirectory has its own, which is what those two guard against.

What earns a pattern-scoped rule is a glob **no single directory can express** — one crossing sibling
trees, or matching by extension across the repository. Check first that the nearest common ancestor
directory could not carry it as an `AGENTS.md`; usually it can. The three clients spell the field
differently, so write the prose once in `.claude/rules/` and make the others pointers:

| File | Field |
|---|---|
| `.claude/rules/<name>.md` | `paths:` — a YAML list of globs |
| `.cursor/rules/<nn>-<name>.mdc` | `globs:` plus `alwaysApply: false` |
| `.github/instructions/<name>.instructions.md` | `applyTo:` — one quoted glob, comma-separated for several |

## The secret blocklists

`AGENTS.md` names the paths agents must not read. Two files enforce it and have to track that list,
both in gitignore pattern syntax: `permissions.deny` in `.claude/settings.json`, and `.cursorignore`.
Neither format supports negation, which is why the rule is a **naming convention** — `.env`,
`.env.local`, `.env.<environment>.local` — rather than a list with `.env.example` carved back out. A
secret named outside those shapes matches nothing, so extending protection means adding a pattern to
both files rather than documenting a filename. Those two stop a file being read; `.gitignore` carries
the same shapes to stop one being committed, which makes it a third place to extend.

## Verify

```bash
.agents/skills/commit-changes/scripts/verify-docs.sh
```

It checks the paths and commands these files name against the repository, that every `AGENTS.md` has
its twin holding the import, and that every skill would load. It cannot check prose against reality —
a stale paraphrase passes, which is why a pointer beats a copy.
