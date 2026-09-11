# Using Claude Code with this repo

How Claude Code works: <https://code.claude.com/docs/en/how-claude-code-works>

## Automated setup

One script installs Claude Code and writes your settings, asking for the values you were given.
From the repo root, on macOS, Linux and WSL:

```bash
.agents/onboarding/claude/setup-claude-code.sh
```

On Windows, in PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File .agents\onboarding\claude\setup-claude-code.ps1
```

A value your settings file already holds is offered as the default, so press Enter to keep it. The
file itself is replaced, and the previous one is kept beside it as `settings.json.bak.<timestamp>`.
The next two sections are the same steps by hand.

## Manual setup

### Install Claude Code

macOS, Linux, WSL:

```bash
curl -fsSL https://claude.ai/install.sh | bash
```

Windows PowerShell:

```powershell
irm https://claude.ai/install.ps1 | iex
```

Windows CMD:

```batch
curl -fsSL https://claude.ai/install.cmd -o install.cmd && install.cmd && del install.cmd
```

### Set up Claude Code settings

Create your user settings file — `~/.claude/settings.json` on macOS, Linux and WSL,
`%USERPROFILE%\.claude\settings.json` on Windows:

```json
{
  "env": {
    "CLAUDE_CODE_USE_BEDROCK": "1",
    "AWS_REGION": "us-east-1",
    "ANTHROPIC_DEFAULT_SONNET_MODEL": "<SONNET_PROFILE_ID>",
    "ANTHROPIC_DEFAULT_OPUS_MODEL": "<OPUS_PROFILE_ID>",
    "AWS_ACCESS_KEY_ID": "<AWS_ACCESS_KEY_ID>",
    "AWS_SECRET_ACCESS_KEY": "<AWS_SECRET_ACCESS_KEY>"
  },
  "model": "sonnet",
  "modelPicker": {
    "options": [
      {
        "model": "<SONNET_PROFILE_ID>",
        "behavesAs": "claude-sonnet-5",
        "label": "Sonnet 5 (Inference profile)",
        "description": "EPM-CMBI Sonnet 5 Amazon Bedrock Application Inference Profile for Cost Tracking"
      },
      {
        "model": "<OPUS_PROFILE_ID>",
        "behavesAs": "claude-opus-5",
        "label": "Opus 5 (Inference profile)",
        "description": "EPM-CMBI Opus 5 Amazon Bedrock Application Inference Profile for Cost Tracking"
      }
    ],
    "replaceBuiltInOptions": true
  },
  "effortLevel": "high"
}
```

Replace the placeholders `<SONNET_PROFILE_ID>`, `<OPUS_PROFILE_ID>`, `<AWS_ACCESS_KEY_ID>` and
`<AWS_SECRET_ACCESS_KEY>` with the values you were given.

These settings apply to every project. If you would rather configure Claude Code per project, put the
same JSON in `<repo root>/.claude/settings.local.json` (gitignored) and launch it as
`claude --settings .claude/settings.local.json` — the flag is what makes `modelPicker` take effect,
since Claude Code ignores that key in a project checkout.

## Launch

From the repo root:

```bash
claude
```

## Configure the environment

In a Claude session, ask it to configure the development environment, or invoke the
`configure-dev-environment` skill directly:

> Hey Claude, please configure the development environment

> /configure-dev-environment

You can run it again at any time:

> /configure-dev-environment I don't want to use Playwright MCP for GUI testing, can you please disable it?

*Claude may ask you to relaunch the session* — for example, after it installs an MCP server. Exit the
session and start it again:

> /exit

```bash
claude
```

What it learned about this workstation lands in `.agents/localenv/`, one file per toolchain. That
directory is gitignored and per-developer, so a new machine or checkout runs the skill once.

## What Claude reads in this repo

| Path | What it is |
|---|---|
| `AGENTS.md` | the conventions every session loads — repository layout, development process, anti-patterns. `CLAUDE.md` only includes it |
| `AGENTS.md` in any subdirectory | additions that take precedence inside that subtree |
| `.agents/skills/` | procedures Claude loads on demand, one directory per skill. `.claude/skills/` is a committed symlink to it |
| `.claude/settings.json` | shared harness config: which commands are pre-approved, which ask, which are blocked |
| `~/.claude/settings.json` | your own settings, for every project — the file you created above |
| `.claude/settings.local.json` | your overrides for this repository alone, gitignored |
| `.agents/localenv/` | this workstation's toolchains, gitignored |

**On Windows, check the skills symlink first.** Git can check `.claude/skills` out as a plain text
file holding its target — nothing errors, and Claude simply sees no skills. `.agents/skills/README.md`
has the fix.

## Working on a task

Give Claude the issue number along with the task — it asks for one otherwise, and it will not invent
one:

> Please implement #1234

From there it follows the `implement-task` skill: plan, branch, change the code with the tests and
docs that the change owes, run the checks, hand the diff to a second agent for review, then commit.
Expect it to ask early whether to cut a new branch from the one you are on, and whether to work in a
worktree under `.worktrees/<branch>`.

Skills you can also invoke directly:

| Skill | Use it for |
|---|---|
| `/plan-change` | planning before the first edit, or resuming a plan someone left behind |
| `/verify-changes` | finding and running the checks a change owes |
| `/verify-client-live` | opening a `client/` change in a browser against a real deployment |
| `/commit-changes` | committing, pushing, opening the pull request |
| `/file-issue` | writing up a bug, an enhancement, or a followup found mid-task |
| `/modifying-instructions` | changing what agents read — a convention, a skill, a rule |

Plans for anything larger than a one-file change go to `.agents/plans.local/` (yours, gitignored) or
`.agents/plans/` (shared and committed), each with a ledger beside it recording where the work
stopped. Point a new session at the plan rather than re-explaining the task.

## Models, effort

**opus** — hard tasks, multi-stage ones, ambiguous ones

**sonnet** — everything else

Effort, from fastest to most thorough: low -> medium -> high -> xhigh -> max (or ultracode)

To change the model or the effort, in a Claude session:

> /model

> /effort

## Action confirmation, auto mode

By default, Claude asks before it runs anything — creating a file, filing an issue, and so on.
`Shift+Tab` cycles the permission mode of the session, up to auto mode, where a classifier approves
most actions instead of you.

`.claude/settings.json` already decides this for the commands that come up most: the linters, the
per-module test tasks and the everyday `git` commands run without a prompt; pushing, `npm install`,
the `gh` commands that write and the long builds ask every time; deployment tooling, destructive cloud
commands, force-pushing and reading credentials or keystores are blocked outright, in auto mode too.
Put your own allowances in `.claude/settings.local.json` — the shared file is everyone's.

## Interruption, mid-turn comments

Press `Esc` to interrupt Claude or stop a running command. You can also send follow-up comments
*while* Claude is working, to steer it.

## Context window, sessions

The larger the context, the worse the quality. Claude summarizes the conversation automatically as it
grows, **but prefer starting a fresh session**. Tips:

> Please give me a <short|detailed> prompt to continue working on ... in a fresh session

To start a new session:

> /clear

To resume a previous session:

> /resume

You can name a session, so that it can be found later:

> /rename working-on-issue-1234

A fresh session knows nothing about the previous one. Mid-task, what carries the work over is the
branch, the diff on it, and the plan file with its ledger — so leave those for the next session rather
than keeping the state in your head.
