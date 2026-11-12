---
name: Upload-and-extract on the real async-task API
overview: Move the extract-archive GUI next to the download-archive GUI, replace its mock with the real POST /async-task/create call for UNPACK_ARCHIVE, and make the Storage tasks table show and filter the task type so an extraction is visible there. The API verification below found four mismatches between what the GUI sends today and what the backend accepts, and one blocker outside the GUI - no worker runs an UNPACK_ARCHIVE task.
todos:
  - id: verify-worker
    content: Decide what to do about the missing UNPACK_ARCHIVE worker in storage-async-task-service - build it, or ship the GUI knowingly queueing tasks that never run
    status: pending
  - id: move-files
    content: Move extract-storage-archives.js/.test.js to pipelines/browser/data-storage/utils/extract-archives.js, mirroring the download-archive move
    status: pending
  - id: task-type-constant
    content: Add UNPACK_ARCHIVE to ASYNC_TASK_TYPES and the per-type helpers in models/asyncTask/statuses.js
    status: pending
  - id: real-call
    content: Replace the extractArchives mock with AsyncTaskCreate, one UNPACK_ARCHIVE task per archive, and drop the injectable seam the mock needed
    status: pending
  - id: quota
    content: Handle the per-user active-task quota for a batch of N archives - check before queuing and report what was not queued
    status: pending
  - id: type-column
    content: Add the Type column to the Storage tasks table and stop hard-coding type DOWNLOAD_ARCHIVE in the filter body
    status: pending
  - id: type-filter
    content: Add the Type column filter, after deciding single-select (no backend change) or multi-select (needs a backend types list)
    status: pending
  - id: per-type-columns
    content: Branch the Archive, Storage, Size and Actions columns per task type - UNPACK_ARCHIVE params and result are shaped differently and it has nothing to consume
    status: pending
  - id: notifications
    content: Decide whether the completion-notification poll watches UNPACK_ARCHIVE too, and extend fetchActiveTasks if so
    status: pending
  - id: docs-cases
    content: Update 8.2._Upload_Download_data.md and the release note - the "not available yet" notes go - and the UI test cases on their own branch
    status: pending
isProject: false
---

# Upload-and-extract on the real async-task API

Follow-on from issue 4571's GUI work, on branch `issue_4571/extract_archives_on_upload_gui`
(based on `java21_migration`, which carries the Java side as `3a8d4991d1`).

## Context / assessment

The GUI shipped against an endpoint that did not exist, so
`client/src/components/special/extract-storage-archives.js` ends in a `setTimeout` mock and the
manual carries a "server-side extraction is not available yet" note. `java21_migration` has since
landed the Java side, so the mock can go.

It is not a drop-in replacement. The verification below is the substance of this plan.

### What the backend actually accepts

`POST /async-task/create` (`AsyncTaskController.createAsyncTask`), body `AsyncTaskParams`, which
Jackson discriminates on its own `type` property
(`core/src/main/java/com/epam/pipeline/dto/async/AsyncTaskParams.java`). For an extraction that is
`UnpackArchiveParams`:

| Field | Required | Note |
|---|---|---|
| `type` | yes | the literal `UNPACK_ARCHIVE` |
| `sourceStorageId` | yes | `Long` |
| `sourcePath` | yes | **one** archive, storage-relative, no trailing slash |
| `destinationStorageId` | yes | `Long` |
| `destinationPath` | yes, may be `""` | normalised to a folder prefix server-side |
| `deleteSourceOnSuccess` | no | `false` default; needs WRITE on the source |
| `overwrite` | no | `false` default; one collision refuses the whole task |

`UnpackArchiveTaskProcessor.prepare` asserts each required field, then checks READ on the source
storage and WRITE on the destination storage, and the path permissions on both.
`AsyncTaskParamsValidator.asFilePath` rejects a blank path, a trailing slash, a leading `/`, `//`,
`.`, `..`, a leading `~`, and control characters.

### The four mismatches with what the GUI sends today

1. **One archive per task, not a list.** `handleExtractArchives(storageId, paths)` sends one call
   for the whole batch. `sourcePath` is a single `String`, so N archives are N tasks.
2. **The destination is not optional.** The GUI never collects one. The only sensible value is the
   folder the upload went into - the `path` prop `UploadButton` already holds - with
   `destinationStorageId` equal to the source.
3. **The quota is per task, not per batch.** `AsyncTaskApiService.assertUserQuota` counts the
   user's active tasks against `ArchiveTasksConfig.maxUserTasks` on every create, and that
   allowance is shared with archive downloads. A ten-archive batch needs ten slots and the eleventh
   create throws. The GUI currently assumes one request, one outcome, one toast.
4. **The errors are per task.** With N creates, some may succeed and some fail. One toast saying
   "queued" is no longer accurate.

### The blocker outside the GUI

**No worker runs an `UNPACK_ARCHIVE` task.** `storage-async-task-service/sats/model.py:109`
declares `DOWNLOAD_ARCHIVE` alone, and `sats/worker/__init__.py`'s `_WORKERS` registry has only
`ArchivePreparationWorker`. The API accepts the task, stores it and hands it out on
`/async-task/next` - nothing claims it, so it sits `PENDING` until it is cancelled or times out.

Removing the mock therefore moves the GUI from "nothing happens, and the doc says so" to "a real
task is queued and never runs". Settle `verify-worker` before `real-call`: either the Python worker
is in scope (a module in `sats/worker/` plus one line in `_WORKERS`, per that file's own comment),
or the docs keep a note and say the task waits for a worker.

### Why the Storage tasks table shows nothing today

`downloads-manager.js:206` hard-codes `type: ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE` into every filter
body, and `ASYNC_TASK_TYPES` (`models/asyncTask/statuses.js:71`) has that one member. So an
extraction task would not appear in the table even once it is created. This is the same change the
Type column needs, which is why the two belong in one plan.

## Steps

### move-files

Mirror what `java21_migration` did to the download-archive GUI:

| From | To |
|---|---|
| `client/src/components/special/extract-storage-archives.js` | `client/src/components/pipelines/browser/data-storage/utils/extract-archives.js` |
| `client/src/components/special/extract-storage-archives.test.js` | `.../utils/extract-archives.test.js` |

Use `git mv` so the rename is visible in the diff. Importers to fix: `UploadButton.js` (which
stays in `special/` - it is not storage-specific) and the comment cross-references in
`downloads/queued-task-message.js`. Check whether the move belongs in `utils/index.js`'s
re-exports, as `download-archive.js` does.

### task-type-constant

`models/asyncTask/statuses.js`: add `UNPACK_ARCHIVE: 'UNPACK_ARCHIVE'` to `ASYNC_TASK_TYPES`, and
a `typeOf(task)` helper beside the existing `statusOf`. `taskArchiveIsAvailable` currently answers
from status alone (`statuses.js:107`) - it has to require `DOWNLOAD_ARCHIVE` as well, because
`UnpackArchiveTaskProcessor.consume` throws `UnsupportedOperationException`, so a Download button
on a successful extraction row would always fail.

### real-call

`extract-archives.js`: replace `extractArchives` with a call through
`models/asyncTask/AsyncTaskCreate.js` (already exists, `POST /async-task/create`). One create per
archive path:

```js
{
  type: 'UNPACK_ARCHIVE',
  sourceStorageId: storageId,
  sourcePath: path,
  destinationStorageId: storageId,
  destinationPath: folder
}
```

`overwrite` and `deleteSourceOnSuccess` are left at their `false` defaults; offering them in the
pop-up is a separate decision, not part of this plan.

The `extract` parameter and `UploadButton`'s `extractArchives` prop existed only to inject the
mock. A real model makes a network stub the better seam, so drop both and use `stubApi()` from
`@test` - see `client/test/AGENTS.md`. `UploadButton.test.jsx` loses the prop it passes in two
tests.

Keep `handleExtractArchives` swallowing its error rather than re-throwing: `UploadButton` fires it
without awaiting. Note that `handleDownloadArchive` does the opposite - its modal needs the
rejection to unlock itself.

### quota

Three options, in increasing cost:

1. Create sequentially, stop at the first failure, and toast how many of the N were queued.
2. Call `asyncTasks.checkActiveTasksLimit()` first (as the download flow does) and refuse the whole
   batch when fewer than N slots are free.
3. Queue one task carrying every archive - needs a backend change to `UnpackArchiveParams`, out of
   scope here.

Option 1 is the smallest honest behaviour. Whichever is chosen, the queued toast's wording has to
survive a partial result, which the current single sentence does not.

### type-column

`downloads/columns.js`: a `Type` column rendering `task.type` through a display map (`Download
archive`, `Extract archive`) rather than the raw enum. Place it before `Archive`.

`downloads-manager.js`: remove the hard-coded `type` from the filter body and drive it from the
filter state instead. Everything the table shows today is a download task, so verify the default
(no type filter) does not change what an existing user sees beyond the new rows.

### type-filter

A decision is needed first:

- **Single-select** - no backend change. `AsyncTaskFilterVO.type` is one `AsyncTaskType`
  (`api/.../controller/vo/async/AsyncTaskFilterVO.java`), so the dropdown offers "all / downloads /
  extractions" as radio behaviour.
- **Multi-select**, matching the Status filter's checkbox list - needs a `List<AsyncTaskType> types`
  on `AsyncTaskFilterVO` and in `AsyncTaskManager.loadByFilter`'s query, plus the Python client's
  own `filter_tasks` if it passes the field.

Follow the Status filter's mechanics either way: a custom `filterDropdown`,
`filterDropdownVisible`, `filteredValue`, and no custom `filterIcon` - `columns.js:85-109`
documents the antd 2.13.14 bug that last point avoids. Add a `typeIsFiltered` predicate next to
`statusesAreFiltered` so the "Reset filters" control and the header highlight share one comparison.

Sorting by type is **not** available: `AsyncTaskSortField` has `CREATED`, `STARTED`, `FINISHED`,
`STATUS_CHANGED` only. Adding it is a backend enum change; leave the column unsorted.

### per-type-columns

Every existing column assumes `DownloadArchiveParams`:

| Column | Reads today | For `UNPACK_ARCHIVE` |
|---|---|---|
| Archive | `params.archiveName`, `params.paths` | `params.sourcePath`; no name, no list |
| Storage | `params.storageId` | `params.sourceStorageId` (and a destination, if shown) |
| Size | `archiveSizeOf(task)`, from the download result | `result.bytesWritten`, with `filesWritten` beside it |
| Actions | Download when status is SUCCESS | Cancel only; nothing to consume |

`UnpackArchiveResult` carries `destinationStorageId`, `destinationPath`, `filesWritten` and
`bytesWritten`, and is reported on `SUCCESS` only - so every one of them is absent on a pending,
running or failed row, exactly as `archiveSizeOf` already assumes for a download.

### notifications

`CurrentUserAsyncTasks.js:171` polls `fetchActiveTasks(ASYNC_TASK_TYPES.DOWNLOAD_ARCHIVE)`, so the
"task finished" notification covers downloads only. Extending it to extractions means a second
call, or a filter with no type. The finished-download notification offers a download link, which an
extraction has no equivalent for - so its message needs its own wording, not a reuse.

### docs-cases

- `docs/md/manual/08_Manage_Data_Storage/8.2._Upload_Download_data.md` - step 3's "server-side
  extraction is not available yet" note goes, or changes to whatever `verify-worker` settled. The
  Storage tasks window section gains the Type column and its filter.
- `docs/md/release_notes/v.0.17/v.0.17_-_Release_notes.md` - the same note in the "Upload and
  extract archives" section.
- UI test cases under `docs/testcases/UI/` go on their own branch and pull request - the
  `testcases` skill. The existing 4571 cases live on
  `issue_4571/extract_archives_on_upload_gui_testcases`.

## Checks

`client/` work: `npm test`, `npm run lint`, `npm run stylelint` from `client/` under Node 14, then
the `verify-client-live` skill - this changes rendered data and a table's filters.

If `verify-worker` brings `storage-async-task-service` into scope, that module's own runner applies;
if `AsyncTaskFilterVO` changes, `./gradlew :api:test :api:checkstyleMain :api:pmdMain`, which needs
the live PostgreSQL configured in `api/src/test/resources/test-application.properties`. The
`verify-changes` skill is the authority on both.
