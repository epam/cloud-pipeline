#!/usr/bin/env bash
# shellcheck disable=SC2016 # the stubs' bodies are not for this shell to expand
#
# Checks of install-services.sh, the master's side of sandbox.sh services: run as root, more than
# once, with docker, systemctl and every curl call that would reach the registry, the published
# build or the HTTP server replaced by stubs that record their calls. openssl, tar and python3 are
# real, so this runs in a python image of its own, which sandbox-test.sh starts as root and throws
# away: Amazon Linux 2023, which the master runs, has python3 too.
set -euo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
INSTALL=$(dirname "$TEST_DIR")/host/install-services.sh
DOCKERFILE=$(dirname "$TEST_DIR")/host/tool.Dockerfile

WORK=$(mktemp -d)
STUBS=$WORK/stubs
export FIXTURES=$WORK/fixtures STUB_STATE=$WORK/state STUB_CALLS=$WORK/calls
OUTPUT=$WORK/output
IP=10.0.0.7
URL=https://builds.example.com/builds/develop/cloud-pipeline.0.17.0.1.abc.tgz
FAILURES=0
STATUS=

[[ $(id -u) == 0 ]] || { echo "services-host-test.sh runs as root, in a container of its own" >&2; exit 1; }

check() {   # check <description> <actual> <expected>
  if [[ $2 == "$3" ]]; then
    printf 'ok      %s\n' "$1"
  else
    printf 'FAILED  %s\n        expected: %s\n        actual:   %s\n' "$1" "$3" "$2"
    sed 's/^/        | /' "$OUTPUT"
    FAILURES=$((FAILURES + 1))
  fi
}

# run [args...]: runs install-services.sh with the stubs first on PATH; sets STATUS, and keeps what
# it printed in OUTPUT. Its calls start a fresh record.
run() {
  : >"$STUB_CALLS"
  if PATH=$STUBS:$PATH WAIT_SECONDS=2 POLL_SECONDS=0 bash "$INSTALL" "$@" >"$OUTPUT" 2>&1; then
    STATUS=0
  else
    STATUS=$?
  fi
}

called() {   # called <command>: the recorded calls of it
  grep -E "^$1( |$)" "$STUB_CALLS" || true
}

count() {   # count <text> <file>
  grep -cF -- "$1" "$2" || true
}

stub() {   # stub <name> [<body>]: a command that records its call, then runs the body
  printf '#!/bin/bash\nprintf "%%s\\n" "$(basename "$0") $*" >>"$STUB_CALLS"\n%s\n' "${2:-exit 0}" \
    >"$STUBS/$1"
  chmod +x "$STUBS/$1"
}

# fixture_build <name> <file>...: a published build, as the stubbed download hands it out for a URL
# ending in <name>: bin/pipeline.jar, holding the files given in its static directory.
fixture_build() {
  local name=$1 dir
  shift
  dir=$(mktemp -d)
  mkdir -p "$dir/bin"
  python3 - "$dir/bin/pipeline.jar" "$@" <<'EOF'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as jar:
    jar.writestr("BOOT-INF/classes/application.properties", "not served\n")
    for name in sys.argv[2:]:
        jar.writestr("BOOT-INF/classes/static/" + name, "the " + name + " of the build\n")
EOF
  tar -czf "$FIXTURES/$name" -C "$dir" bin
  rm -rf "$dir"
}

distribution_files() {   # the files served, by name, in order
  find /var/lib/cp-sandbox/distribution -mindepth 1 -maxdepth 1 ! -name '.*' -printf '%f\n' | sort | paste -sd ' '
}

set_up() {
  mkdir -p "$STUBS" "$FIXTURES" "$STUB_STATE"
  # The registry runs once docker run has started it; the tool is in it once pushed.
  stub docker 'case $1 in
  inspect) [[ -f $STUB_STATE/registry-running ]] && echo true || exit 1 ;;
  rm) rm -f "$STUB_STATE/registry-running" ;;
  run) : >"$STUB_STATE/registry-running" ;;
  push) : >"$STUB_STATE/tool-pushed" ;;
esac'
  stub systemctl '[[ $1 != restart && "$1 $2" != "enable --now" ]] || : >"$STUB_STATE/served"'
  stub curl 'url=${!#}
case $url in
  */v2/) [[ -f $STUB_STATE/registry-running ]] ;;
  */tags/list) [[ -f $STUB_STATE/tool-pushed ]] && echo "{\"name\":\"library/centos\",\"tags\":[\"7\"]}" || exit 22 ;;
  */pipe-common.tar.gz) [[ -f $STUB_STATE/served ]] ;;
  *.tgz) [[ -f $FIXTURES/${url##*/} ]] && cat "$FIXTURES/${url##*/}" || exit 22 ;;
  *) exit 7 ;;
esac'
  fixture_build "${URL##*/}" pipe-common.tar.gz pipe gpustat.tar.gz pipe.tar.gz
  fixture_build cloud-pipeline.0.17.0.2.def.tgz pipe-common.tar.gz pipe gpustat.tar.gz pipe.tar.gz fsbrowser.tar.gz
  fixture_build cloud-pipeline.0.17.0.3.bad.tgz pipe-common.tar.gz gpustat.tar.gz
}

test_refuses() {
  run "$IP" "$DOCKERFILE"
  check "install-services.sh needs three arguments" "$STATUS" 1
  run not-an-ip "$DOCKERFILE" "$URL"
  check "install-services.sh refuses an address that is not IPv4" "$STATUS $(count 'not an IPv4 address' "$OUTPUT")" '1 1'
  run "$IP" "$WORK/missing.Dockerfile" "$URL"
  check "install-services.sh refuses a Dockerfile that is not there" "$STATUS $(count 'no tool Dockerfile' "$OUTPUT")" '1 1'
  check "and changes nothing" "$(called docker)$([[ -e /etc/cp-sandbox ]] && echo written)" ''
}

test_install_services() {
  local cert=/etc/cp-sandbox/registry/registry.crt
  run "$IP" "$DOCKERFILE" "$URL"
  check "install-services.sh succeeds" "$STATUS" 0
  check "the registry's certificate is for the master's private address, and its key for root only" \
    "$(openssl x509 -in "$cert" -noout -checkip "$IP" | grep -c 'does match') $(stat -c %a /etc/cp-sandbox/registry/registry.key)" \
    '1 600'
  check "Docker on the master trusts it for the registry's address" \
    "$(cmp -s "$cert" "/etc/docker/certs.d/$IP:5000/ca.crt" && echo trusted)" trusted
  check "the registry runs on the host's network, with its certificate, its data on the host, and a restart policy" \
    "$(called 'docker run' | grep -c -- '--restart=always --name cp-sandbox-registry --network host --volume /var/lib/cp-sandbox/registry:/var/lib/registry --volume /etc/cp-sandbox/registry:/certs:ro --env REGISTRY_HTTP_ADDR=0.0.0.0:5000 --env REGISTRY_HTTP_TLS_CERTIFICATE=/certs/registry.crt --env REGISTRY_HTTP_TLS_KEY=/certs/registry.key registry:2.8.3' || true)" 1
  check "the tool is built from the Dockerfile given, and pushed to the registry" \
    "$(called 'docker build' | sed -E 's# [^ ]+$##') | $(called 'docker push')" \
    "docker build --quiet --tag $IP:5000/library/centos:7 --file $DOCKERFILE | docker push $IP:5000/library/centos:7"
  check "the distribution holds the files launch.sh downloads, as the build has them, and its source" \
    "$(distribution_files) $(cat /var/lib/cp-sandbox/distribution/pipe) $(cat /var/lib/cp-sandbox/distribution/.source)" \
    "gpustat.tar.gz pipe pipe-common.tar.gz pipe.tar.gz the pipe of the build $URL"
  check "the build's other files are left out, and nothing of it is left behind" \
    "$([[ -e /var/lib/cp-sandbox/distribution/application.properties ]] && echo kept)$(find /var/tmp -maxdepth 1 -name 'cp-sandbox-distribution.*' | wc -l)" 0
  check "a systemd unit serves them on the private address, and is started" \
    "$(grep '^ExecStart=' /etc/systemd/system/cp-sandbox-distribution.service) | $(called systemctl | paste -sd ',')" \
    "ExecStart=/usr/bin/python3 -m http.server 8090 --bind $IP --directory /var/lib/cp-sandbox/distribution | systemctl daemon-reload,systemctl enable cp-sandbox-distribution,systemctl restart cp-sandbox-distribution"
  check "install-services.sh says where the registry and the files are" \
    "$(count "The registry is https://$IP:5000, with library/centos:7; the distribution files are at http://$IP:8090/." "$OUTPUT")" 1

  run "$IP" "$DOCKERFILE" "$URL"
  check "install-services.sh succeeds again" "$STATUS" 0
  check "a second run starts, builds, pushes, downloads and restarts nothing" \
    "$(called 'docker run')$(called 'docker build')$(called 'docker push')$(called curl | grep -c '\.tgz$' || true)$(called 'systemctl restart')" 0
  check "and keeps the certificate" "$(count 'is there already' "$OUTPUT")" 1
}

# After test_install_services, which leaves the services installed.
test_install_services_changes() {
  local next=https://builds.example.com/builds/develop/cloud-pipeline.0.17.0.2.def.tgz
  run "$IP" "$DOCKERFILE" "$next"
  check "another build is downloaded, and replaces the files, the optional ones it has included" \
    "$STATUS $(distribution_files) $(cat /var/lib/cp-sandbox/distribution/.source)" \
    "0 fsbrowser.tar.gz gpustat.tar.gz pipe pipe-common.tar.gz pipe.tar.gz $next"

  run "$IP" "$DOCKERFILE" https://builds.example.com/builds/develop/cloud-pipeline.0.17.0.3.bad.tgz
  check "a build without a file launch.sh needs fails, names the file, and leaves the files there" \
    "$STATUS $(count 'holds no BOOT-INF/classes/static/pipe' "$OUTPUT") $(cat /var/lib/cp-sandbox/distribution/.source)" \
    "1 1 $next"
  run "$IP" "$DOCKERFILE" https://builds.example.com/builds/develop/cloud-pipeline.missing.tgz
  check "a build that cannot be downloaded fails, and says so" \
    "$STATUS $(count 'could not take bin/pipeline.jar from' "$OUTPUT")" '1 1'

  cp "$DOCKERFILE" "$WORK/changed.Dockerfile"
  printf 'RUN true\n' >>"$WORK/changed.Dockerfile"
  run "$IP" "$WORK/changed.Dockerfile" "$next"
  check "a Dockerfile that changed builds the tool again, and pushes it" \
    "$STATUS $(called 'docker build' | wc -l) $(called 'docker push' | wc -l)" '0 1 1'
  run "$IP" "$WORK/changed.Dockerfile" "$next"
  check "and once pushed, it is not built again" "$STATUS $(called 'docker build' | wc -l)" '0 0'

  run 10.0.0.8 "$DOCKERFILE" "$next"
  check "another private address gets a certificate of its own, and the registry starts again with it" \
    "$STATUS $(openssl x509 -in /etc/cp-sandbox/registry/registry.crt -noout -checkip 10.0.0.8 | grep -c 'does match') $(called 'docker rm' | head -n 1) $(called 'docker run' | wc -l)" \
    '0 1 docker rm -f cp-sandbox-registry 1'
}

set_up
test_refuses
test_install_services
test_install_services_changes
rm -rf "$WORK"
if ((FAILURES)); then
  printf '%s check(s) failed\n' "$FAILURES"
  exit 1
fi
