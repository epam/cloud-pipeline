# Issue 4571 - Extract archive from the selection, and the completion notification - ledger

| Phase | Name | Status | Model / effort | Commit | Notes |
|---|---|---|---|---|---|
| 1 | `deleteSourceOnSuccess` back to `false` | done | opus · low | | reverses the earlier decision; `overwrite: true` stays |
| 2 | disable "Upload and extract" on a shortfall | done | opus · medium | | one `extractionBlockedBy`, shared with the selection action |
| 3 | "Extract archive" selection action | done | opus · high | | no modal; disabled with a Popover at `zIndex: 1071` |
| 4 | notify when an extraction finishes | done | opus · high | | `trackTask` + type-aware message, no link |
| 5 | docs and release note | done | opus · medium | | new manual section for the selection action |
| — | fresh-eyes review | | opus | | also owes the three commits before this |
| 6 | UI test cases | not done | | | `4571_4` and `4571_9` are now stale again |

decision · `deleteSourceOnSuccess: false`, the user's call, reversing
`extract_archives_real_api.ledger.md`. The manual's deletion note and the release-note warning went
with it.
decision · a shortfall now disables rather than warns: one archive is one task, so a batch of three
with one slot free cannot be honoured. `handleExtractArchives`'s partial-queue path stays as the
safety net for the allowance changing between the check and the click.
decision · "Extract archive" is offered for a single file, unlike "Download archive" - extracting one
archive is the ordinary case. Gated on write access, since extraction writes.
note · the Popover needed `overlayStyle={{zIndex: 1071}}`: antd 2.13.14 puts `@zindex-popover` at
1030 and `@zindex-dropdown` at 1050, so it rendered behind the menu. The number is copied from
`pipelines/launch/form/utilities/capability.js`, which solved the same thing.
note · antd's disabled menu items only restyle colour - no `pointer-events: none` - so the disabled
item can still own the hover target.
note · the selection's allowance is checked on the dropdown's `onVisibleChange`, not polled: the menu
is transient, and a stale answer costs one click the server would refuse anyway.
note · `restore()` now syncs without a type, so a task queued on another machine is picked up
whichever kind it is. `extractedArchiveNameOf` moved into `statuses.js` - the notification and the
table's Archive column both need it, and `archiveNameOf` reads `params.archiveName`, which an
extraction does not have.
note · tracking an extraction made three store tests reach `continuousFetch`'s crypto-backed backoff,
which jsdom has no `window.crypto` for. They now stub the task route and carry `created`, as the
download tests already did - the suite's own `afterEach` comment describes this trap.
