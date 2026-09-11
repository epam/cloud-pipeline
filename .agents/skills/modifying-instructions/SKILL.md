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
| A constraint tied to a file *pattern* no directory can express | a rule — one file per vendor, sharing a stem |
| A script an agent runs deliberately | `scripts/` inside the skill it supports; others may run it too |
| Setup and usage detail for humans | the module's `README.md` |
| The team's own process — stages, labels, naming, what a change owes | `CONTRIBUTIONS.md` |

**Behaviour is vendor-neutral.** Skills and `AGENTS.md` name capabilities ("the session's browser
tool", "a fresh agent", "if you cannot ask"). Vendor commands, config paths and permission files
belong only in this skill and in the pointer files it names.

**`CONTRIBUTIONS.md` is written for people.** Agents read the conventions it holds and point at it. An
agent-only rule never goes into it — not the steps an agent walks, not what it asks for, not what it
does unattended, not a table of what each vendor supports.

Claude-only surfaces that have no portable equivalent — `.claude/settings.json` allow/deny/ask,
`.claude/agents/`, Claude hooks — stay in `.claude/` and are **extra enforcement**, not the spec.
Prefer a skill over a Claude subagent definition.

`AGENTS.md` loads on every task in the repository; a skill loads only on the tasks matching its
`description`. Prefer a skill whenever the content is a procedure. In `AGENTS.md` treat deletion as
the default: cut anything derivable from the file it describes. When you cut something a reader still
needs, **move** it.

## What each vendor actually does

**Vendor** is the tool an agent runs inside — Claude Code, Cursor, Copilot. Copilot is two of them:
in the editor, and **on GitHub**, assigned to an issue or a pull request. They read the same files
but index different ones, so the column below says which. `client` in this repository is the GUI
module and nothing else; never use that word for a tool.

Where a vendor has no equivalent, the cell is empty — never invent a file it cannot enforce.

| Concern | Shared (the spec) | Claude Code | Cursor | Copilot |
|---|---|---|---|---|
| Conventions | `AGENTS.md`, nested the same way | `CLAUDE.md` twin (required; Claude does not read `AGENTS.md`) | native; combines parent into child. `.cursor/rules/00-agents.mdc` keeps the root file in play | native, nested files included, plus `.github/copilot-instructions.md` repository-wide |
| Skills | `.agents/skills/` | symlink `.claude/skills` → that directory | native from `.agents/skills/` | native in the editor; **on GitHub, no skill index** — only a file `AGENTS.md` or `copilot-instructions.md` told it to open |
| Deny-read (repo files) | `AGENTS.md` list + `.gitignore` | `permissions.deny` `Read(...)` | `.cursorignore` | none beyond the prose |
| Deny-read (`~/` credential stores) | named in `AGENTS.md` | `permissions.deny` `Read(~/...)` | not applicable (those paths are outside the workspace) | none |
| Deny-shell (`pipectl`, force-push, …) | `AGENTS.md` anti-patterns | `permissions.deny` `Bash(...)` | session sandbox; **not** a repo file | none |
| Browser | `verify-client-live` | Playwright MCP *if already configured* | the IDE browser MCP, if the session has it | often none → skip |
| Fresh review | a new agent, given the diff, not the author's conversation | `/code-review` if that command exists | a fresh subagent | Copilot code review on the PR, otherwise a declared self-review |
| Unattended | you cannot get an answer before the next step | same | same | same |

## AGENTS.md

Root `AGENTS.md` is the source of truth for conventions. Every other instruction file points at it or
scopes it to a pattern; none of them restate it.

Any directory may carry one, and they nest. A module earns one when it **departs from the repository
default** — a live database, a different test runner, a suite that only partly runs. Most Java modules
need none. Keep the root file's claims about a subtree thin: if the two disagree the subtree file wins.

**Every `AGENTS.md` needs a `CLAUDE.md` beside it** whose entire contents are the import:

```
@AGENTS.md
```

A script writes and stages the twin — with no arguments, for every `AGENTS.md` in the working tree;
given directories, only those:

```bash
.agents/skills/modifying-instructions/scripts/claude-md-twins.sh
.agents/skills/modifying-instructions/scripts/claude-md-twins.sh client/test
```

Write the `AGENTS.md` first: that one is yours. A `CLAUDE.md` already holding something other than
the import is reported and left alone, since replacing it would throw away what a person wrote.

## Skills

`.agents/skills/` is the source of truth. The `.claude/skills` link is committed, and
`.agents/skills/README.md` has the fix where a checkout loses it. A skill that must reach Copilot on
GitHub is named from `AGENTS.md` or `.github/copilot-instructions.md`, as a file to open.

Frontmatter takes **only** `name`, `description`, `license`, `compatibility`, `metadata` and
`allowed-tools`; anything else is a hard error when a skill is packaged. `name` must equal the
directory name.

**The `description` is the whole index** for vendors that index skills. One that describes the
contents but not the occasion never loads.

Keep `SKILL.md` short. Write what this repository does differently.

## Pattern-scoped rules

What earns one is a glob **no single directory can express** — crossing sibling trees, or matching by
extension. Anything one directory covers belongs in that directory's `AGENTS.md`, which says the same
thing to every vendor, so check the nearest common ancestor first.

**One rule is three files sharing a stem, each carrying the same prose** — `client-components.md`,
`client-components.mdc`, `client-components.instructions.md`. Write all three yourself; where one is
missing the rule is off for that vendor. Only the glob field differs:

| File | Glob field |
|---|---|
| `.claude/rules/<name>.md` | `paths:` — a YAML list |
| `.cursor/rules/<name>.mdc` | `globs:`, plus `alwaysApply: false` |
| `.github/instructions/<name>.instructions.md` | `applyTo:` — one quoted glob, comma-separated for several |

In `.cursor/rules/` an `<nn>-` prefix marks an always-applied pointer at `AGENTS.md` instead, and that
file stands alone.

## The secret blocklists

`AGENTS.md` names the paths agents must not read. Enforcement is extra and incomplete:

- **Repo-relative** shapes (`.env`, `.env.local`, `.env.<environment>.local`, `secrets/`, keystore/key
  suffixes) go in `permissions.deny` in `.claude/settings.json`, `.cursorignore`, and `.gitignore`.
- **Home credential stores** (`~/.ssh`, `~/.aws`, …) stay in `AGENTS.md` and in Claude `Read(~/...)`
  denies. They do not belong in `.cursorignore`.

Neither deny-read format supports negation, so the rule is a **naming convention** rather than a list
with `.env.example` carved back out. Extending protection means adding a repo-relative pattern to all
three files, or a `~/` path to `AGENTS.md` and Claude's deny list.

Shell denies live in `AGENTS.md` anti-patterns for every vendor. Claude may *also* list them under
`permissions.deny`.

## Verify

```bash
.agents/skills/modifying-instructions/scripts/verify-instructions.sh
```

It checks the gradle modules these files name against `settings.gradle`, that every `AGENTS.md` has
its Claude twin, that every shape in `.cursorignore` is also denied to Claude Code and gitignored, that
every rule stem exists in all three vendor directories, and that every skill would load. CI runs the
same command from `.github/workflows/agent-instructions.yml`.
It cannot check prose against reality: a stale paraphrase passes. Point rather than copy.
