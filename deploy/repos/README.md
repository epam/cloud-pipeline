# Package repositories

Runs add the Cloud Pipeline package repository of their OS from `${GLOBAL_DISTRIBUTION_URL}tools/repos/<os>/<version>`
(`CP_REPO_ENABLED`), if the distribution has one for that version. It has the packages that the platform scripts
install and the OS lacks, or has in another version: SGE, LizardFS, blobfuse, gcsfuse and the base packages of a run.
The public distribution has the Ubuntu repositories of 16.04 to 22.04.

| Script | Builds |
|---|---|
| `build-ubuntu-repo.sh` | the apt repository of an Ubuntu release from 20.04 on, e.g. 24.04 or 26.04, in docker containers |
| `build-all.sh` | all the older repositories, with `build-repo-deb.sh` (Debian and Ubuntu, up to 22.04) and `build-repo-rpm.sh` (CentOS 7 and 8). These need a CentOS host with `yum`, and upload to the bucket themselves |
| `build-repo-pypi.sh`, `build-repo-pypi3.sh` | the Python package mirrors. The pypi3 mirror is built by `deploy/dependencies/pypi3` now |
| `build-rocky-draft.sh`, `custom-packages/` | a draft that mirrors the Rocky Linux 8.9 repositories with `rsync`, and the build of a custom `nvme-cli` package |

## `build-ubuntu-repo.sh`

```bash
build-ubuntu-repo.sh --out <dir> [--version 24.04] [--image ubuntu:24.04] [--no-cloud-fuse]
                     [--prev-repo <url> | --no-prev-repo] [--gpg-key <private key file> --gpg-key-id <key id>]
                     [--docker <docker binary>] [--insecure]
aws s3 sync <dir>/ s3://<bucket and path of the distribution URL>/tools/repos/ubuntu/<version>/ --delete
```

It builds `<dir>/dists/stable/main/binary-amd64/Packages(.gz)`, `<dir>/dists/stable/Release` and `<dir>/pool/main`,
in `linux/amd64` containers of `ubuntu:<version>` (or `--image`, which must be that release). It needs docker, and curl
on the host to look up the previous repository. It does not upload. Upload with `--delete`, so that no file of an older
build stays, e.g. an `InRelease` that no longer matches. The script fails if the repository did not land in `<dir>`,
e.g. when the docker daemon runs in a VM that does not share that path.

Ubuntu 16.04 and 18.04 are not supported: the public distribution has their repositories, and their old packages
differ (e.g. blobfuse 1.1.1).

What the repository gets:

- **The packages of the 22.04 repository.** These are the lists of `download-scripts/debian` (common, dev, dind and
  cloudfuse) with the names of the newer releases, plus the Singularity prerequisites (`squashfs-tools`,
  `libseccomp-dev`), and their dependencies that a stock image lacks. A name that the release does not have is skipped
  with a warning, e.g. `libip4tc0`, `python2` on 24.04 and later, or `libxml2` on 26.04.
- **gcsfuse and blobfuse2**, from the Google and Microsoft repositories. `--no-cloud-fuse` leaves them out.
- **From a previous repository:** SGE `8.1.9+dfsg-4+deb9u2` (the default `CP_CAP_SGE_VERSION`), LizardFS 3.12.0 and
  blobfuse 1.3.8, with the old libraries that they need and the release lacks (e.g. `libssl1.0.2`, `libtinfo5`).
  - The previous repository is, by default, the public repository of the same release if there is one, else the
    public 22.04 one. The old libraries of 22.04 do not fit 20.04, for example. If the lookup gets no answer (e.g. no
    network, or a proxy error), the script fails rather than guess.
  - `--prev-repo <url>` names another one, and `--no-prev-repo` leaves these packages out.
  - If any of them cannot be downloaded, the build fails.

Options:

| Option | Default | Use |
|---|---|---|
| `--gpg-key <file> --gpg-key-id <id>` | unsigned | signs the repository (`InRelease`, `Release.gpg`) with a private key that has no passphrase. Use the key whose public half the deployment publishes as `tools/repos/cloud-pipeline.key`. The build fails if the file is not there, or has no key with that ID |
| `--docker <path>` | `docker` from `PATH`, or the `DOCKER` environment variable | another docker binary. Its daemon mounts `--out` and the `--gpg-key` file, so they must be paths on the daemon's host |
| `--insecure` | TLS checks on | turns off the TLS checks of curl and apt for the https downloads, e.g. behind a proxy that replaces the certificates. apt still checks the GPG signatures of the repositories |

Signing:

- A run of a launch.sh with [#4875](https://github.com/epam/cloud-pipeline/issues/4875) adds the repository as
  trusted, so the repository may be unsigned.
- An older launch.sh imports `tools/repos/cloud-pipeline.key` with `apt-key` and adds the repository without
  `[trusted=yes]`. Then only launch.sh's own installs and SGE pass `--allow-unauthenticated`. LizardFS, the
  Singularity prerequisites, blobfuse and the other capability installs fail on an unsigned repository, so sign it.
- Ubuntu 25.10 and later have no `apt-key`, so their runs need a launch.sh with #4875.

A build takes about 6 minutes on an Apple Silicon Mac with amd64 emulation. Repositories of 20.04, 22.04, 24.04 and
26.04, built with this script or an earlier version of it, were checked in a stock image of each release, with the
repository as the only apt source:

- the launch.sh base packages, and the common, dev and dind packages, installed;
- the Singularity prerequisites and LizardFS installed with `-t stable`;
- blobfuse 1, blobfuse2 and gcsfuse installed;
- the SGE master set up with the default `CP_CAP_SGE_VERSION`, and a `qsub` job ran.

Known limits:

- On 26.04, `fuse` is the transitional package of fuse3, so blobfuse 1 does not run without `libfuse2t64`. The
  repository has it, but `install_azure_fuse_blobfuse` installs only `blobfuse` and `fuse`.
- The repository is a snapshot of its release. apt prefers its versions for the `-t stable` installs (the Singularity
  prerequisites, LizardFS). Rebuild it when such an install fails on an image with newer libraries.

`test-build-ubuntu-repo.sh` tests the options of the script with a stub docker and a stub curl, and checks that the
scripts of the containers parse. It needs bash only, and no CI workflow runs it:

```bash
bash deploy/repos/test-build-ubuntu-repo.sh
```
