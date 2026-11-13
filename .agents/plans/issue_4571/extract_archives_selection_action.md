---
issue: 4571
branch: issue_4571/extract_archives_on_upload_gui
base: java21_migration @ 9d34ca82e7
area: gui
author: Cloud Pipeline Agent
model: opus
cadence: checkpoint
location: shared
---

# Extract archive from the selection, and notify when an extraction finishes

Follows `extract_archives_real_api.md`. Five changes, one of them a reversal of a decision made
there.

## Context / assessment

Extraction is reachable only through the upload pop-up today. This adds a second entry point — the
storage browser's selection dropdown, for archives already in the storage — and closes the loop by
notifying when an extraction finishes, which the previous plan deliberately left out.

What the code already gives us, verified:

- `fetchActiveTasks(type)` omits `type` when it is falsy, so one call covers both task kinds.
- `notifyListeners(task)` fires on a SUCCESS/FAILED transition, and `async-task-notifications.js`
  subscribes to it. Tracking an extraction is therefore all that stands between us and a toast.
- `renderSelectionActionsButton` already supports a per-action `hint` rendered as an icon beside the
  item, and antd's disabled menu items only restyle colour — no `pointer-events: none` — so a
  disabled item can still own a hover target.
- **The z-index trap is real:** antd 2.13.14 puts `@zindex-popover` at 1030 and `@zindex-dropdown`
  at 1050, so a Popover inside the selection dropdown renders *behind* it.
  `pipelines/launch/form/utilities/capability.js:126` already solves this with
  `overlayStyle={{zIndex: 1071}}` on a popover in a dropdown item. Reuse that number rather than
  inventing one.

## Steps

### 1 · `deleteSourceOnSuccess` back to `false`

`CurrentUserAsyncTasks.startArchiveExtraction`. `overwrite: true` stays. Reverses the decision in
`extract_archives_real_api.ledger.md`; the manual note about the archive being deleted goes with it,
and so does the warning in the release note.

### 2 · Disable "Upload and extract" when the batch would exceed the allowance

Today the pop-up only disables at the hard cap, and merely *warns* when the allowance covers part of
the batch. One archive is one task, so a batch of three with one slot free cannot be honoured either:
the button is disabled in that case too, with the same `Alert` carrying the reason.

`UploadButton`: fold `extractionShortfallWarning` into one `extractionDisabledReason`, read by both
the button's `disabled` and the alert. The partial-queue path in `handleExtractArchives` stays — it
is still the safety net if the allowance changes between the check and the click.

### 3 · "Extract archive" in the selection dropdown

`data-storage/index.js`, `renderSelectionActionsButton`: a new action under `downloadArchive`.

- `available` — the storage supports archives (`archiveStorageTypeSupported`), the user may write to
  the current folder (extraction writes), and the selection holds at least one archive **file**.
  Unlike "Download archive" it is offered for a single file, which is the common case.
- No modal: the click queues extraction for every selected archive, through the same
  `handleExtractArchives` the upload path uses, so the destination rule
  (`folder/a.tar.gz` → `folder/a/`) and the toast are shared.
- `disabled` plus an info `Popover` beside the title when the selection would exceed the allowance,
  explaining why. `overlayStyle={{zIndex: 1071}}`, `trigger={['hover']}`.
- The allowance is checked when the selection changes, not on a poll: the dropdown is transient, and
  a stale answer only ever costs one refused click that the server would refuse anyway.

Needs a selection→archive-paths helper beside `archivePathsFromSelection`, filtering to files whose
name is an archive.

### 4 · Notify when an extraction finishes

- `startArchiveExtraction` calls `trackTask`, as `startArchiveDownload` does.
- `restore()` syncs without a type, so a task queued on another machine is picked up whatever its
  kind.
- `async-task-notifications.js` branches on type: the extraction message names the archive and does
  **not** offer a download link, since `consume` throws for it. It also must not reuse
  `archiveNameOf`, which reads `params.archiveName` — an extraction has `params.sourcePath`.

### 5 · Docs, release note, UI test cases

The manual's upload section loses the deletion note and gains the selection action; the Storage
tasks section gains the completion notification for an extraction. UI test cases go on
`issue_4571/extract_archives_on_upload_gui_testcases` (PR #4719) — `4571_4` and `4571_9` both
describe behaviour steps 1 and 2 change.

## Checks

`npm test`, `npm run lint`, `npm run stylelint`, `npm run build` from `client/` under Node 14, then
the `verify-client-live` skill — this changes a dropdown, a disabled state and a notification.
