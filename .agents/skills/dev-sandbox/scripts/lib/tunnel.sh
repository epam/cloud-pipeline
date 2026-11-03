# shellcheck shell=bash
# Host side: a sandbox's objects on the developer's Docker, which the tools image cannot reach. Its
# network, cp-sandbox-<name>, is where local containers reach the master through the tunnel. The
# tunnel container runs the tools image detached on that network, as commands/connect.sh describes;
# connect starts it, connect --stop and down remove it. Sourced by sandbox.sh after common.sh and
# host.sh. bash 3.2, like them.

LABEL_SANDBOX=cp-sandbox
LABEL_ROLE=cp-sandbox-role
TUNNEL_LOG_LINES=20

HOST_DRY_RUN=0

# docker_change <docker args...>: a docker command that changes something, or under --dry-run the
# command printed instead.
docker_change() {
  if [ "$HOST_DRY_RUN" = 1 ]; then
    log "[dry-run] $(print_command docker "$@")"
    return 0
  fi
  docker "$@"
}

# docker_objects [<name>]: the containers, networks and volumes labelled for the sandbox, or for
# any sandbox, one per line: <sandbox> <container|network|volume> <name> <state>.
docker_objects() {
  local filter=label=$LABEL_SANDBOX${1:+=$1}
  docker ps --all --filter "$filter" \
    --format "{{.Label \"$LABEL_SANDBOX\"}} container {{.Names}} {{.State}}"
  docker network ls --filter "$filter" --format "{{.Label \"$LABEL_SANDBOX\"}} network {{.Name}} -"
  docker volume ls --filter "$filter" --format "{{.Label \"$LABEL_SANDBOX\"}} volume {{.Name}} -"
}

# container_state <name> <role>: the state of the sandbox's container with that role (running,
# exited, ...), or nothing when there is none.
container_state() {
  docker ps --all --filter "label=$LABEL_SANDBOX=$1" --filter "label=$LABEL_ROLE=$2" \
    --format '{{.State}}'
}

# tunnel_state <name>: the state of the sandbox's tunnel container, as container_state gives it.
tunnel_state() {
  container_state "$1" tunnel
}

# start_tunnel <name>: the tunnel container, detached, on the sandbox's network under the name the
# API server's certificate carries. It runs the tools image as every tools container does, with
# the config as its environment, so its process is in-tools.sh tunnel.
start_tunnel() {
  tools_image
  tools_options
  docker_change run --detach --init --name "$(tunnel_container "$1")" \
    --label "$LABEL_SANDBOX=$1" --label "$LABEL_ROLE=tunnel" \
    --network "$(sandbox_network "$1")" --network-alias "$(kube_tunnel_host "$1")" \
    "${TOOLS_OPTIONS[@]}" "$TOOLS_IMAGE" bash "$SCRIPTS_DIR/in-tools.sh" tunnel --name "$1" >/dev/null
}

# cmd_connect [--name <name>] [--stop] [--dry-run]: opens the sandbox's tunnel, or closes it. The
# network stays when the tunnel closes, for the local containers attached to it; down removes it.
cmd_connect() {
  local name='' stop=0 network container state
  local -a dry=()
  while [ $# -gt 0 ]; do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --stop) stop=1; shift ;;
      --dry-run) HOST_DRY_RUN=1; dry=(--dry-run); shift ;;
      *) die "connect: unknown option $1" ;;
    esac
  done
  name=$(host_select_sandbox "$name")
  network=$(sandbox_network "$name")
  container=$(tunnel_container "$name")

  if [ "$stop" = 1 ]; then
    run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" connect-stop --name "$name" ${dry[@]+"${dry[@]}"}
    if [ -z "$(tunnel_state "$name")" ]; then
      printf 'Sandbox %s has no tunnel container.\n' "$name"
      return 0
    fi
    docker_change rm --force "$container" >/dev/null
    if [ "$HOST_DRY_RUN" = 1 ]; then
      printf '[dry-run] Nothing was removed.\n'
    else
      printf 'Closed the tunnel of sandbox %s: removed the container %s.\n' "$name" "$container"
    fi
    return 0
  fi

  run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" connect-prepare --name "$name" ${dry[@]+"${dry[@]}"}
  if ! docker network inspect "$network" >/dev/null 2>&1; then
    docker_change network create --label "$LABEL_SANDBOX=$name" "$network" >/dev/null
  fi
  state=$(tunnel_state "$name")
  case $state in
    running) log "The tunnel container $container is running already." ;;
    *)
      if [ -n "$state" ]; then
        log "The tunnel container $container is $state: starting it again."
        docker_change rm --force "$container" >/dev/null
      fi
      start_tunnel "$name"
      ;;
  esac

  # Under --dry-run there is no network for the check to join.
  if [ "$HOST_DRY_RUN" = 1 ]; then
    run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" connect-check --name "$name" --dry-run
    return 0
  fi
  if ! TOOLS_SANDBOX=$name run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" connect-check --name "$name"
  then
    log "The last lines of the tunnel's log:"
    docker logs --tail "$TUNNEL_LOG_LINES" "$container" 2>&1 | sed 's/^/  | /' >&2 || true
    docker rm --force "$container" >/dev/null 2>&1 || true
    die "the tunnel did not work, so connect removed its container"
  fi
}

# host_down [--name <name>] [--yes] [--dry-run]: down, with the sandbox's Docker objects listed for
# it, and removed once it has succeeded with --yes. The API's container goes first, so that it
# starts no node while the instances go.
host_down() {
  local name='' yes=0 objects
  local -a args=()
  while [ $# -gt 0 ]; do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --yes) yes=1; args+=(--yes); shift ;;
      --dry-run) HOST_DRY_RUN=1; args+=(--dry-run); shift ;;
      *) die "down: unknown option $1" ;;
    esac
  done
  name=$(host_select_sandbox "$name")
  objects=$(docker_objects "$name")
  if [ "$yes" = 1 ] && [ "$HOST_DRY_RUN" = 0 ] && [ -n "$(container_state "$name" api)" ]; then
    docker rm --force "$(service_container "$name" api)" >/dev/null ||
      die "could not remove the API's container $(service_container "$name" api), which may start nodes: \
remove it with docker rm --force, then run down again"
    printf 'Removed the container %s first, so that it starts no node.\n' "$(service_container "$name" api)"
  fi
  TOOLS_DOCKER_OBJECTS=$objects run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" down --name "$name" \
    ${args[@]+"${args[@]}"}
  if [ "$yes" = 1 ] && [ "$HOST_DRY_RUN" = 0 ]; then
    objects=$(docker_objects "$name")
    if [ -n "$objects" ]; then
      remove_docker_objects "$objects"
    fi
  fi
}

# remove_docker_objects <objects>: removes what docker_objects listed, the containers first: a
# network or a volume goes only once no container uses it. One that something else still uses is
# left, and named. Fails when a container could not be removed.
remove_docker_objects() {
  local sandbox kind object failed=''
  while read -r sandbox kind object _; do
    if [ "$kind" != container ]; then
      continue
    elif docker rm --force "$object" >/dev/null; then
      printf 'Removed the container %s.\n' "$object"
    else
      failed=$failed${failed:+ }$object
    fi
  done <<<"$1"
  while read -r sandbox kind object _; do
    case $kind in
      network | volume) ;;
      *) continue ;;
    esac
    if docker "$kind" rm "$object" >/dev/null 2>&1; then
      printf 'Removed the %s %s.\n' "$kind" "$object"
    else
      log "Left the $kind $object: a container still uses it, one not labelled \
$LABEL_SANDBOX=$sandbox or one that could not be removed. Remove it, then: docker $kind rm $object"
    fi
  done <<<"$1"
  [ -z "$failed" ] || die "could not remove the container(s) $failed: remove them with docker rm --force"
}
