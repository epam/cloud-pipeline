---
name: checkout-testcases
description: Check the UI test cases against a code changes — an issue, PR, commit, or diff — and add any case the change's functionality is still missing. Use only when explicitly asked to check or write UI test coverage for a change, not as a side effect of an unrelated task; not for running the suite itself.
---

# Checking out UI test cases

A GUI change owes test-case coverage, not just automation — `e2e/gui/AGENTS.md` → "Test cases" keeps
the Selenide suite in step with `docs/testcases/UI/`. Keep the case files themselves in step with
the change.

**Wait to be asked.** Checking or writing test coverage is its own request — never pull it in just
because a diff happens to touch the GUI.

**Find the area.** From the issue, PR, commit or diff, name the page, dialog or flow it changes,
then the folder under `docs/testcases/UI/` that covers it. `GUI_tests.md` indexes every case by
folder, so a known case ID or a keyword search there beats browsing.

**Read what's there before writing anything new.** Every case in that folder whose steps or
expected results describe the changed behavior gets checked against it; flag the ones that now
describe stale behavior rather than rewriting them on sight.

**Check for a gap.** Only functionality with no existing case needs a new one. A change that spans
several folders gets a gap check, and an index update, in each one. A feature with no covering
folder at all is itself a question to ask, not a new folder or `GUI_tests.md` section to invent.

**Offer the plan, then wait.** Before creating a file, list the new cases one per line — proposed
filename, title, and the scenario it covers — rather than folding them into a paragraph. Create
nothing until that's confirmed.

**Match the sibling format.** A `# ` title (`[MANUAL]` only where it can't be automated), a one-line
description of what the test verifies, a `**Prerequisites**` list where the case needs one, the
`| Steps | Actions | Expected results |` table, and an `**After**` note where the test leaves state
behind. Name the file after the issue number, suffixed `_1`, `_2`, ... for several cases from one
issue — whatever the folder's existing files already do.

**Update the index.** Add the new file to `GUI_tests.md`'s table for that folder, and its
`<summary>` test count — hand-maintained, so nothing does this for you.

Hand back a table of existing cases that need updating and what's stale in each, and — once
confirmed and created — the new case(s) with their file paths.
