<!--
The shape of every page under docs/md/release_notes/. Fill in a copy; replace each <...>; delete
this comment. The copy is the publishable text and nothing else, so it can be diffed straight
against the version's page:

    git diff --no-index docs/md/release_notes/v.<version>/v.<version>_-_Release_notes.md \
                        .agents/.temp/release_notes_v<version>_draft.md

The draft is the page as it should read, not only the new parts - so copy the text of every entry
already on the page verbatim, including its list line, and write new text only where the cycle is
not described yet. The diff is then the proposed change and nothing else; where it reports a deletion
or a change to an existing entry, that is a finding about the page, not noise to tidy away.

Hold the shape exactly - the blank lines, and a `***` between the title block, the bug-fix block, the
artifacts line and every `##` section. There is no `***` between the `###` entries inside Notable Bug
fixes, and none at the end of the file.

Entries go in order of delivery, earliest first, in the list and in the sections alike. Release
artifacts are the one section that order does not reach, since the build that produces them comes
after every entry; everything else here holds for it, its list line and its `***` included.

The artifacts section belongs to the GitHub release description this draft becomes, and to no page:
a notes page carries the entries and never the artifacts. So where the diff against the version's
page reports that section as an addition, that is the one addition which is not a finding.

A feature or bug-fix name opens with its issue number - `#<n>`, then a space, then the title - and
the list line repeats it. Where no single issue covers the entry, the name is the title alone: that
is several related fixes gathered into one entry, or work that carried no issue number at all. The
`###` parts of a feature do not repeat their feature's number, and Notable Bug fixes and Release
artifacts carry none. An entry already published on the page keeps the name it has: renaming it moves
the anchor readers and other pages already link to.

An anchor is the heading lowercased with spaces turned into `-` and everything else dropped -
backticks, quotes, apostrophes, periods, commas, colons, slashes, parentheses, and the `#` of an
issue number, whose digits stay. A heading "Storage: `pipe` upload/download limits" therefore anchors
as #storage-pipe-uploaddownload-limits, and "#4864 Capacity block offering search" as
#4864-capacity-block-offering-search. A list label may differ from the heading it points at; its
anchor may not. Check every one of them against the headings before showing the draft.

Dashes are a spaced hyphen ` - `, in the title and in the prose. Not an em dash.

Manual links are relative to docs/md/release_notes/v.<version>/, so they read
`../../manual/<chapter>/<page>.md#<anchor>` and resolve from there, not from the draft's own
directory. Check each against the file on disk.
-->
# Cloud Pipeline v.<version> - Release notes

- [#<issue> <Feature title>](#<issue>-<feature-anchor>)
    - [<Part of that feature a reader would look up on its own>](#<part-anchor>)

***

- [Notable Bug fixes](#notable-bug-fixes)
    - [#<issue> <What was wrong>](#<issue>-<fix-anchor>)

***

- [Release artifacts](#release-artifacts)

***

## #<issue> <Feature title>

<What a user can now do, and what it is for - present tense, and never the commit subject. Name the
preference, run parameter or API field that controls it, with its default and the value that turns it
off. Close with `See [<section>](../../manual/<chapter>/<page>.md#<anchor>).` where the manual covers
it.

Usually that is user-facing behaviour. A platform-wide change - a runtime, build or framework level,
a service moved - is the exception, and is written as what it is, naming the old and the new level,
because nothing a user clicks is what changed. Such an entry owes the consequences for whoever
upgrades: renamed configuration keys, a table that must pre-exist, a new default limit.>

### <Part of that feature a reader would look up on its own>

<Only where a feature has such parts. Otherwise one `##` section is the whole entry.>

***

## Notable Bug fixes

### #<issue> <What was wrong, from the user's side>

[#<issue>](https://github.com/epam/cloud-pipeline/issues/<issue>)

Previously, <what happened, and what it took to hit it - the preference, OS version, cloud provider,
storage type or sequence of actions>.
Now, <what happens instead>.
See details in [<section>](../../manual/<chapter>/<page>.md#<anchor>).

<!--
The issue line is dropped where no single issue covers the entry - one that gathers several related
fixes opens straight into the prose instead, under a name that carries no number either. One issue,
one link; never a pull request number, and never an issue number the commit subject did not carry.
-->

***

## Release artifacts

Built by run [<run id>](https://github.com/epam/cloud-pipeline/actions/runs/<run id>) of
Cloud-Pipeline-Distribution, from `<ref>` at commit `<head sha>`.

| Artifact | Where |
|---|---|
| Distribution tarball | https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/builds/<ref>/cloud-pipeline.<version, build number and full sha>.tgz |
| `pipectl` installer | https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/builds/<ref>/pipectl.<version, build number and full sha> |

Docker images, under `quay.io/lifescience/cloud-pipeline`:

| Service | Version tag | This build's tag |
|---|---|---|
| `<cp-api-srv>` | `<api-srv-0.0>` | `<api-srv-0.0.00000000.final>` |

<!--
Fill this section only after the final distribution build has finished, and only with what it
published: one image row per entry of the build matrix in .github/workflows/build-pipectl.yml - all
of them, not only the few the manifest step picks out - every object listed from the builds bucket,
and every tag confirmed against the registry. The commit-specific tag carries the first 8 characters
of the sha, where the S3 object names carry all of it. An artifact that cannot be confirmed is
reported to the user, not written here.

This section, and the manual links rewritten to https://cloud-pipeline.com/..., are what separate
the release description from the page. Make both changes on the way out, not while the draft is
still being diffed against the page.
-->
