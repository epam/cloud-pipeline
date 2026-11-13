# Python 3.12 distribution

Builds `python3.12.tar.gz`: the Python that the runs, the commit and pause scripts on the node hosts and the
`cp-api-srv`, `cp-dav` and `cp-edge` images install into `/usr/local/python3.12`. It unpacks to `python3.12/`,
with `bin/python3.12` and `bin/pip3.12`.

It carries its own libraries, so its modules load in any tool image with `glibc` 2.17 or later (CentOS 7
and later), whatever the image has installed:

- the build runs in `quay.io/pypa/manylinux2014_x86_64` (`glibc` 2.17), pinned by digest in `build.sh`
- OpenSSL, libffi, SQLite, ncurses, libedit, xz, bzip2, zlib and libuuid are built from `sources.txt`, as
  static archives, and linked into the interpreter: the extension modules are built in. Their symbols are
  not exported, so they do not clash with the libraries that user code loads.
- `readline` uses libedit, not GNU readline (GPL). There is no `dbm.gnu` (GDBM is GPL), no `dbm.ndbm`,
  no `nis` (removed in Python 3.13) and no `tkinter`.
- `crypt` stays a loadable module that needs the system's `libcrypt.so.1`. RHEL 9 and Rocky Linux 9 have it
  only with the `libxcrypt-compat` package. The module is deprecated, and removed in Python 3.13.
- OpenSSL looks for the CA certificates in `/etc/ssl`: Debian and Ubuntu have them there, in the hashed
  `/etc/ssl/certs` folder. `patches/` makes `ssl` also load `/etc/pki/tls/cert.pem`, where the RHEL family
  keeps them, when `/etc/ssl/cert.pem` is missing.
- ncurses reads the terminfo database of the image: `/etc/terminfo`, `/lib/terminfo`, `/usr/share/terminfo`
- `--enable-optimizations --with-lto`, like the python.org builds
- not shipped: the test suite, the static `libpython3.12.a` (for embedding), debug symbols
- `share/licenses/` has the licenses of the libraries, and those of CPython with the code it bundles
  (libmpdec, expat, HACL* and others, in `cpython/license.rst`). `BUILD-INFO.txt` has the versions it was
  built from.

## Build and check

Needs Docker. On a host that is not x86-64 the build runs under emulation, and takes hours.

```bash
deploy/dependencies/python3/build.sh /tmp/python3
deploy/dependencies/python3/check.sh centos:7 /tmp/python3
```

`check.sh` checks the SHA256 of the tarball, unpacks it into `/usr/local` of the image as a run does, then
checks that:

- no library is missing, but `libcrypt.so.1` for `crypt`
- the modules load and work: `curses` with the terminfo of the image, `crypt` with its `libcrypt.so.1`
  (a SHA-512 hash), where the image has one
- HTTPS verifies with the default `ssl` context. In the minimal Debian and Ubuntu images, which have no CA
  store, it installs their `ca-certificates` first, and fails if it cannot.
- `bin/pip3.12` works, and the `pipeline` package of this repository installs from the pypi3 mirror with
  `pip check` clean
- a watchdog observer, which the NFS events observer of a run uses, gets file events

The workflow checks it in each image of `check-images.txt`: CentOS 7, Amazon Linux 2, Ubuntu 18.04, 20.04,
22.04 and 24.04, Rocky Linux 8 and 9, and Debian 12.

## Publish

On `develop`, the workflow publishes a build whose checks all pass with `publish.sh`, to
`s3://cloud-pipeline-oss-builds/tools/python/3/`:

- `python3.12.tar.gz`, the file that the runs, the node hosts and the platform images download, so they take
  it at their next start or build
- `python3.12.tar.gz.sha256` and `python3.12.BUILD-INFO.txt`, the commit and the versions it was built from

It does not replace a build of a newer commit: the commit in the published `python3.12.BUILD-INFO.txt` must be
an ancestor of the commit of the new build, or the same, in the full history of the repository. A published
build of a commit that is not in it, or without a commit, is replaced, with a warning. It stops, publishing
nothing, when the commit of the new build is not in the history, the history is shallow, the checksum is
wrong, or S3 gives an unclear answer. It needs `s3:ListBucket` on the bucket, so that a missing file reads as
missing, and `s3:GetObject` and `s3:PutObject` on `tools/python/3/`.

The first publication keeps the file it replaces as `python3.12.0.tar.gz` (CPython 3.12.0 with OpenSSL
1.1.1w, built on CentOS 7). The backup is taken only while no `python3.12.BUILD-INFO.txt` is there, so it is
never a published build. To put the old file back:

1. copy `python3.12.0.tar.gz` over `python3.12.tar.gz`
2. remove `python3.12.tar.gz.sha256` and `python3.12.BUILD-INFO.txt`, which describe the new build
3. until a fixed build is ready, keep the workflow from publishing on `develop`: no merge of a change to
   `deploy/dependencies/` or to the workflow, no manual run, no re-run of an earlier run. Each of them
   publishes again.

`test.sh`, which the workflow runs before the build, tests `publish.sh` against a stub `aws` CLI and a scratch
git history: an older build does not replace a newer one, the backup is taken once and never from a published
build, and an unknown commit, a shallow history, an unclear answer of S3 or a wrong checksum publish nothing.

## Update a version

Change its line in `sources.txt`: the version, the URL and the SHA256 of the file the URL serves, from the
project's release page. For a new CPython patch release, check that `patches/` still applies.
