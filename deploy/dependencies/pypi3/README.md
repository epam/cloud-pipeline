# pypi3 mirror

Builds the pypi3 mirror, `s3://cloud-pipeline-oss-builds/tools/python/pypi3/`: the Python package index that
the runs (`CP_REPO_PYPI3_BASE_URL_DEFAULT`), the commit and pause scripts on the node hosts and the platform
images install from, with the platform's Python 3.12. pip reads it at
`http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/tools/python/pypi3/simple`.

The package files are at the top of the prefix, and the index in `simple/`: `simple/index.html` links each
package page, and `simple/<package>/index.html` links the files of the package as `../../<file>`.

## Package list

`requirements.txt` has one pinned requirement (`name==version`) per line. Each is downloaded with its
dependencies, which are not pinned: a build takes the versions that fit at that time.

The mirror keeps every file it has ever indexed: the runs of older platform versions install the versions they
pin. Removing a line from `requirements.txt` removes nothing from the mirror. To remove a file, edit the bucket
by hand.

`workflows/pipe-common/test/test_pypi3_mirror_list.py` checks that each Linux pin of the `pipeline` package
(`workflows/pipe-common/setup.py`) is in `requirements.txt`. `test.sh` runs it too.

## Build

`build.sh` downloads the platform's Python 3.12, the published `tools/python/3/python3.12.tar.gz`, as a run
does, and checks it against its `.sha256`. Without a published Python, or with a wrong checksum, it stops:
build and publish the [python3](../python3/README.md) dependency first. A change to both folders builds the
mirror with the Python published before it. `CP_PYTHON3_DISTRO_URL` sets another Python.

pip downloads the files that fit the platform it runs on, so `build.sh` downloads the requirements twice:

- in `manylinux2014` (`glibc` 2.17, as CentOS 7, the oldest the platform supports)
- in `manylinux_2_34` (`glibc` 2.34, as Rocky Linux 9 and RHEL 9)

A package without a `glibc` 2.17 wheel gets its source distribution on the first, and its wheel on the
second. Both images are pinned by digest in `build.sh`.

Then it writes the index: the files of the build, merged with every link of the index published now (read from
the address above, or `CP_PYPI3_MIRROR_URL`). The merge never drops a link. It fails when a requirement has no
file in the index.

The output dir has the layout of the mirror: the files of the build, `simple/`, and `BUILD-INFO.txt` (the
commit, the images and the Python), which is not published. It must be new or empty: `build.sh` stops when it
has package files already, since the index and the publication take every one of them.

## Check

`check.sh <image> <output dir>` checks the build in a base image, with the platform's Python 3.12:

- it serves the mirror from the output dir. A file that the index links but the build does not have (a file
  published before) is redirected to the bucket. So pip sees what it will see once the build is published.
- the `pipeline` package of this repository installs from the mirror alone, and `pip check` is clean
- every requirement has a file that pip picks in this image, with no dependencies (`pip download --no-deps`):
  a wheel that fits, or a source distribution whose metadata builds there

The workflow checks it in each image of `check-images.txt`: CentOS 7, Amazon Linux 2 (the AWS node hosts),
Ubuntu 18.04, 20.04, 22.04 and 24.04, Rocky Linux 8 and 9, and Debian 12.

## Publish

On `develop`, the workflow publishes a build whose checks all pass with `publish.sh`. Every run of pip on the
platform reads this index, so it:

1. merges the index of the build again with the index published now, so a link published since the build
   stays
2. publishes nothing when the new index would lose a link of the published one, or would link a file that
   neither the bucket nor the build has, or when it cannot read or list the bucket
3. uploads the files that the bucket does not have, each with a conditional write: a file that the bucket has
   by then is kept. It never replaces a file in the bucket.
4. copies the published index to `simple-backups/<UTC time>/`, then uploads the pages that changed: the package
   pages first, `simple/index.html` last

It deletes nothing. A build that adds nothing publishes nothing. `CP_PYPI3_PUBLISH_DRY_RUN=1` (any value but
`0`) reads the bucket, prints what it would upload and writes nothing. A package page with a link that is not
`../../<file>`, e.g. one added by hand, stops the build and the publication: the merge would drop it. It needs `s3:ListBucket` on the bucket, and `s3:GetObject` and
`s3:PutObject` on `tools/python/pypi3/`.

To put an index back, copy its backup over `simple/`:

```bash
aws s3 cp --recursive --content-type text/html \
    s3://cloud-pipeline-oss-builds/tools/python/pypi3/simple-backups/<UTC time>/ \
    s3://cloud-pipeline-oss-builds/tools/python/pypi3/simple/
```

This leaves the package pages that the publication added for new packages: pip opens a package page by its
name, so it still finds them. Remove them by hand, if needed.

## Run by hand

Needs Docker, curl and python3; on a host that is not x86-64 the downloads run under emulation.

```bash
deploy/dependencies/pypi3/test.sh
deploy/dependencies/pypi3/build.sh ~/pypi3
deploy/dependencies/pypi3/check.sh centos:7 ~/pypi3
CP_PYPI3_PUBLISH_DRY_RUN=1 deploy/dependencies/pypi3/publish.sh ~/pypi3
```

With colima, keep the output dir under `$HOME`: colima does not share `/tmp` with the containers.
`deploy/repos/build-repo-pypi3.sh` runs all the steps, the publication included.

`test.sh`, which the workflow runs before the build, tests `mirror.py` (`test_mirror.py`), the download of the
platform's Python, and `publish.sh` against a stub `aws` CLI that keeps the bucket in a local folder, and runs
`test_pypi3_mirror_list.py`.
