# Dependencies

Recipes for the third-party software that the platform ships but that is not part of its source code. Each
folder builds one dependency from pinned sources, and checks the result.

| Folder | Builds |
|---|---|
| [python3](python3/README.md) | the Python 3.12 distribution of `/usr/local/python3.12` |
| [pypi3](pypi3/README.md) | the pypi3 mirror, the Python package index that the platform installs from |

A folder is a dependency when it has:

- `build.sh <output dir>`: builds it into the output dir, usually in a container
- `check.sh <image> <output dir>`: checks the build in a docker image
- `check-images.txt`: the images to check it in, one per line, at least one; blank lines and lines starting
  with `#` are skipped
- `test.sh`, optional: tests the scripts of the folder, before the build
- `publish.sh <output dir>`, optional: publishes a checked build, with the AWS credentials of the repository

The `Build-Dependencies` GitHub Actions workflow (`.github/workflows/build-dependencies.yml`) builds each
dependency in a job of its own, uploads the output dir as an artifact named after the folder, and checks it
in each of its images. A pull request, or a push to `develop`, builds only the dependencies whose folder it
changed, or all of them when it changed the workflow or the files of this folder. A change to Markdown
files alone builds nothing. A manual run builds the folder it names, or all.

On `develop` (a push, or a manual run), when every build and every check of the run passed, each
dependency with a `publish.sh` is published by it, one at a time, with the AWS secrets of the repository
(`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_DEFAULT_REGION`). A `publish.sh` keeps a newer build from
being replaced by an older one, which a run that finishes late could do. GitHub keeps only one waiting
publication: one that shows as cancelled needs a re-run. Pull requests only build and check.

`select-changed.sh` decides what the workflow builds, and `test-select-changed.sh` tests it in a scratch git
repository (the workflow runs it first). A new dependency needs only its folder: the workflow
does not change. A folder without the three files is no dependency (shared helpers, for one): the workflow
warns about it, and a change to it builds nothing. A manual run with a name that is no dependency fails.
