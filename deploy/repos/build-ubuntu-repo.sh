#!/usr/bin/env bash

# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Builds the Cloud Pipeline apt repository for an Ubuntu release (default 24.04), in the layout that runs expect at
#   ${GLOBAL_DISTRIBUTION_URL}tools/repos/ubuntu/<version>/
# Signing: with the launch.sh of issue #4875, a run adds the repository as "deb [trusted=yes] <url> stable main", so it
# may be unsigned. An older launch.sh adds "deb <url> stable main" and imports tools/repos/cloud-pipeline.key with
# apt-key (gone on Ubuntu 25.10+); there, sign the repository with that key (--gpg-key), as only launch.sh's own
# installs and SGE pass --allow-unauthenticated, while LizardFS, Singularity, blobfuse and others fail on an unsigned one.
#
# The packages follow the lists the 22.04 repository was built from (develop dded98401e, deploy/repos/
# download-scripts/debian: common, dev, dind, cloudfuse), with the names of the newer release, plus the Singularity
# prerequisites (squashfs-tools, libseccomp-dev). Their dependencies are downloaded as a stock ubuntu:<version> image
# needs them, and a package that the release does not have is skipped with a warning.
# SGE (gridengine 8.1.9+dfsg-4*, the default CP_CAP_SGE_VERSION), LizardFS 3.12.0 and blobfuse 1.3.8 come from a
# previous repository (--prev-repo), with the old libraries they need that the release lacks. By default it is the
# public repository of the same release if there is one (20.04, 22.04), else the public ubuntu/22.04 one.
# Ubuntu 20.04 and later only: the public distribution has the repositories of 16.04 and 18.04.
#
# Usage:
#   build-ubuntu-repo.sh --out <dir> [--version 24.04] [--image ubuntu:24.04] [--no-cloud-fuse]
#                        [--prev-repo <url of the previous repository> | --no-prev-repo]
#                        [--gpg-key <private key file> --gpg-key-id <key id>] [--docker <docker binary>]
#                        [--insecure]
# Then upload, with --delete so that no file of an older build (e.g. an InRelease) stays:
#   aws s3 sync <dir>/ s3://<bucket and path of the distribution URL>/tools/repos/ubuntu/<version>/ --delete
#
# Needs docker, and curl on the host to look up the previous repository. It runs the downloads in linux/amd64 containers (the runs' architecture). The docker binary is
# "docker" from PATH, or the one that --docker or the DOCKER environment variable names, e.g. --docker /usr/local/bin/docker.
# --insecure turns off the TLS checks of curl (-k) and apt for the downloads over https, e.g. behind a proxy that
# replaces the certificates (Zscaler). apt still checks the GPG signatures of the repositories.
set -euo pipefail

VERSION="24.04"
IMAGE=""
OUT=""
CLOUD_FUSE="true"
PUBLIC_REPOS="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/ubuntu"
PREV_REPO="default"
GPG_KEY=""
GPG_KEY_ID=""
DOCKER="${DOCKER:-docker}"
INSECURE="false"
# value <option> <next argument>: fails if the option has no value
value() { [ $# -ge 2 ] && [ -n "$2" ] && [[ "$2" != --* ]] || { echo "[ERROR] $1 needs a value"; exit 1; }; }
while [ $# -gt 0 ]; do
    case "$1" in
        --version) value "$@"; VERSION="$2"; shift 2 ;;
        --image) value "$@"; IMAGE="$2"; shift 2 ;;
        --out) value "$@"; OUT="$2"; shift 2 ;;
        --no-cloud-fuse) CLOUD_FUSE="false"; shift ;;
        --prev-repo) value "$@"; PREV_REPO="$2"; shift 2 ;;
        --no-prev-repo) PREV_REPO=""; shift ;;
        --gpg-key) value "$@"; GPG_KEY="$2"; shift 2 ;;
        --gpg-key-id) value "$@"; GPG_KEY_ID="$2"; shift 2 ;;
        --docker) value "$@"; DOCKER="$2"; shift 2 ;;
        --insecure) INSECURE="true"; shift ;;
        *) echo "[ERROR] Unknown option $1"; exit 1 ;;
    esac
done
IMAGE="${IMAGE:-ubuntu:$VERSION}"
[ "$OUT" ] || { echo "[ERROR] --out <dir> is required"; exit 1; }
if [[ ! "$VERSION" =~ ^([0-9]+)\.[0-9]+$ ]] || [ "${BASH_REMATCH[1]}" -lt 20 ]; then
    echo "[ERROR] --version $VERSION: Ubuntu 20.04 and later only, e.g. 24.04"; exit 1
fi
if [ "$GPG_KEY" ] && [ -z "$GPG_KEY_ID" ]; then echo "[ERROR] --gpg-key needs --gpg-key-id"; exit 1; fi
if [ "$GPG_KEY_ID" ] && [ -z "$GPG_KEY" ]; then echo "[ERROR] --gpg-key-id needs --gpg-key"; exit 1; fi
if [ "$GPG_KEY" ]; then
    [ -f "$GPG_KEY" ] || { echo "[ERROR] --gpg-key $GPG_KEY: no such file"; exit 1; }
    GPG_KEY="$(cd "$(dirname "$GPG_KEY")" && pwd)/$(basename "$GPG_KEY")"
fi
command -v "$DOCKER" > /dev/null || { echo "[ERROR] docker is not found: $DOCKER (set it with --docker <path>)"; exit 1; }
if [ "$PREV_REPO" == "default" ]; then
    # The old libraries must suit the release: the 22.04 ones need a newer glibc than 20.04 has
    command -v curl > /dev/null || { echo "[ERROR] curl is not found: it looks up the previous repository, or set --prev-repo"; exit 1; }
    code="$(curl -s -o /dev/null -w "%{http_code}" -I $([ "$INSECURE" == "true" ] && echo "-k") \
                 "$PUBLIC_REPOS/$VERSION/dists/stable/Release")" || true
    case "$code" in
        200) PREV_REPO="$PUBLIC_REPOS/$VERSION" ;;
        403|404) PREV_REPO="$PUBLIC_REPOS/22.04" ;;
        *) echo "[ERROR] Cannot check $PUBLIC_REPOS/$VERSION (HTTP code ${code:-none}): set --prev-repo, or --insecure behind a proxy"
           exit 1 ;;
    esac
fi

# Runs a command in a container, and shows the end of its output only if it fails
# With --insecure: curl -k ($CURL_K) and no TLS checks in apt, in a container
TLS_SETUP='CURL_K=""; if [ "$INSECURE" == "true" ]; then CURL_K="-k"; mkdir -p /etc/apt/apt.conf.d; printf "Acquire::https::Verify-Peer \"false\";\nAcquire::https::Verify-Host \"false\";\n" > /etc/apt/apt.conf.d/99cp-insecure; fi'
LOGGED='logged() { "$@" > /tmp/logged.log 2>&1 && return 0; rc=$?; echo "[ERROR] \"$*\" failed with exit code $rc, its last output:"; tail -40 /tmp/logged.log; return $rc; }'

mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
DEBS="$OUT/.debs"
mkdir -p "$DEBS"

# The package lists. A name that ends with "*" stands for every package whose name starts so. Changes from the 22.04
# lists for the newer releases: python2 is skipped where the release has none (24.04 and later), libncurses5-dev is
# libncurses-dev, libfuse2 is libfuse2t64 and libip4tc0 is libip4tc2 where the release renamed them (both names are
# tried), fuse3 and libfuse3-dev come next to fuse and libfuse-dev, and blobfuse2 next to blobfuse.
COMMON="sudo locales python2 git curl wget fuse fuse3 tzdata acl coreutils libltdl7 openssh-server gnupg lsb-release
        nfs-common cifs-utils nfs-kernel-server tcl-dev samba* procps pciutils screen vim nano htop gettext
        bash-completion libfuse2 libfuse2t64 libexpat1 ucf libxml2"
DEV="automake autotools-dev fuse g++ gcc libcurl4-gnutls-dev libfuse-dev libfuse3-dev libssl-dev libxml2-dev make
     pkg-config zlib1g-dev libncurses-dev gettext-base libjsoncpp-dev"
DIND="btrfs-progs e2fsprogs iptables iproute2 xfsprogs xz-utils pigz kmod libkmod2 libip4tc0 libip4tc2"
# singularity_setup installs these with "apt-get install -t stable", which also needs a source whose release is
# "stable", i.e. this repository
SINGULARITY="squashfs-tools libseccomp-dev"
# From the previous repository, pinned: the version the platform scripts expect
PREV_PKGS="gridengine-common=8.1.9+dfsg-4* gridengine-client=8.1.9+dfsg-4* gridengine-master=8.1.9+dfsg-4*
           gridengine-exec=8.1.9+dfsg-4* lizardfs-common=3.12.0 lizardfs-client=3.12.0 lizardfs-master=3.12.0
           lizardfs-chunkserver=3.12.0 blobfuse=1.3.8"

# 1. The Ubuntu archive packages, in a stock image: apt downloads every dependency that the stock image lacks
echo "[INFO] Downloading the Ubuntu $VERSION packages in $IMAGE"
"$DOCKER" run --rm --platform linux/amd64 -v "$DEBS:/debs" -e PKGS="$COMMON $DEV $DIND $SINGULARITY" \
           -e WANT_VERSION="$VERSION" "$IMAGE" bash -c '
    set -e
    image_version="$(. /etc/os-release; echo "$VERSION_ID")"
    if [ "$image_version" != "$WANT_VERSION" ]; then
        echo "[ERROR] The image is Ubuntu $image_version, not $WANT_VERSION: set --version or --image"; exit 1
    fi
    # The files of an earlier build that stopped, owned by root
    rm -f /debs/*.deb
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    set -f
    for p in $PKGS; do
        if [[ "$p" == *"*" ]]; then
            for q in $(apt-cache pkgnames "${p%\*}" | sort); do
                apt-get install --download-only -y -qq "$q" > /dev/null 2>&1 || echo "[WARN] $q cannot be downloaded, skipping it"
            done
            continue
        fi
        if apt-cache show "$p" > /dev/null 2>&1; then
            apt-get install --download-only -y -qq "$p" > /dev/null \
                || echo "[WARN] $p cannot be downloaded, skipping it"
        else
            echo "[WARN] $p is not in this release, skipping it"
        fi
    done
    set +f
    cp /var/cache/apt/archives/*.deb /debs/
'

# 2. gcsfuse (Google) and blobfuse2 (Microsoft), from their own repositories
if [ "$CLOUD_FUSE" == "true" ]; then
    echo "[INFO] Downloading gcsfuse and blobfuse2"
    "$DOCKER" run --rm --platform linux/amd64 -v "$DEBS:/debs" -e LOGGED="$LOGGED" \
               -e TLS_SETUP="$TLS_SETUP" -e INSECURE="$INSECURE" "$IMAGE" bash -c '
        set -e
        eval "$LOGGED"
        eval "$TLS_SETUP"
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -qq
        logged apt-get install -y -qq curl gnupg lsb-release ca-certificates
        codename="$(lsb_release -cs)"; version="$(lsb_release -rs)"
        mkdir -p /etc/apt/keyrings
        if curl $CURL_K -fsSL -o /tmp/gcsfuse.key https://packages.cloud.google.com/apt/doc/apt-key.gpg \
                && gpg --dearmor -o /etc/apt/keyrings/gcsfuse.gpg /tmp/gcsfuse.key; then
            echo "deb [signed-by=/etc/apt/keyrings/gcsfuse.gpg] https://packages.cloud.google.com/apt gcsfuse-$codename main" \
                > /etc/apt/sources.list.d/gcsfuse.list
        else
            echo "[WARN] Cannot get the key of the gcsfuse repository"
        fi
        if curl $CURL_K -fsSL -o /tmp/ms.deb "https://packages.microsoft.com/config/ubuntu/$version/packages-microsoft-prod.deb"; then
            logged dpkg -i /tmp/ms.deb
        else
            echo "[WARN] Cannot get packages-microsoft-prod.deb of Ubuntu $version"
        fi
        apt-get update -qq || true
        rm -f /var/cache/apt/archives/*.deb
        for p in gcsfuse blobfuse2; do
            apt-get install --download-only -y -qq "$p" > /dev/null \
                || echo "[WARN] $p cannot be downloaded, skipping it"
        done
        cp /var/cache/apt/archives/*.deb /debs/ 2>/dev/null || true
    '
fi

# 3. SGE, LizardFS and blobfuse 1 from the previous repository, with the old libraries that the release lacks. Where
# the release renamed libfuse2 to libfuse2t64, libfuse2t64 is asked for too: else apt takes the old libfuse2 of the
# previous repository, which conflicts with libfuse2t64
if [ "$PREV_REPO" ]; then
    echo "[INFO] Downloading SGE, LizardFS and blobfuse 1 from $PREV_REPO"
    "$DOCKER" run --rm --platform linux/amd64 -v "$DEBS:/debs" -e PREV_REPO="$PREV_REPO" -e PKGS="$PREV_PKGS" \
               -e LOGGED="$LOGGED" -e TLS_SETUP="$TLS_SETUP" -e INSECURE="$INSECURE" "$IMAGE" bash -c '
        set -e
        eval "$LOGGED"
        eval "$TLS_SETUP"
        export DEBIAN_FRONTEND=noninteractive
        # The stock image has no CA certificates, and apt only warns when it cannot reach an https repository
        apt-get update -qq
        logged apt-get install -y -qq ca-certificates
        echo "deb [trusted=yes] $PREV_REPO stable main" > /etc/apt/sources.list.d/cp-prev.list
        apt-get update -qq
        rm -f /var/cache/apt/archives/*.deb
        # In one call: pinned one at a time, a package gets its dependencies of the same source package (e.g.
        # gridengine-common) in the newer version of the release, which do not fit
        extra=""
        if apt-cache policy libfuse2t64 2>/dev/null | grep -q "Candidate: [^(]"; then extra="libfuse2t64"; fi
        set -f
        apt-get install --download-only -y -qq --allow-unauthenticated $PKGS $extra > /dev/null \
            || { echo "[ERROR] Cannot download from $PREV_REPO: $PKGS"; exit 1; }
        set +f
        cp /var/cache/apt/archives/*.deb /debs/
    '
fi

# 4. The repository: pool/main/<file>.deb, dists/stable/main/binary-amd64/Packages(.gz), dists/stable/Release.
# The file names lose the "+" and "%3a" (epoch) characters: S3 reads a "+" in a URL path as a space.
echo "[INFO] Building the repository in $OUT"
GPG_MOUNT=()
if [ "$GPG_KEY" ]; then
    GPG_MOUNT=(-v "$GPG_KEY:/gpg.key:ro")
fi
"$DOCKER" run --rm --platform linux/amd64 -v "$OUT:/repo" ${GPG_MOUNT[@]+"${GPG_MOUNT[@]}"} \
           -e GPG_KEY_ID="$GPG_KEY_ID" -e VERSION="$VERSION" -e LOGGED="$LOGGED" "$IMAGE" bash -c '
    set -e
    eval "$LOGGED"
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    logged apt-get install -y -qq dpkg-dev apt-utils gnupg
    cd /repo
    rm -rf pool dists
    mkdir -p pool/main dists/stable/main/binary-amd64
    for f in .debs/*.deb; do
        name="$(basename "$f" | sed -e "s/%3a/_/g" -e "s/+/_/g")"
        mv "$f" "pool/main/$name"
    done
    rmdir .debs
    dpkg-scanpackages --multiversion pool /dev/null > dists/stable/main/binary-amd64/Packages 2>/dev/null
    gzip -9 -k -f dists/stable/main/binary-amd64/Packages
    apt-ftparchive \
        -o APT::FTPArchive::Release::Origin="Cloud Pipeline" \
        -o APT::FTPArchive::Release::Label="Cloud Pipeline" \
        -o APT::FTPArchive::Release::Suite=stable \
        -o APT::FTPArchive::Release::Codename=stable \
        -o APT::FTPArchive::Release::Architectures=amd64 \
        -o APT::FTPArchive::Release::Components=main \
        -o APT::FTPArchive::Release::Description="Cloud Pipeline packages for Ubuntu $VERSION" \
        release dists/stable > /tmp/Release
    mv /tmp/Release dists/stable/Release
    if [ "$GPG_KEY_ID" ]; then
        [ -f /gpg.key ] || { echo "[ERROR] The key file is not mounted at /gpg.key"; exit 1; }
        export GNUPGHOME="$(mktemp -d)"
        gpg --batch --import /gpg.key
        # -u fails on a key that is not there (--default-key would take another one)
        gpg --batch --yes -u "$GPG_KEY_ID" --clearsign -o dists/stable/InRelease dists/stable/Release
        gpg --batch --yes -u "$GPG_KEY_ID" -abs -o dists/stable/Release.gpg dists/stable/Release
        rm -rf "$GNUPGHOME"
        echo "[INFO] Signed with $GPG_KEY_ID"
    else
        echo "[WARN] Not signed: fine for runs with the #4875 launch.sh; with an older one, only the installs with --allow-unauthenticated work"
    fi
    echo "[INFO] $(ls pool/main | wc -l) packages"
'
# The containers write through the docker daemon: a daemon that does not see the path (e.g. one in a VM that shares only
# the home folder) leaves it empty here
if [ ! -f "$OUT/dists/stable/Release" ] || ! ls "$OUT"/pool/main/*.deb > /dev/null 2>&1; then
    echo "[ERROR] $OUT has no repository: the docker daemon may not see this path, use one that it shares"
    exit 1
fi
echo "[INFO] Done. Upload it with:"
echo "  aws s3 sync $OUT/ s3://<bucket and path of the distribution URL>/tools/repos/ubuntu/$VERSION/ --delete"
