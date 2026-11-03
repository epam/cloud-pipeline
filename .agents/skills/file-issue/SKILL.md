---
name: file-issue
description: Create a new GitHub issue in this repository — a bug report, an enhancement request, or a followup/sub-issue found while implementing another task. Use when asked to file, open, or write up an issue, not when implementing one that already exists.
---

# Filing an issue

Pick the file in `.github/ISSUE_TEMPLATE/` whose `about:` matches what you are filing, and fill in its
sections rather than a shape of your own. Skip a section the issue genuinely has nothing for rather
than leaving the placeholder text in. The title is yours to write.

**Labels** — `gh label list` for the current set. Pass the template's `kind/*`, `intel/artificial 🤖`
(never `intel/natural 🧐`, whatever the template carries — `CONTRIBUTIONS.md` → "Authorship labels"),
and `sys/<area>` / `cloud/<provider>` where the issue clearly names one. Leave `priority/*`, `goal/*`,
`kb`, `kind/duplicate`, `kind/wontfix`, `kind/question` and `dependencies` unset unless told directly
to add them.

A followup or sub-issue found mid-task uses the matching template too, linked to its parent —
`CONTRIBUTIONS.md` → "Task entity properties" has the convention.

Attended, confirm the title and labels with whoever asked before creating it. Unattended, create one
only where the trigger said to file it, and give the issue number and URL in the hand-back.
