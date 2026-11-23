---
name: testcases
description: Check, add, update or fix the UI test cases under docs/testcases/UI/. Required for every change that alters what a user sees or can do in the GUI — a client/ change, or an API or other backend change that reaches the GUI — and on its own whenever asked to check or write test cases. Holds which cases a change owes, their format, the index, and the separate branch and pull request they go on. Not for running or writing e2e tests.
---

# UI test cases

## What a change owes

**A change owes test cases when a user can see or do something different in the GUI after it** —
wherever the code is. A new API field the GUI shows, a preference it reads, a permission it checks, a
run state it displays: each one reaches the GUI. Where a change reaches nothing there, say so in the
hand-back; an unstated skip reads as an oversight.

Owed are the cases for **what the change touched**: existing cases it made stale, and functionality it
added or changed that no case covers. That no case covers it today is the gap, not a reason to skip.
The rest of the area's missing coverage is not owed.

## Their own branch and pull request

**Test cases that come with other work never go on that task's branch.** They get their own branch
and draft pull request under the task's issue, and go through the `implement-task` steps from
Workspace onward by themselves. The Plan is "Offer the plan", below.

- **Name the branch per `CONTRIBUTIONS.md` → "Branch naming"**, with the `_testcases` suffix.
- **Cut it from the same base the task's branch was cut from** — never from the task's branch itself.
- **The pull request links the issue and the task's pull request**, and the task's pull request links
  back to it.
- **Once the pull request opens, the issue gets `state/has-case` and a comment** naming the pull
  request and the folder the cases land in on the base branch:

  ```
  Test cases were created by #<pr> and located [here](https://github.com/epam/cloud-pipeline/tree/<base>/docs/testcases/UI/<folder>)
  ```

Asked only to add, update or fix test cases, that is the whole task: it runs the `implement-task`
skill like any other change, and of the rules above the `_testcases` branch name and the issue's
label and comment apply.

## Writing them

**Find the area.** From the issue, pull request, commit or diff, name the page, dialog or flow that
changed, then the folder under `docs/testcases/UI/` that covers it. `GUI_tests.md` indexes every case
by folder, so a known case ID or a keyword search there beats browsing. A change that spans several
folders gets each step below in each one. A feature with no covering folder at all is a question to
ask, not a new folder or `GUI_tests.md` section to invent; unattended, use the closest folder and say
so in the pull request body.

**Read what is there first.** Check every case in that folder whose steps or expected results
describe the changed behaviour, and note what is now stale in each.

**Offer the plan, then wait.** List, one per line, each stale case with what to fix, and each new case
with its proposed filename, title and the scenario it covers. Attended, change nothing until that is
confirmed. Unattended, write them and put the list in the pull request body.

**Name the file after the original task's issue number** — `<issue>.md`, or `<issue>_1.md`, `<issue>_2.md`, … for
several cases. Several cases from one issue go into a subfolder named after it, optionally with a few
words added — `4599/`, `4608_capacity_retries/` — inside the feature subfolder where one covers them,
as in `Data_storages/NFS_mount/4599/`. This holds even where older cases in the folder sit flat. A
task with no issue: attended, ask what to name the files; unattended, use the task branch's
description. Never guess a number.

**Match the files beside it.** A `# ` title, prefixed `[MANUAL]` where the case cannot be automated and
`[Negative]` for a negative scenario; a description of what the test verifies, one line or a
`Test verifies that:` list; then, where the case needs them, `**Prerequisites**:` and
`**Preparations**:` lists; the `| Steps | Actions | Expected results |` table; and an `**After**:`
note where the test leaves state behind.

**Update the index.** `GUI_tests.md` holds one section per top-level folder, and a case in a subfolder
goes into its top-level folder's table, beside the other rows from that subfolder. A row is the linked
case ID, the title, and `:white_check_mark:` under `Automated` only where an `e2e/gui` test names the
case ID in its `@TestCase`. Then fix both counts in the section's `<summary>`,
`(N tests, M automated)`. The file is hand-maintained: nothing updates it for you.

## Hand back

The stale cases and what changed in each, the new cases with their file paths, and the branch and pull
request they went on.
