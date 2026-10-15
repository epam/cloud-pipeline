#!/usr/bin/env bash
#
# Installs on a sandbox master what the platform's services on the developer's workstation need of
# it, once install-master.sh has installed the master:
#   - a Docker registry, with a self-signed certificate for the master's private address, which
#     Docker here trusts;
#   - one tool in it, built from a Dockerfile given;
#   - the files a run's launch.sh downloads from the platform's distribution (pipe-common, pipe,
#     gpustat and fsbrowser), taken from a published build of the platform and served over HTTP
#     on the master's private address, which the platform's nodes reach.
#
# Runs as root on the master. Safe to run again: a step already done is skipped, and the
# distribution is downloaded again only for another build.
#
# Usage: install-services.sh <private-ip> <tool-dockerfile> <distribution-archive-url>
#   <distribution-archive-url>: a cloud-pipeline.<version>.tgz, as the platform's CI publishes it.
set -euo pipefail

REGISTRY_IMAGE=registry:2.8.3
REGISTRY_PORT=5000
REGISTRY_CONTAINER=cp-sandbox-registry
REGISTRY_DATA_DIR=/var/lib/cp-sandbox/registry
REGISTRY_CERT_DIR=/etc/cp-sandbox/registry
REGISTRY_CERT=$REGISTRY_CERT_DIR/registry.crt
REGISTRY_KEY=$REGISTRY_CERT_DIR/registry.key
REGISTRY_CERT_DAYS=3650
TOOL_REPOSITORY=library/centos
TOOL_TAG=7
# The hash of the Dockerfile the pushed tool was built from: another one builds it again.
TOOL_BUILT_FROM=/var/lib/cp-sandbox/tool.sha256
DISTRIBUTION_PORT=8090
DISTRIBUTION_DIR=/var/lib/cp-sandbox/distribution
DISTRIBUTION_SERVICE=cp-sandbox-distribution
DISTRIBUTION_UNIT=/etc/systemd/system/$DISTRIBUTION_SERVICE.service
# The files in the API jar's static directory that launch.sh downloads; those marked optional are
# fetched only when a run asks for them.
DISTRIBUTION_FILES=(pipe-common.tar.gz pipe gpustat.tar.gz)
DISTRIBUTION_OPTIONAL_FILES=(pipe.tar.gz fsbrowser.tar.gz)
DISTRIBUTION_JAR=bin/pipeline.jar
DISTRIBUTION_JAR_STATIC=BOOT-INF/classes/static
DOCKER_HOME=/var/cloud-pipeline/docker
WAIT_SECONDS=${WAIT_SECONDS:-60}
POLL_SECONDS=${POLL_SECONDS:-2}

export PATH=$PATH:$DOCKER_HOME

log() {
  printf '%s\n' "$*"
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

# wait_for <description> <command...>: until the command succeeds, up to WAIT_SECONDS.
wait_for() {
  local what=$1 deadline=$((SECONDS + WAIT_SECONDS))
  shift
  until "$@" >/dev/null 2>&1; do
    ((SECONDS < deadline)) || die "$what: not in $WAIT_SECONDS seconds"
    sleep "$POLL_SECONDS"
  done
}

# The certificate names the master by its private address, which the registry's path in the
# platform holds, and by its host name.
registry_certificate() {
  local ip=$1 dir
  if [[ -f $REGISTRY_CERT && -f $REGISTRY_KEY ]] && openssl x509 -in "$REGISTRY_CERT" -noout \
    -checkip "$ip" | grep -q 'does match'; then
    log "The registry's certificate for $ip is there already."
  else
    log "Making the registry's certificate for $ip."
    install -d -m 0700 "$REGISTRY_CERT_DIR"
    (umask 077 && openssl req -x509 -newkey rsa:2048 -nodes -days "$REGISTRY_CERT_DAYS" \
      -subj "/CN=$ip" -addext "subjectAltName=IP:$ip,DNS:$(hostname -f),DNS:localhost,IP:127.0.0.1" \
      -keyout "$REGISTRY_KEY" -out "$REGISTRY_CERT" 2>/dev/null)
    chmod 0644 "$REGISTRY_CERT"
    # A registry that runs serves the old certificate until it starts again.
    docker rm -f "$REGISTRY_CONTAINER" >/dev/null 2>&1 || true
  fi
  dir=/etc/docker/certs.d/$ip:$REGISTRY_PORT
  mkdir -p "$dir"
  cp "$REGISTRY_CERT" "$dir/ca.crt"
}

registry_answers() {
  curl --silent --fail --cacert "$REGISTRY_CERT" --output /dev/null "https://$1:$REGISTRY_PORT/v2/"
}

# On the host's network, so that nothing stands between it and the platform's nodes.
start_registry() {
  local ip=$1
  if [[ $(docker inspect -f '{{.State.Running}}' "$REGISTRY_CONTAINER" 2>/dev/null || true) == true ]]; then
    log "The registry runs already."
  else
    docker rm -f "$REGISTRY_CONTAINER" >/dev/null 2>&1 || true
    log "Starting the registry, $REGISTRY_IMAGE, on port $REGISTRY_PORT."
    docker pull --quiet "$REGISTRY_IMAGE" >/dev/null
    mkdir -p "$REGISTRY_DATA_DIR"
    docker run --detach --restart=always --name "$REGISTRY_CONTAINER" --network host \
      --volume "$REGISTRY_DATA_DIR:/var/lib/registry" --volume "$REGISTRY_CERT_DIR:/certs:ro" \
      --env "REGISTRY_HTTP_ADDR=0.0.0.0:$REGISTRY_PORT" \
      --env REGISTRY_HTTP_TLS_CERTIFICATE=/certs/registry.crt \
      --env REGISTRY_HTTP_TLS_KEY=/certs/registry.key \
      "$REGISTRY_IMAGE" >/dev/null
  fi
  wait_for "the registry at https://$ip:$REGISTRY_PORT" registry_answers "$ip"
}

tool_pushed() {
  curl --silent --fail --cacert "$REGISTRY_CERT" \
    "https://$1:$REGISTRY_PORT/v2/$TOOL_REPOSITORY/tags/list" | grep -q "\"$TOOL_TAG\""
}

push_tool() {
  local ip=$1 dockerfile=$2 image context hash
  image=$ip:$REGISTRY_PORT/$TOOL_REPOSITORY:$TOOL_TAG
  hash=$(sha256sum "$dockerfile" | cut -d' ' -f1)
  if tool_pushed "$ip" && [[ -f $TOOL_BUILT_FROM && $(<"$TOOL_BUILT_FROM") == "$hash" ]]; then
    log "The tool $image is in the registry already."
    return 0
  fi
  log "Building the tool $image, and pushing it to the registry."
  context=$(mktemp -d)
  docker build --quiet --tag "$image" --file "$dockerfile" "$context" >/dev/null
  rm -rf "$context"
  docker push "$image" >/dev/null
  tool_pushed "$ip" || die "the registry does not list $image after the push"
  mkdir -p "$(dirname "$TOOL_BUILT_FROM")"
  printf '%s\n' "$hash" >"$TOOL_BUILT_FROM"
}

# jar_extract <jar> <directory> <name>...: the files of the jar's static directory, with the names
# given, into the directory; names it does not hold are left out. Writes the names extracted.
jar_extract() {
  python3 - "$@" <<'EOF'
import os, sys, zipfile
jar, target, names = sys.argv[1], sys.argv[2], sys.argv[3:]
prefix = os.environ["STATIC"] + "/"
with zipfile.ZipFile(jar) as archive:
    members = set(archive.namelist())
    for name in names:
        if prefix + name in members:
            with archive.open(prefix + name) as source, open(os.path.join(target, name), "wb") as out:
                while True:
                    chunk = source.read(1 << 20)
                    if not chunk:
                        break
                    out.write(chunk)
            print(name)
EOF
}

# Only bin/pipeline.jar is kept of the archive, and of it only the files launch.sh downloads. The
# source URL is kept beside them, so that the same build is not downloaded twice.
install_distribution() {
  local url=$1 work file
  local -a extracted
  if [[ -f $DISTRIBUTION_DIR/.source && $(<"$DISTRIBUTION_DIR/.source") == "$url" ]]; then
    log "The distribution files of $url are there already."
    return 0
  fi
  log "Downloading the distribution files from $url."
  work=$(mktemp -d /var/tmp/cp-sandbox-distribution.XXXXXX)
  if ! curl --silent --show-error --fail --location "$url" |
    tar -xzf - -C "$work" "$DISTRIBUTION_JAR"; then
    rm -rf "$work"
    die "could not take $DISTRIBUTION_JAR from $url"
  fi
  mkdir -p "$work/files"
  mapfile -t extracted < <(STATIC=$DISTRIBUTION_JAR_STATIC jar_extract "$work/$DISTRIBUTION_JAR" \
    "$work/files" "${DISTRIBUTION_FILES[@]}" "${DISTRIBUTION_OPTIONAL_FILES[@]}")
  for file in "${DISTRIBUTION_FILES[@]}"; do
    if [[ ! -s $work/files/$file ]]; then
      rm -rf "$work"
      die "$DISTRIBUTION_JAR of $url holds no $DISTRIBUTION_JAR_STATIC/$file"
    fi
  done
  rm -rf "$DISTRIBUTION_DIR"
  mkdir -p "$(dirname "$DISTRIBUTION_DIR")"
  mv "$work/files" "$DISTRIBUTION_DIR"
  chmod 0755 "$DISTRIBUTION_DIR"
  printf '%s\n' "$url" >"$DISTRIBUTION_DIR/.source"
  rm -rf "$work"
  log "Took ${extracted[*]} from $url."
}

distribution_answers() {
  curl --silent --fail --head --output /dev/null "http://$1:$DISTRIBUTION_PORT/pipe-common.tar.gz"
}

serve_distribution() {
  local ip=$1 unit
  unit="[Unit]
Description=The dev sandbox's distribution files, for the platform's runs
After=network-online.target
Wants=network-online.target

[Service]
ExecStart=/usr/bin/python3 -m http.server $DISTRIBUTION_PORT --bind $ip --directory $DISTRIBUTION_DIR
Restart=always

[Install]
WantedBy=multi-user.target"
  if [[ ! -f $DISTRIBUTION_UNIT || $(<"$DISTRIBUTION_UNIT") != "$unit" ]]; then
    printf '%s\n' "$unit" >"$DISTRIBUTION_UNIT"
    systemctl daemon-reload
    systemctl enable "$DISTRIBUTION_SERVICE"
    systemctl restart "$DISTRIBUTION_SERVICE"
  else
    systemctl enable --now "$DISTRIBUTION_SERVICE"
  fi
  wait_for "the distribution files at http://$ip:$DISTRIBUTION_PORT/" distribution_answers "$ip"
}

main() {
  (($# == 3)) || die "usage: install-services.sh <private-ip> <tool-dockerfile> <distribution-archive-url>"
  local ip=$1 dockerfile=$2 url=$3
  [[ $ip =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "not an IPv4 address: $ip"
  [[ -f $dockerfile ]] || die "no tool Dockerfile at $dockerfile"
  [[ $url == http://* || $url == https://* ]] || die "not an HTTP URL: $url"

  registry_certificate "$ip"
  start_registry "$ip"
  push_tool "$ip" "$dockerfile"
  install_distribution "$url"
  serve_distribution "$ip"
  log "The registry is https://$ip:$REGISTRY_PORT, with $TOOL_REPOSITORY:$TOOL_TAG; the \
distribution files are at http://$ip:$DISTRIBUTION_PORT/."
}

main "$@"
