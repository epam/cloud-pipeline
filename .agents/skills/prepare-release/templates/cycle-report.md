<!--
What the cycle holds and where the published page disagrees with it. Fill in a copy; replace each
<...>; delete this comment and any row or section that has no entries. Never goes into docs/ - it is
the working record behind the draft, and what the user is shown before deciding what to fix.
-->
# Release cycle v.<version>

| | |
|---|---|
| Version in `build.gradle` | `<version>` |
| Page it names | `docs/md/release_notes/v.<version>/v.<version>_-_Release_notes.md` <absent, or not in the nav> |
| Last release line | `origin/release/<last>` |
| Cut at | `<base commit>` <subject>, <date> |
| Range walked | `<base>..origin/develop`, <n> commits, <n> merges in range |
| Ref check | `<last>` <= or !=> `<version>`, so <this version's line is not cut yet / the cycle ended at that cut> |
| Expanded | `<sha>` is a squash of <n> commits spanning <first date> to <last date> - see below |
| Draft | `.agents/.temp/release_notes_v<version>_draft.md`, <n> features and <n> bug fixes, in delivery order |
| Final build | <not dispatched yet, or> run [<id>](https://github.com/epam/cloud-pipeline/actions/runs/<id>) from `<ref>` with `alias=final`, <status or conclusion> |

## Delivery order

Where a squash dates several entries to the day it merged, the order comes from the earliest
implementing commit instead. Ties broken on the commit timestamp in UTC.

| # | Delivered | Entry | Issue |
|---|---|---|---|
| 1 | <date> | <entry title> | [#<n>](https://github.com/epam/cloud-pipeline/issues/<n>) |

<Where one entry's work spans the whole range, say whether it is ordered by its first or its last
commit - the two put it at opposite ends.>

## Release-note material

| Entry | Issue | Kind | `state/has-doc` | Commits | Where its text is now |
|---|---|---|---|---|---|
| <in the user's terms, not the subject line> | [#<n>](https://github.com/epam/cloud-pipeline/issues/<n>) | enhancement / bug / platform-wide | yes / no | `<sha>` | on the page / on `v.<earlier>` / nowhere |

`state/has-doc` is the issue's own claim that its documentation exists. It is checked below, not
taken, and it says nothing about which page the text landed on.

<Where an issue number came from a commit subject inside a squash rather than from the squashed
subject, say so, and say which attributions are worth confirming before publishing.>

## Inside the squashed commit

<Drop this section where no commit in the range needed expanding.>

`<sha>` is PR [#<n>](https://github.com/epam/cloud-pipeline/pull/<n>), head branch `<branch>`,
squashed on <date> from <n> commits. Its work reached develop only through this squash.

**Present on develop, absent from the release line, so this cycle whatever the dates say:**

| Marker | files on `origin/develop` | files on `origin/release/<last>` |
|---|---|---|
| `<class, preference key, role, run parameter or module>` | <n> | 0 |

**Present in both, so an earlier cycle's:**

| Issue | Title | Landed on develop | Documented? |
|---|---|---|---|
| [#<n>](https://github.com/epam/cloud-pipeline/issues/<n>) | <title> | `<sha>`, <date> | <yes / no / on which page> |

## Not release-note material

Carried so that nothing in the range is dropped without being seen. <n> commits, which with the rows
above is the whole range of <n>.

| Group | Commits |
|---|---|
| No issue number - version bump, CI, agent instructions, a test fixed for a toolchain | `<sha>` <subject> |
| UI test cases and e2e tests, for behaviour that shipped in the release line | `<sha>` <subject> |
| UI test cases for this cycle's own feature - not an entry, but not an earlier cycle's either | `<sha>` <subject> |
| Port or backport from a release line, already shipped there | `<sha>` <subject> |

## Findings

| # | Finding | Entry | What is wrong | Fix |
|---|---|---|---|---|
| 1 | Documented on an earlier version's page | `<## section>` | <written by a branch authored before the version was bumped, so the text describes this release from the previous page> | move it to this page; both pages change together |
| 2 | Moved text names the wrong version | `<## section>` | <opens with the version it was written for> | rewrite to the opener the pages use |
| 3 | Missing from every page | <entry>, [#<n>](https://github.com/epam/cloud-pipeline/issues/<n>) | <nothing covers it, and there is no earlier page it could belong to> | add the drafted section |
| 4 | Published text is wrong | `<## section>` | <what the cycle changed after that text was written, or what it never named> | <what it should say> |
| 5 | Not a docs gap | `<key, table or artifact>` | <a configuration or deployment consequence that nothing in the repository provides> | confirm with whoever owns it; worth an issue |
| 6 | Broken link | `<## section>` | <the target that does not resolve, and whether the page, the anchor or both> | <the target that does> |
| 7 | Page missing from the nav | `docs/mkdocs.yml` | <the page exists but no nav line names it, so it is not in the built manual - or a nav line names a page that was never written> | add the line, or the page |
| 8 | Belongs to another release line | <entries> | <shipped in that line and documented nowhere> | that line's notes, not this cycle |
| 9 | Label lags the documentation | [#<n>](https://github.com/epam/cloud-pipeline/issues/<n>) | <documented, and still unlabelled> | add `state/has-doc` |
| 10 | Low confidence | <entry> | <what suggests a change, and what could not be verified> | <what to confirm first> |
| - | Checked, nothing wrong | - | <the link, anchor and nav checks that passed, so the absence of a finding is a result rather than a gap> | - |

Nothing here is applied yet. Ask which findings to close: the answer is the whole scope of the stage
that closes them, and a finding nobody picked stays on this table. One that turns out not to be
documentation work at all is not a `docs/` change and leaves this flow entirely.
