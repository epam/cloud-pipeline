# Issue 4571 - Extract archive from the selection, and the completion notification - ledger

| Phase | Name | Status | Model / effort | Commit | Notes |
|---|---|---|---|---|---|
| 1 | `deleteSourceOnSuccess` back to `false` | done | opus · low | | reverses the earlier decision; `overwrite: true` stays |
| 2 | disable "Upload and extract" on a shortfall | done | opus · medium | | one `extractionBlockedBy`, shared with the selection action |
| 3 | "Extract archive" selection action | done | opus · high | | no modal; disabled with a Popover at `zIndex: 1071` |
| 4 | notify when an extraction finishes | done | opus · high | | `trackTask` + type-aware message, no link |
| 5 | docs and release note | done | opus · medium | | new manual section for the selection action |
| 7 | dim the disabled item, fix the hint popover | done | opus · medium | | `cp-text-not-important`; the hint is its own tested component |
| 8 | both modal buttons wait for the first cap check | done | opus · medium | | a click before the answer could have queued past the allowance |
| — | fresh-eyes review | | opus | | also owes the four commits before this |
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
decision · the disabled item is dimmed with `cp-text-not-important` (themes/styles/layout.less), not
antd's own disabled grey, which the user found too faint.
note · the hint moved into `components/action-hint.js` (`ActionHint`), with its own test that
renders it inside a disabled `Menu.Item`. It mounts on `document.body`
(`getPopupContainer`) so the menu can neither clip it nor trap it in its stacking context, keeps the
`zIndex: 1071` override, and swallows the click so it does not reach the menu.
correction · the popover did not open because a disabled menu item carries `pointer-events: none`,
which its children inherit - found by the user in the browser, after my own jsdom tests had cleared
both the ref and the z-index theories. jsdom applies no such rule, so no test here could have caught
it; the hint now sets `pointerEvents: 'auto'` and a test pins that style.
decision · the hint is a Tooltip rather than a Popover, the user's call. A tooltip is 1060 against a
dropdown's 1050, so it only just clears the menu - the explicit override stays.
note · "Upload" is disabled alongside "Upload and extract" while the first check is in flight, as the
user asked. Cancel stays live. A failing check releases both - the server enforces the quota.
note · the existing confirmation tests clicked a button immediately after the debounce settled, which
is now too early; `settleConfirmation()` waits for the button as a user would.
note · the two cursors differed because `cursor` already inherits: the icon took the disabled item's
`not-allowed`, while the row itself was not hit-tested at all and fell back to the menu's own
`pointer`. The row now takes its pointer events back with `cursor: not-allowed`, and the icon's
cursor is `inherit`, so both read the same. rc-menu binds no `onClick` to a disabled item, so this
cannot make the row clickable.

note · the second merge of java21_migration brought `ActionHint` from
`fix/selection_action_hint_tooltip_gui`, which now serves both a plain hint and a disabled action's
reason; this branch's own `disabled-action-hint.js` is gone. Merging the branch rather than copying
the file means whichever pull request lands first, the other does not conflict.
correction · the cap check is re-thrown so `continuousFetch` widens its interval, as every other
poller here does; it was swallowing the rejection and re-polling a failing endpoint every 5 s.
correction · the selection menu only asks for the allowance where "Extract archive" could appear at
all - it was costing a `POST /async-task/filter` on every open, on every storage.
correction · `fetchActiveTasks`'s doc block still said `restore` passes DOWNLOAD_ARCHIVE and that an
extraction must not be announced. Both became false in this work; two module headers and a test
title said the same.
correction · "Checking active downloads..." and the active-download warnings now read "archive
tasks": the cap they check is the shared `maxUserTasks`.
note · `!Number.isFinite(limit)` in `checkActiveTasksLimit` was unreachable - `PreferencesLoad`
always substitutes the default - and is gone.
