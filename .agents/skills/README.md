# Agent skills

Skills for this repository, in the [Agent Skills](https://agentskills.io) open format. A skill is a
directory holding a `SKILL.md` — instructions an agent loads **on demand**, when a task matches its
`description`. `AGENTS.md` and `.claude/rules/` load on every session instead; put facts and
conventions there, and multi-step procedures here.

`.agents/skills/` is the vendor-neutral discovery path and the source of truth. Claude Code reads only
`.claude/skills/`, which is a symlink to this directory — so a new skill needs no linking step at all.
The link is committed; if a checkout loses it:

```bash
ln -s ../.agents/skills .claude/skills
.agents/skills/commit-changes/scripts/verify-docs.sh           # checks it
```

**On Windows the link is what breaks.** Git checks a symlink out as a plain text file holding its
target unless it is allowed to create real ones, and nothing errors — the skills are simply not found,
and the check above reports the path as "a regular file, not a symlink". Enable symlink support once,
then replace the file:

```
git config --global core.symlinks true
del .claude\skills
git checkout -- .claude/skills
```

Creating symlinks needs Developer Mode or an elevated shell. Without either, a directory junction is
the fallback — run it from the repository root, since a junction stores an absolute path:

```
mklink /J .claude\skills .agents\skills
```

Git then reports `.claude/skills` as locally modified, because a junction is not the symlink the index
records. Nothing else in the repository depends on symlinks.

Adding or editing any of this is itself covered by the `modifying-instructions` skill, which owns the
layout, the `SKILL.md` format, the mandatory `CLAUDE.md` twin and the pattern-scoped rule files.
