#!/usr/bin/env bash
#
# Installs on a sandbox master what the platform's services on the developer's workstation need of
# it, once install-master.sh has installed the master:
#   - a Docker registry, with a self-signed certificate for the master's private address, which
#     Docker here trusts, deletes enabled, a login for the platform's admin, which Docker here
#     keeps, and, when asked, notifications to the API, as a deployment's registry has them;
#   - one tool in it, built from a Dockerfile given;
#   - the files a run's launch.sh downloads from the platform's distribution (pipe-common, pipe,
#     gpustat and fsbrowser), taken from a published build of the platform and served over HTTP
#     on the master's private address, which the platform's nodes reach; pipe-common from a
#     checkout instead, when one is given.
#
# Runs as root on the master. Safe to run again: a step already done is skipped, and the
# distribution is downloaded again only for another build.
#
# Usage: install-services.sh <private-ip> <tool-dockerfile> <distribution-archive-url>
#          [--pipe-common <archive>] [--notify-url <url> --api-cert <certificate>]
#   <distribution-archive-url>: a cloud-pipeline.<version>.tgz, as the platform's CI publishes it.
#   --pipe-common: a pipe-common.tar.gz packed from a checkout, served in place of the build's.
#   --notify-url: where the registry notifies the API of what is pushed and deleted, with the token
#     copied to REGISTRY_NOTIFY_TOKEN first; --api-cert: the API's certificate there.
set -euo pipefail

REGISTRY_IMAGE=registry:2.8.3
REGISTRY_PORT=5000
REGISTRY_CONTAINER=cp-sandbox-registry
REGISTRY_DATA_DIR=/var/lib/cp-sandbox/registry
REGISTRY_CERT_DIR=/etc/cp-sandbox/registry
REGISTRY_CERT=$REGISTRY_CERT_DIR/registry.crt
REGISTRY_KEY=$REGISTRY_CERT_DIR/registry.key
REGISTRY_CERT_DAYS=3650
# The registry's settings, which hold what it notifies the API with: root's only.
REGISTRY_CONFIG=$REGISTRY_CERT_DIR/config.yml
# The registry's container carries a hash of what it was started with, under this label: a
# registry that runs with other settings, or without the label, is started again.
REGISTRY_LABEL=cp-sandbox-config
# The registry takes a login, so that what it notifies the API of names the user who pushed: the
# API ignores an event without one. Its one user is the platform's admin, whose password is made
# here once; the API gets it from the local state. 40 hex digits, a word the API's log is read
# with blanked out (sandbox.sh services --log): a commit hands it to the run's node.
REGISTRY_USER=pipe_admin
REGISTRY_PASSWORD_FILE=$REGISTRY_CERT_DIR/password.secret
REGISTRY_PASSWORD_BYTES=20
REGISTRY_HTPASSWD=$REGISTRY_CERT_DIR/htpasswd
# The Pythons to make the hash with, the first whose crypt makes bcrypt hashes: the system's, as
# the platform's Python 3.12 on the master has no libcrypt.so.1 to load crypt with, then the one on
# PATH.
CRYPT_PYTHONS=(/usr/bin/python3 python3)
# What the registry notifies the API with, which services copies here over SSH, and the API's
# certificate, which the registry trusts for that.
REGISTRY_NOTIFY_TOKEN=$REGISTRY_CERT_DIR/notify-token.secret
REGISTRY_API_CERT=$REGISTRY_CERT_DIR/api.crt
# How a deployment's registry notifies the API (deploy/docker/cp-docker-registry/*/update_config.sh),
# but with a longer timeout: the API is reached through the tunnel.
REGISTRY_NOTIFY_TIMEOUT=10s
REGISTRY_NOTIFY_THRESHOLD=5
REGISTRY_NOTIFY_BACKOFF=1s
REGISTRY_NOTIFY_IGNORED_MEDIA_TYPES=(application/octet-stream
  application/vnd.docker.container.image.rootfs.diff+x-gtar
  application/vnd.docker.distribution.manifest.list.v2+json
  application/vnd.docker.container.image.v1+json
  application/vnd.docker.image.rootfs.diff.tar.gzip
  application/vnd.docker.image.rootfs.foreign.diff.tar.gzip
  application/vnd.docker.plugin.v1+json)
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
# Where the build's own pipe-common waits, beside it, while the checkout's is served.
PIPE_COMMON_FILE=pipe-common.tar.gz
PIPE_COMMON_PUBLISHED=$PIPE_COMMON_FILE.published
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
  fi
  dir=/etc/docker/certs.d/$ip:$REGISTRY_PORT
  mkdir -p "$dir"
  cp "$REGISTRY_CERT" "$dir/ca.crt"
}

# crypt_python: the first of CRYPT_PYTHONS whose crypt makes bcrypt hashes.
crypt_python() {
  local python
  for python in "${CRYPT_PYTHONS[@]}"; do
    if "$python" -W ignore -c 'import crypt, sys; sys.exit(crypt.METHOD_BLOWFISH not in crypt.methods)' \
      >/dev/null 2>&1; then
      printf '%s\n' "$python"
      return 0
    fi
  done
  die "none of ${CRYPT_PYTHONS[*]} makes bcrypt hashes with its crypt module, which the registry's \
htpasswd needs"
}

# registry_login: the password of the registry's user, made once, and the bcrypt hash of it in
# the registry's htpasswd file, the only hash that file takes; written again only when it does not
# match, so that the registry is not started again for a new salt.
registry_login() {
  local python
  if [[ ! -s $REGISTRY_PASSWORD_FILE ]]; then
    log "Making the password of the registry's user, $REGISTRY_USER."
    (umask 077 && openssl rand -hex "$REGISTRY_PASSWORD_BYTES" >"$REGISTRY_PASSWORD_FILE")
  fi
  python=$(crypt_python)
  "$python" - "$REGISTRY_USER" "$REGISTRY_PASSWORD_FILE" "$REGISTRY_HTPASSWD" <<'EOF'
import os, sys, warnings
warnings.simplefilter("ignore", DeprecationWarning)
import crypt
user, password_file, htpasswd = sys.argv[1:]
with open(password_file) as source:
    password = source.read().strip()
try:
    with open(htpasswd) as source:
        name, hashed = source.read().strip().split(":", 1)
except (OSError, ValueError):
    name, hashed = None, None
if name == user and crypt.crypt(password, hashed) == hashed:
    sys.exit(0)
descriptor = os.open(htpasswd + ".part", os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(descriptor, "w") as out:
    out.write(user + ":" + crypt.crypt(password, crypt.mksalt(crypt.METHOD_BLOWFISH)) + "\n")
os.replace(htpasswd + ".part", htpasswd)
print("Wrote the registry's htpasswd, for " + user + ".")
EOF
}

# registry_curl <curl args...>: curl to the registry, as its user, with its certificate. The
# password goes to curl on its input, not on its command line.
registry_curl() {
  printf 'user = "%s:%s"\n' "$REGISTRY_USER" "$(<"$REGISTRY_PASSWORD_FILE")" |
    curl --config - --silent --fail --cacert "$REGISTRY_CERT" "$@"
}

registry_answers() {
  registry_curl --output /dev/null "https://$1:$REGISTRY_PORT/v2/"
}

# notify_config <ip> <url>: the registry's notifications, to the API at the URL, as a deployment's
# registry sends them: with the token the API takes them as the admin's, and the registry's path in
# the platform, which the API looks the registry up by.
notify_config() {
  local type
  cat <<EOF
notifications:
  endpoints:
    - name: cp-sandbox-api
      disabled: false
      url: $2
      headers:
        Authorization: [Bearer $(tr -d '[:space:]' <"$REGISTRY_NOTIFY_TOKEN")]
        Registry-Path: [$1:$REGISTRY_PORT]
      timeout: $REGISTRY_NOTIFY_TIMEOUT
      threshold: $REGISTRY_NOTIFY_THRESHOLD
      backoff: $REGISTRY_NOTIFY_BACKOFF
      ignoredmediatypes:
EOF
  for type in "${REGISTRY_NOTIFY_IGNORED_MEDIA_TYPES[@]}"; do
    printf '        - %s\n' "$type"
  done
}

# write_registry_config <ip> [<notify-url>]: the registry's settings, as a deployment's registry
# has those that apply here (deploy/docker/cp-docker-registry/*/update_config.sh): its storage on
# the host, deletes enabled, TLS with the registry's certificate, a login, and, with a URL, the
# notifications to the API.
write_registry_config() {
  local ip=$1 url=${2-}
  {
    cat <<EOF
version: 0.1
log:
  fields:
    service: registry
storage:
  cache:
    blobdescriptor: inmemory
  filesystem:
    rootdirectory: /var/lib/registry
  delete:
    enabled: true
http:
  addr: 0.0.0.0:$REGISTRY_PORT
  headers:
    X-Content-Type-Options: [nosniff]
  tls:
    certificate: /certs/registry.crt
    key: /certs/registry.key
health:
  storagedriver:
    enabled: true
    interval: 10s
    threshold: 3
auth:
  htpasswd:
    realm: cp-sandbox-registry
    path: /certs/htpasswd
EOF
    [[ -z $url ]] || notify_config "$ip" "$url"
  } | (umask 077 && cat >"$REGISTRY_CONFIG")
}

# start_registry <ip> [<notify-url>]: the registry, on the host's network, so that nothing stands
# between it and the platform's nodes. Started again when what it runs with has changed: its
# image, its options, its settings, its certificate or its login. With notifications, it trusts the
# API's certificate, and that alone.
start_registry() {
  local ip=$1 url=${2-} hash running
  local -a options=(--detach --restart=always --name "$REGISTRY_CONTAINER" --network host
    --volume "$REGISTRY_DATA_DIR:/var/lib/registry" --volume "$REGISTRY_CERT_DIR:/certs:ro")
  [[ -z $url ]] || options+=(--env "SSL_CERT_FILE=/certs/$(basename "$REGISTRY_API_CERT")")
  options+=("$REGISTRY_IMAGE" /certs/config.yml)
  write_registry_config "$ip" "$url"
  hash=$({ printf '%s\n' "${options[*]}"; cat "$REGISTRY_CONFIG" "$REGISTRY_CERT" "$REGISTRY_HTPASSWD"
    [[ -z $url ]] || cat "$REGISTRY_API_CERT"; } | sha256sum | cut -c1-12)
  running=$(docker inspect -f "{{.State.Running}} {{index .Config.Labels \"$REGISTRY_LABEL\"}}" \
    "$REGISTRY_CONTAINER" 2>/dev/null || true)
  if [[ $running == "true $hash" ]]; then
    log "The registry runs already, with these settings."
  else
    if [[ -n $running ]]; then
      log "Starting the registry again: it does not run, or not with these settings."
    fi
    docker rm -f "$REGISTRY_CONTAINER" >/dev/null 2>&1 || true
    log "Starting the registry, $REGISTRY_IMAGE, on port $REGISTRY_PORT, with deletes enabled, a \
login for $REGISTRY_USER${url:+, and notifications to $url}."
    docker pull --quiet "$REGISTRY_IMAGE" >/dev/null
    mkdir -p "$REGISTRY_DATA_DIR"
    docker run --label "$REGISTRY_LABEL=$hash" "${options[@]}" >/dev/null
  fi
  wait_for "the registry at https://$ip:$REGISTRY_PORT" registry_answers "$ip"
  # Docker here keeps the login, in root's config, for the tool's push and for one made by hand.
  docker login --username "$REGISTRY_USER" --password-stdin "$ip:$REGISTRY_PORT" \
    <"$REGISTRY_PASSWORD_FILE" >/dev/null
}

tool_pushed() {
  registry_curl "https://$1:$REGISTRY_PORT/v2/$TOOL_REPOSITORY/tags/list" | grep -q "\"$TOOL_TAG\""
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

# serve_pipe_common [<archive>]: the distribution's pipe-common.tar.gz is the archive given, packed
# from a checkout, with the build's own kept beside it; without one, it is the build's again.
# .pipe-common says which: "checkout <SHA-256>" or "published".
serve_pipe_common() {
  local archive=${1-} served=$DISTRIBUTION_DIR/$PIPE_COMMON_FILE kept=$DISTRIBUTION_DIR/$PIPE_COMMON_PUBLISHED
  local source=$DISTRIBUTION_DIR/.pipe-common hash
  if [[ -n $archive ]]; then
    hash=$(sha256sum "$archive" | cut -d' ' -f1)
    if [[ -f $kept && -f $source && $(<"$source") == "checkout $hash" ]]; then
      log "The distribution serves this pipe-common.tar.gz of the checkout already."
      return 0
    fi
    [[ -f $kept ]] || mv "$served" "$kept"
    install -m 0644 "$archive" "$served.part"
    mv -f "$served.part" "$served"
    printf 'checkout %s\n' "$hash" >"$source"
    log "The distribution serves pipe-common.tar.gz from the checkout, $hash; the build's is kept as \
$PIPE_COMMON_PUBLISHED."
  elif [[ -f $kept ]]; then
    mv -f "$kept" "$served"
    printf 'published\n' >"$source"
    log "The distribution serves the build's pipe-common.tar.gz again."
  else
    printf 'published\n' >"$source"
  fi
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
  local usage="usage: install-services.sh <private-ip> <tool-dockerfile> <distribution-archive-url> \
[--pipe-common <archive>] [--notify-url <url> --api-cert <certificate>]"
  (($# >= 3)) || die "$usage"
  local ip=$1 dockerfile=$2 url=$3 pipe_common='' notify_url='' api_cert=''
  shift 3
  while (($#)); do
    (($# >= 2)) || die "$usage"
    case $1 in
      --pipe-common) pipe_common=$2 ;;
      --notify-url) notify_url=$2 ;;
      --api-cert) api_cert=$2 ;;
      *) die "$usage" ;;
    esac
    shift 2
  done
  [[ $ip =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "not an IPv4 address: $ip"
  [[ -f $dockerfile ]] || die "no tool Dockerfile at $dockerfile"
  [[ $url == http://* || $url == https://* ]] || die "not an HTTP URL: $url"
  [[ -z $pipe_common || -s $pipe_common ]] || die "no pipe-common archive at $pipe_common"
  [[ -z $notify_url && -z $api_cert || -n $notify_url && -n $api_cert ]] ||
    die "--notify-url and --api-cert go together"
  if [[ -n $notify_url ]]; then
    [[ $notify_url == https://* ]] || die "not an HTTPS URL: $notify_url"
    [[ -s $api_cert ]] || die "no API certificate at $api_cert"
    [[ -s $REGISTRY_NOTIFY_TOKEN ]] || die "no token to notify the API with at $REGISTRY_NOTIFY_TOKEN: \
sandbox.sh services copies it there"
  fi

  registry_certificate "$ip"
  registry_login
  if [[ -n $notify_url ]]; then
    install -m 0644 "$api_cert" "$REGISTRY_API_CERT"
  fi
  start_registry "$ip" "$notify_url"
  push_tool "$ip" "$dockerfile"
  install_distribution "$url"
  serve_pipe_common "$pipe_common"
  serve_distribution "$ip"
  log "The registry is https://$ip:$REGISTRY_PORT, with $TOOL_REPOSITORY:$TOOL_TAG, for \
$REGISTRY_USER; the distribution files are at http://$ip:$DISTRIBUTION_PORT/."
}

main "$@"
