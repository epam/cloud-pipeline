---
name: prepare-release
description: Prepare the release of the version currently in development, in four stages that each stop at the user - draft this cycle's release notes and a report of where the published notes disagree, close the findings the user picks and push them, dispatch the final distribution build with alias=final, and record the artifacts it published. Use when asked to prepare a release, to draft, check or update release notes, to list what has gone into this release cycle so far, to build the release, or to list its artifacts.
---

# Preparing a release

Four stages. **Each one ends at the user, and none starts before that answer**: from stage 2 on, what
they lead to is published where the team cannot take it back.

| Stage | Produces | The gate that ends it |
|---|---|---|
| 1 | the draft and the cycle report, in gitignored scratch | which findings to close |
| 2 | those findings closed in the working tree, for the user to commit | their own commit and push |
| 3 | the distribution build dispatched with `alias=final` | whether to dispatch it |
| 4 | the artifacts of that run, listed in the draft | the GitHub release they create from it |

**A stage may be asked for on its own**, and the earlier ones leave their work behind - the two files
in `.agents/.temp/`, the user's release-notes commit on `develop`, the run recorded in the report.
Look for that before redoing it.

**Unattended, stage 1 is the whole deliverable.** The three gates after it are a person's to answer;
with no one to ask, report the draft and the report and stop.

**Still not this skill's:** cutting the release branch, bumping the version in the root
`build.gradle`, and tagging.

## Stage 1 - The draft and the report

### The two refs

**The version being prepared comes from the root `build.gradle`**, never from the release branch
number:

```bash
sed -n 's/.*major: *\([0-9]*\).*minor: *\([0-9]*\).*/\1.\2/p' build.gradle | head -1
```

That names the notes page - `docs/md/release_notes/v.<version>/v.<version>_-_Release_notes.md`.

**The last release line is the version-sorted last of the `origin/release/*` branches.** Fetch
first, and sort with `-V`: lexicographically `0.9` comes after `0.25`.

```bash
git fetch --quiet --prune origin
last=$(git branch -r --list 'origin/release/*' --format='%(refname:short)' \
         | sed 's|^origin/release/||' | sort -V | tail -1)
base=$(git merge-base origin/develop "origin/release/$last")
```

`--prune` because a deleted release branch otherwise stays in the sort input. The glob already leaves
out the dated `bkp/release/*` backups and the `stage/*` deployment lines. A release line may carry a
suffix or a patch level, so the top of the sort is not always a plain `<major>.<minor>`.

**The branch number and the `build.gradle` version move independently, and have been several
versions apart, so a mismatch proves nothing.** The signal to check is the opposite one: **where
`$last` equals the `build.gradle` version, this version's line has already been cut**, and the cycle
asked about ended at that cut rather than at develop's tip. Then take the entry before `$last` as the
base and `origin/release/$last` as the end of the range. Name both refs and the commit count either
way before going on.

Use the `origin/` refs throughout: a local `develop` is stale as often as not.

### The commit list

```bash
git log --no-merges --format='%h %s' "$base..origin/develop"
```

**Never `--first-parent` here.** develop carries `Merge branch 'develop' of <url> into develop`
pull-merges on its own first-parent chain, and that flag follows the local side of each, dropping
whatever reached only the remote side - other people's squash-merged issues, not intermediate
commits. Confirm the size of what it would hide before trusting any claim about it:

```bash
git rev-list --no-merges --count "$base..origin/develop"
git rev-list --first-parent --count "$base..origin/develop"
```

`--no-merges` cannot lose a commit; the duplication it lets through is absorbed by grouping on the
issue.

**The unit of work is the issue in the subject, not the commit.** A number in trailing parentheses is
the pull request, and `gh issue view` resolves a pull request number just as happily as an issue, so a
bare `(#n)` read as an issue silently yields the wrong title and no labels. One issue spans several
commits, one commit several issues. Ask GitHub rather than paraphrasing a subject line:

```bash
gh issue view <n> --json number,title,labels,state,body
```

The labels sort the list: `kind/enhancement` against `kind/bug`, `sys/*` for the area, and
`state/has-doc` claiming the documentation exists already - a claim to check, not to trust. It says
nothing about *which page* the documentation landed on.

**A commit with no issue number can still be the headline of the release**, so the groups in the
report are decided by what a change does for a user, never by whether a number is in the subject. A
commit in neither half of the report has been dropped silently, which is the one outcome to avoid.

### Expanding a squashed merge

**A squash merge is one subject line over an unbounded amount of work**, and a long-lived branch
squashed into develop can carry most of a release behind a subject that names only its own theme.
Suspect every commit in the range whose diff is far larger than its subject suggests, and whose
subject names a branch, a migration or a component rather than a change:

```bash
git show --stat <sha> | tail -1
```

Expand it rather than entering it as one line. Its pull request number is in the squashed subject, and
fetching that pull request's head makes the real history local and searchable:

```bash
gh pr view <n> --json headRefName,mergedAt
git fetch --quiet origin pull/<n>/head
git log --no-merges --format='%h %ad %s' --date=short "origin/release/$last..FETCH_HEAD"
git rev-list --no-merges --count "origin/release/$last..FETCH_HEAD"
```

**Count the commits locally, as above, and take no count from the GitHub API.** Both
`gh pr view --json commits` and the REST commits endpoint cap their answer silently, at different
limits, so a capped figure is indistinguishable from a real one.

Each commit on that branch carries its own issue in its subject, which the squashed subject hides.
Those issues are the entries, and the branch names under `git branch -a` often confirm which issue
owns which part.

### What the cycle actually contains

**Do not take the commit range as the answer to what the release contains.** A long-lived branch
means a feature reaches develop long after it was written, and a release branch cut the same day the
branch merged does not have it at all.

**Decide membership by presence, not by date.** Pick a marker per candidate feature - a class name, a
preference key, a role, a run parameter, a module directory - and count where it exists:

```bash
for m in <marker> ...; do
  printf '%-34s develop:%-5s %s:%s\n' "$m" \
    "$(git grep -l "$m" origin/develop | wc -l)" \
    "release/$last" "$(git grep -l "$m" "origin/release/$last" | wc -l)"
done
```

Absent from the release line and present on develop means this cycle, whatever the dates say. Present
in both means an earlier cycle, and the entry belongs to that cycle's page.

### Where its release notes may already be

**One notes page stays live until the version is bumped, so a branch written before the bump put its
release notes on the previous page.** Nobody re-homes that text when the merge lands. Check which
notes pages a squashed merge touched:

```bash
git show --stat <sha> -- docs/md/release_notes/
```

Text that describes this cycle and sits on an earlier page is a **move**, not a rewrite - it is
already written and reviewed. Both pages change together.

**Moved text names its old version.** Grep the result for version references and rewrite them to the
opener the pages use most:

```bash
grep -nE 'v\.?0\.[0-9]+' <draft>
```

Say which page an entry belongs on, and on what evidence - the presence test above is the evidence,
not the date the page was last edited.

### Order

**Entries go in order of delivery, earliest first - the earliest commit that implements each, not the
date it reached develop.** A squash dates everything to the day it merged, which orders nothing. The
markers are the ones the presence test already needed:

```bash
git log --reverse --format='%h %ad %s' --date=short -S'<marker>' "origin/release/$last..FETCH_HEAD"
```

Break ties on the committer timestamp in UTC (`--format='%h %aI'`), since the authors are in
different zones. Where one entry's work spans the whole range, say whether you ordered it by its
first or its last commit - the two put it at opposite ends.

### The two files

Both come from this skill's `templates/`, filled in rather than reshaped, and both land in gitignored
scratch: `mkdir -p .agents/.temp` first, since a fresh checkout has none.

| Template | Copy to | Holds |
|---|---|---|
| `templates/release-notes.md` | `.agents/.temp/release_notes_v<version>_draft.md` | the publishable text, in the shape every page under `docs/md/release_notes/` carries |
| `templates/cycle-report.md` | `.agents/.temp/release_notes_v<version>_report.md` | the refs walked, every commit accounted for, the delivery order, and the findings |

Each template carries the rules for its own contents - the page shape and how an anchor is built, and
what belongs in a row. The pages already under `docs/md/release_notes/` are the worked examples; the
longest one shows every shape a page can take, and the page the current version names is the closer
model for its own draft.

**Nothing is written into `docs/` in this stage.** The draft exists to be diffed against the version's
page, and the report is what the user is shown.

Where assembling the draft means copying many sections verbatim, do it with a script rather than by
hand, and keep the script outside the repository - retyped text drifts from what was reviewed, and a
rerun has to reproduce the same file.

### Where the published notes disagree

Read the page the version names and fill in the report's findings.

**No directory for the version is the finding itself**, not a reason to stop: the draft is then the
new page, and `docs/mkdocs.yml` lists every notes page by hand, so it owes a nav line too. A release
branch of its own may carry the same gap - a nav line whose page was never written.

Check the draft before showing it: every list anchor resolves to a heading, and every manual link
resolves to a file and an anchor on disk.

**The gate:** show the report and ask which findings to close. Those answers are stage 2's whole
scope; a finding the user did not pick stays in the report.

## Stage 2 - Closing the findings

Each finding is the `docs/` edit its own Fix column names, and nothing wider. A move between pages
changes both pages. A version with no page yet owes its line in `docs/mkdocs.yml` as well.

Let the diff drive the edit rather than retyping the draft over the page:

```bash
git diff --no-index docs/md/release_notes/v.<version>/v.<version>_-_Release_notes.md \
                    .agents/.temp/release_notes_v<version>_draft.md
```

**The edits belong on `develop` itself, and committing them is the user's own work.** Leave them in
the working tree - a new page staged, so that it is not invisible - and commit nothing: this is the
one release-notes change that does not go on a branch of its own, and a shared branch is not an
agent's to write to.

**The gate is the hand-over.** Give the user what they need to finish it in one message:

- the files the edits touched, and `git status` showing nothing else of theirs caught up in them;
- the diff, so the commit is reviewed before it exists;
- a subject line to use or discard - the `commit-changes` skill → §3 has the forms;
- that the commit and the push are both theirs to make.

Stage 3 opens when they say it is pushed, not when the edits are written. Re-read the page then: it
and the draft now agree, which is what stage 4 edits further.

## Stage 3 - The final distribution build

One dispatch of `main.yml` - the `Cloud-Pipeline-Distribution` workflow - carrying the alias that
marks the build the release ships:

```bash
gh workflow run main.yml --ref <ref> -f alias=final
```

**Ask which ref**, and check it against both of these before dispatching:

- `--ref` decides which copy of the workflow runs, so `alias` has to be declared in `main.yml` *on
  that ref* - a ref whose copy predates it is rejected with `422 Unexpected inputs provided`.
- **the tarball is uploaded for a fixed set of refs only** - `develop`, `master`, `release/*`,
  `stage/*`, `vscode-ext`, `java21_migration`, in `deploy/github_actions/gha_pack_dist.sh`. Any other
  ref builds for an hour, then hands `Build_Pipectl` a distribution URL for an object nobody
  uploaded, and every image needing it fails.

`alias=final` reaches `build-pipectl.yml`, which appends it to each image's commit-specific tag, so
the images carrying `.final` are this build's.

**Confirm the dispatch with the user on every run** - an earlier run's approval is not this one's -
and confirm it knowing what the run replaces. The tarball and `pipectl` objects do not overwrite
anything, since their names carry the build number and the head sha, but two other things are
replaced in place:

- **each image's moving `<service>-<major>.<minor>` tag** in the registry, which deployments tracking
  the version resolve;
- **the published manual.** `Publish_Docs` rebuilds it, wipes the ref's directory on `gh-pages` - and
  the root of `gh-pages` too when the ref is `develop` - and force-pushes. `Build_All` runs after it,
  so the docs are republished even if the build then fails.

`gh workflow run` prints no run id, so read it back - and check the newest run is this dispatch and
not someone's push:

```bash
gh run list --workflow=main.yml --limit 5 \
  --json databaseId,event,headBranch,status,createdAt,url
```

**Record that id and URL in the report** before ending the stage. A run does not carry its inputs in
the API, so afterwards nothing distinguishes this dispatch from a push build of the same ref.

Neither workflow sets `cancel-in-progress`, so a build already running is safe from the dispatch -
but a dispatch that lands while one is running goes *pending*, and the next run to enter the group
cancels it. A push to the same ref is enough to evict it, which is why stage 4 reads the run's own
conclusion rather than assuming it ran.

**A run takes between one and two hours, so do not wait on it.** Report its URL, end the stage there,
and open stage 4 when it has finished.

## Stage 4 - The artifacts it published

Name every artifact from the run, and confirm each one exists before it goes in the draft. **Read the
run's own conclusion first, not only its jobs** - a dispatch that waited in the concurrency group may
have been cancelled before it built anything:

```bash
gh run view <run-id> --json number,headSha,status,conclusion,jobs \
  -q '{number, headSha, status, conclusion, failed: [.jobs[] | select(.conclusion != "success") | .name]}'
```

A failed job means part of the list below does not exist. Report what is missing rather than thinning
the list silently.

**The version in every artifact name** is the root `build.gradle` version, then the build number,
then the head sha: `<major>.<minor>.<patch>.<seed + run number>.<head sha>`. The seed is
`CLOUD_PIPELINE_BUILD_NUMBER_SEED` in `main.yml`. The S3 object names carry the **full** sha; the
image tags carry its **first 8 characters**, which is what `build-pipectl.yml` cuts for its
commit-specific tag.

**The tarball and `pipectl`** land under the run's own branch prefix in the public builds bucket,
which answers an anonymous listing - so list it rather than composing the name from the formula:

```bash
curl -s "https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/?list-type=2&prefix=builds/<ref>/" \
  | grep -oE '<Key>[^<]+</Key>'
```

The same objects are public over HTTPS at
`https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/<key>`, which is the form the draft
carries.

**The images** are `quay.io/lifescience/cloud-pipeline`, two tags per matrix entry of
`build-pipectl.yml`: its `tag` field with `$DOCKERS_VERSION` replaced by `<major>.<minor>`, and the
commit-specific one ending in `.final`. Build the list from **every** entry of that matrix - its
`include:` is the list of images the platform ships, and the workflow's own manifest step is no model
for it, since that loop takes only the few entries carrying `manifest_pretty_names`.

Confirm each tag against the registry, which answers anonymously:

```bash
curl -s "https://quay.io/api/v1/repository/lifescience/cloud-pipeline/tag/?specificTag=<tag>&onlyActiveTags=true"
```

An empty `tags` array means that image is not in the registry: report it, and leave it out of the
list. `filter_tag_name=like:final` in place of `specificTag` lists what one aliased build pushed,
which is also the only way to recognise that build once the run id is lost.

The list goes in the draft's artifacts section, which `templates/release-notes.md` holds, and the
run's identity goes in the report's header table.

**That draft is then the release description**, and the artifacts section is the part of it the notes
page never carries - stage 2 published the entries, and nothing here goes back into `docs/`.

A body has to stand on its own outside the built manual, so **rewrite every manual link to the
published form** before handing it over: `../../manual/<chapter>/<page>.md#<anchor>` becomes
`https://cloud-pipeline.com/manual/<chapter>/<page>/#<anchor>`. Check each one resolves, the same way
the draft's own links were checked.

**The gate:** the release is the user's to create, as the tag and the title are theirs to choose -
nothing in the repository decides either, and tagging is not this skill's. Hand over the body and the
command, and run neither:

```bash
gh release create <tag> --title '<title>' --draft \
  --notes-file .agents/.temp/release_notes_v<version>_draft.md
```
