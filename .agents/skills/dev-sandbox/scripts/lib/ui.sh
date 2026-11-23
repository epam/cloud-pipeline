# shellcheck shell=bash
# Host side of sandbox.sh ui: the platform's GUI, client/ of this checkout, run by its dev server
# (npm start) in a container on the sandbox's network, against the API that services runs there.
# The dev server and its proxy, client/scripts/dev-proxy.js, listen on every address in the
# container, and their ports are published on 127.0.0.1 only: the proxy makes every request as the
# platform's admin. Sourced by sandbox.sh after common.sh, host.sh, tunnel.sh and services.sh.
# bash 3.2, like them.

# The ports of verify-client-live, which stay clear of a developer's own npm start.
UI_PORT=3100
UI_PROXY_PORT=9199
# The API on the sandbox's network, as services runs it, under the path the GUI is served on.
UI_API_URL=https://api:9999/pipeline/
UI_PATH=/pipeline/
# The Node version the GUI runs on, as the tag of Node's image.
UI_NVMRC=client/.nvmrc
UI_ENTRYPOINT=/usr/local/bin/ui-entrypoint.sh
UI_TOKEN_FILE=/run/cp-sandbox/admin-token.secret
# npm's cache for npm ci, in the local state root, which the developer owns.
NPM_CACHE_DIR_NAME=.npm-cache
UI_WAIT_SECONDS=1200
UI_POLL_SECONDS=5
UI_LOG_LINES=40
# What the dev server logs once a compile has ended, as react-dev-utils' createCompiler words it.
UI_COMPILED_PATTERN='Compiled successfully|Compiled with warnings|Failed to compile'

# ui_image: the Node image of client/.nvmrc.
ui_image() {
  local version
  version=$(tr -d '[:space:]' <"$REPO_ROOT/$UI_NVMRC" 2>/dev/null || true)
  [ -n "$version" ] || die "$UI_NVMRC in $REPO_ROOT names no Node version"
  printf 'node:%s\n' "$version"
}

# install_client_modules <image>: client/node_modules, installed with npm ci when it is missing, in
# the Node image, as the calling user, so that what it writes in the checkout stays the user's.
install_client_modules() {
  local client=$REPO_ROOT/client cache=$STATE_ROOT/$NPM_CACHE_DIR_NAME
  [ ! -d "$client/node_modules" ] || return 0
  log "Installing client/node_modules with npm ci, in $1: several minutes, once for each checkout."
  if [ "$HOST_DRY_RUN" = 0 ]; then
    mkdir -p "$cache"
  fi
  docker_change run --rm --user "$(id -u):$(id -g)" --volume "$client:$client" \
    --volume "$cache:$cache" --env HOME=/tmp --env "npm_config_cache=$cache" --workdir "$client" \
    "$1" npm ci >&2 || die "npm ci did not install client/node_modules"
}

# ui_progress <log>: compiled, failed or starting, as the dev server's log tells of its last
# compile, or that none has ended yet.
ui_progress() {
  case $(printf '%s\n' "$1" | { grep -oE "$UI_COMPILED_PATTERN" || true; } | tail -n 1) in
    'Failed to compile') printf 'failed\n' ;;
    Compiled*) printf 'compiled\n' ;;
    *) printf 'starting\n' ;;
  esac
}

# show_ui_log <name>: the end of the dev server's log, on stderr. It holds no secret.
show_ui_log() {
  log "The last lines of the log of $(service_container "$1" ui):"
  docker logs --tail "$UI_LOG_LINES" "$(service_container "$1" ui)" 2>&1 | sed 's/^/  | /' >&2 || true
}

# wait_for_ui <name>: until a compile of the dev server's has ended, up to UI_WAIT_SECONDS. Fails,
# with the end of its log, when the last compile failed, when the container stops, or when no
# compile ends in time; the container stays.
wait_for_ui() {
  local name=$1 container deadline progress
  [ "$HOST_DRY_RUN" = 0 ] || return 0
  container=$(service_container "$name" ui)
  deadline=$((SECONDS + UI_WAIT_SECONDS))
  log "Waiting for the GUI to compile, up to $((UI_WAIT_SECONDS / 60)) minutes: the first compile takes several."
  while :; do
    progress=$(ui_progress "$(docker logs "$container" 2>&1 || true)")
    [ "$progress" = starting ] || break
    if [ "$(container_state "$name" ui)" != running ] || [ "$SECONDS" -ge "$deadline" ]; then
      show_ui_log "$name"
      die "the GUI's dev server stopped, or did not compile in $((UI_WAIT_SECONDS / 60)) minutes; its \
container $container stays, for its log: docker logs $container"
    fi
    sleep "$UI_POLL_SECONDS"
  done
  if [ "$progress" = failed ]; then
    show_ui_log "$name"
    die "the GUI did not compile. Its dev server runs on in $container, and compiles again when a \
file changes; sandbox.sh ui --stop removes it"
  fi
}

# start_ui <name> <image>: the dev server's container, started again when what it starts from has
# changed: the image, the checkout, or how it is run. The admin's token is mounted, alone, for the
# entrypoint to read. The dev proxy answers the GUI's own origin alone, so that no other page in
# the browser reads what it answers as the admin. Every setting of where the proxy listens and
# forwards is given here, empty where unset, since the client's .env files in the checkout set only
# what the environment does not: one there could send the admin's token elsewhere.
start_ui() {
  local name=$1 image=$2 dir=$STATE_ROOT/$1 client=$REPO_ROOT/client container state hash
  local -a options
  container=$(service_container "$name" ui)
  options=(--network "$(sandbox_network "$name")"
    --publish "127.0.0.1:$UI_PORT:$UI_PORT" --publish "127.0.0.1:$UI_PROXY_PORT:$UI_PROXY_PORT"
    --user "$(id -u):$(id -g)" --env HOME=/tmp --workdir "$client"
    --volume "$client:$client" --volume "$dir/admin-token.secret:$UI_TOKEN_FILE:ro"
    --volume "$SKILL_DIR/docker/ui-entrypoint.sh:$UI_ENTRYPOINT:ro"
    --env "SANDBOX_TOKEN_FILE=$UI_TOKEN_FILE" --env "SERVER=$UI_API_URL" --env PROXY_INSECURE=1
    --env "PROXY_TARGET=${UI_API_URL%"$UI_PATH"}" --env "PROXY_SERVER=http://localhost:$UI_PROXY_PORT$UI_PATH"
    --env PROXY_HOST=0.0.0.0 --env "PROXY_PORT=$UI_PROXY_PORT" --env "PROXY_CORS_ORIGIN=http://localhost:$UI_PORT"
    --env PROXY_DISABLED= --env SKIP_DEV_PROXY= --env HOST=0.0.0.0 --env "PORT=$UI_PORT" --env HTTPS=false
    --env CHOKIDAR_USEPOLLING=true --env BROWSER=none
    --entrypoint bash "$image" "$UI_ENTRYPOINT" npm start)
  hash=$(printf '%s\n' "${options[@]}" | git hash-object --stdin | cut -c1-12)
  state=$(container_state "$name" ui)
  if [ "$state" = running ] && [ "$(container_label "$name" ui "$LABEL_CONFIG")" = "$hash" ]; then
    log "The GUI's container $container is running already, on the same checkout and settings."
    return 0
  fi
  if [ -n "$state" ]; then
    log "Starting the GUI's container $container again: it is $state, or its settings changed."
    docker_change rm --force "$container" >/dev/null
  fi
  docker_change run --detach --init --name "$container" --label "$LABEL_SANDBOX=$name" \
    --label "$LABEL_ROLE=ui" --label "$LABEL_CONFIG=$hash" "${options[@]}" >/dev/null
}

# cmd_ui [--name <name>] [--stop] [--dry-run]: runs the GUI of this checkout against the sandbox's
# API, as its admin, or removes its container.
cmd_ui() {
  local name='' stop=0 container image
  while [ $# -gt 0 ]; do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --stop) stop=1; shift ;;
      --dry-run) HOST_DRY_RUN=1; shift ;;
      *) die "ui: unknown option $1" ;;
    esac
  done
  name=$(host_select_sandbox "$name")
  container=$(service_container "$name" ui)
  if [ "$stop" = 1 ]; then
    if [ -z "$(container_state "$name" ui)" ]; then
      printf 'Sandbox %s runs no GUI here.\n' "$name"
      return 0
    fi
    docker_change rm --force "$container" >/dev/null
    if [ "$HOST_DRY_RUN" = 1 ]; then
      printf '[dry-run] Nothing was removed.\n'
    else
      printf 'Stopped the GUI of sandbox %s: removed the container %s.\n' "$name" "$container"
    fi
    return 0
  fi

  if [ "$(container_state "$name" api)" != running ]; then
    [ "$HOST_DRY_RUN" = 1 ] || die "sandbox $name runs no API here: run sandbox.sh services first"
    log "[dry-run] Sandbox $name runs no API here; without --dry-run, ui stops here."
  fi
  if [ ! -f "$STATE_ROOT/$name/admin-token.secret" ]; then
    [ "$HOST_DRY_RUN" = 1 ] || die "sandbox $name has no admin-token.secret: run sandbox.sh services"
    log "[dry-run] Sandbox $name has no admin-token.secret; without --dry-run, ui stops here."
  fi
  image=$(ui_image)
  install_client_modules "$image"
  start_ui "$name" "$image"
  wait_for_ui "$name"
  if [ "$HOST_DRY_RUN" = 1 ]; then
    printf '[dry-run] Nothing was started.\n'
    return 0
  fi
  cat <<EOF
Sandbox $name: the GUI of $REPO_ROOT/client runs in the container $container.
  gui    http://localhost:$UI_PORT/, on 127.0.0.1 only: every request goes to the API as its admin
  proxy  http://localhost:$UI_PROXY_PORT$UI_PATH, to $UI_API_URL on the network
  log    docker logs $container
  stop   sandbox.sh ui --stop
It compiles again when a file of client/ changes.
EOF
}
