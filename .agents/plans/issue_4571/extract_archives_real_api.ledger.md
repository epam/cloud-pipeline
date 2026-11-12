# Issue 4571 — the real extract-archive API and the Storage tasks Type column — ledger

| Phase | Name | Status | Model / effort | Commit | Notes |
|---|---|---|---|---|---|
| — | plan, after verifying both sides of the API | done | opus · high | 1d9d70b1e4 | four client/server mismatches found, plus the missing worker |
| move-files | extract module beside download-archive | done | opus · medium | | `git mv`; the `queued-task-message` import had to be re-pathed |
| task-type-constant | UNPACK_ARCHIVE and the per-type helpers | done | opus · medium | | `taskArchiveIsAvailable` now requires DOWNLOAD_ARCHIVE |
| real-call | `startArchiveExtraction` on the store | done | opus · high | | one task per archive; the injectable `extract` seam is gone |
| quota | sequential, stop at the first failure | done | opus · high | | the toast counts what was queued; a second toast says what stopped the rest |
| type-column | Type column, unpinned filter body | done | opus · medium | | `downloads-manager.js` no longer sends `type: DOWNLOAD_ARCHIVE` |
| type-filter | single-select radio filter | done | opus · high | | `typeIsFiltered` shared with the Reset filters control |
| per-type-columns | Archive, Storage, Size, Actions | done | opus · high | | `bytesWritten` vs `archiveSizeBytes`; no Download button on an extraction |
| notifications | left download-only | done | opus · low | | an extraction is not passed to `trackTask` |
| docs-cases | manual and release note | done | opus · medium | | the caveat now says "queued, not yet carried out" rather than "not available" |
| — | fresh-eyes review | done | opus | | 6 findings + nits; no functional defect in the request bodies |
| — | review fixes | done | opus · high | | findings 1, 3, 4, 5 and the nits; 6 left as designed |
| docs-cases | UI test cases | done | opus · medium | 902764a8dd, #4719 | 4571_4 rewritten, 4571_7/8/9 added |
| cap-in-modal | the cap check in the confirmation pop-up | done | opus · high | | at the cap, "Upload and extract" is disabled and an info Alert says why |
| cap-polled | the check polls while the pop-up is open | done | opus · high | | 5s, `continuousFetch`, stopped on cancel/confirm/close/unmount |
| cap-shortfall | the pop-up warns when the allowance covers only part of the batch | done | opus · medium | | `checkActiveTasksLimit` now carries activeCount/limit/remaining |
| extract-options | `overwrite` and `deleteSourceOnSuccess` sent as `true` | done | opus · low | | the user's call |
| extract-destination | each archive extracts into a folder named after it | done | opus · medium | | `data/a.tar.gz` -> `data/a/`; compound suffixes stripped whole |

decision · the UNPACK_ARCHIVE worker in `storage-async-task-service` is out of scope — the user chose
"GUI only, keep a doc note". The GUI queues real tasks that nothing executes, and the manual and the
release note both say so.
decision · the Type filter is single-select, the user's choice: `AsyncTaskFilterVO.type` is one enum,
so a checkbox filter would have needed a backend `types` list and `:api:test` against a live
PostgreSQL this workstation has no sign of.
decision · `deleteSourceOnSuccess` and `overwrite` stay at their `false` defaults. The pop-up offers
no such choice, and adding one was not in scope.
decision · an extraction is not watched for a completion notification: that notification's
finished-archive message offers a download link an extraction has no equivalent for.
assumption · the destination of an extraction is the same storage and the folder the archive was
uploaded into — the only value the GUI can supply without asking the user.
note · the per-user cap message now reads "archive tasks", not "archive downloads": `maxUserTasks` is
one allowance shared by both kinds, which the extraction flow made user-visible.
note · a batch of N archives needs N slots of that allowance, so a large batch can be queued only in
part. `handleExtractArchives` stops at the first refusal rather than collecting N copies of it.

correction · the manual claimed "an existing file in the destination is not replaced", which misread
`overwrite: false`. `UnpackArchiveParams`'s own javadoc is explicit: one collision refuses the whole
task and nothing is written. The note now says so.
deviation · finding 6 (a per-archive error also abandons the rest of the batch) is left as designed:
telling a quota refusal apart from a bad-path refusal would mean matching on the message.
note · the duplicated queued-toast block moved into `showQueuedTaskMessage`, so both handlers share
the dismiss-on-click half as well as the wording.
note · "Upload and extract" is now gated on the store as well as the capability prop - without the
store the pop-up never appears, rather than uploading and silently queueing nothing.
decision · the cap is checked as the confirmation pop-up opens, mirroring what "Download archive"
does before opening its own. The answer is an Alert in the pop-up, not a toast: the pop-up is already
open and is where the choice is made.
decision · only "Upload and extract" is disabled at the cap. Upload and Cancel keep working - the
files can still be uploaded, and refusing the whole upload over an extraction would be worse.
note · the check is optimistic while in flight and on failure, like `archiveDownloadAllowed`: the
server enforces the quota, so a check that cannot run must not refuse the extraction.
note · `componentWillUnmount` is new on UploadButton - the async check needs an unmounted guard, and
the pending selection debounce is cleared there too.
decision · `overwrite: true` and `deleteSourceOnSuccess: true`, the user's call. Overwriting avoids
the all-or-nothing failure `overwrite: false` causes, and the archive is deleted because the user
asked for its contents - server-side it runs last, so a failed extraction keeps the archive.
decision · the destination is the archive's own name as a folder beside it, the user's call:
`data/reports.tar.gz` extracts into `data/reports/`. The compound suffix is stripped whole, which is
what ARCHIVE_EXTENSIONS's longest-first order is for.
decision · the cap check polls on 5s while the pop-up is open, so the user can wait for a task to
finish rather than re-picking the files. Stopped on cancel, confirm, close and unmount.
note · a name whose stripped base would be `.` or `..` falls back to the archive's own folder;
`assertSafePath` would refuse such a path outright.
note · `checkActiveTasksLimit` now also returns `activeCount`, `limit` and `remaining`. `remaining`
is `undefined` where the check could not run, which callers must not read as zero.
