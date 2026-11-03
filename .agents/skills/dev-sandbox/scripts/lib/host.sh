# shellcheck shell=bash
# Host side: where the config and the local state live, and running a command in the tools image.
# Sourced after common.sh, with SCRIPTS_DIR set. bash 3.2, like common.sh.

TOOLS_IMAGE_REPOSITORY=cp-sandbox-tools
# Both as the image's Dockerfile sets them.
CONTAINER_HOME=/home/sandbox
CONTAINER_HOST_AWS_DIR=/mnt/host-aws
ENV_FILE_NAME=.env.sandbox.local
STATE_DIR_NAME=.agents/sandboxes.local

# What run_in_tools adds for one call. TOOLS_SANDBOX: a sandbox whose Docker network the container
# joins, with KUBECONFIG set to its kubeconfig. TOOLS_DOCKER_OBJECTS: the sandbox Docker objects
# the container is told about, as docker_objects lists them, since it cannot reach Docker itself.
TOOLS_SANDBOX=''
TOOLS_DOCKER_OBJECTS=''

# main_checkout <repo-root>: the main working tree of a repository, which is the repo root itself
# unless that is a linked worktree.
main_checkout() {
  local common
  if common=$(cd "$1" && cd "$(git rev-parse --git-common-dir)" && pwd -P); then
    case $common in
      */.git) printf '%s\n' "${common%/.git}"; return 0 ;;
    esac
  fi
  printf '%s\n' "$1"
}

absolute_path() {
  case $1 in
    /*) printf '%s\n' "$1" ;;
    *) printf '%s/%s\n' "$PWD" "$1" ;;
  esac
}

# host_paths: sets SKILL_DIR and REPO_ROOT, then ENV_FILE and STATE_ROOT as config_paths does.
host_paths() {
  SKILL_DIR=$(cd "$SCRIPTS_DIR/.." && pwd -P)
  REPO_ROOT=$(git -C "$SKILL_DIR" rev-parse --show-toplevel) ||
    die "$SKILL_DIR is not inside a git checkout"
  config_paths "$REPO_ROOT"
}

# config_paths <repo-root>: sets ENV_FILE and STATE_ROOT for a checkout.
#
# The config and the sandboxes belong to the developer rather than to a branch, so both default to
# the main checkout, where they outlive any worktree. A config file in the current worktree's root
# is used first, since copying env files into a worktree is the usual way to give it one.
# CP_SANDBOX_ENV_FILE and CP_SANDBOX_STATE_DIR override both.
config_paths() {
  local main
  main=$(main_checkout "$1")

  if [ -n "${CP_SANDBOX_ENV_FILE:-}" ]; then
    ENV_FILE=$(absolute_path "$CP_SANDBOX_ENV_FILE")
  elif [ -f "$1/$ENV_FILE_NAME" ]; then
    ENV_FILE=$1/$ENV_FILE_NAME
  else
    ENV_FILE=$main/$ENV_FILE_NAME
  fi

  if [ -n "${CP_SANDBOX_STATE_DIR:-}" ]; then
    STATE_ROOT=$(absolute_path "$CP_SANDBOX_STATE_DIR")
  else
    STATE_ROOT=$main/$STATE_DIR_NAME
  fi
}

# tools_image: sets TOOLS_IMAGE, building it when docker/ has changed since the last build.
tools_image() {
  local hash
  hash=$(cat "$SKILL_DIR/docker/tools.Dockerfile" "$SKILL_DIR/docker/tools-entrypoint.sh" |
    git hash-object --stdin | cut -c1-12)
  TOOLS_IMAGE=$TOOLS_IMAGE_REPOSITORY:$hash
  docker image inspect "$TOOLS_IMAGE" >/dev/null 2>&1 && return 0
  log "Building the tools image $TOOLS_IMAGE. This happens again only when docker/ changes."
  docker build -f "$SKILL_DIR/docker/tools.Dockerfile" -t "$TOOLS_IMAGE" "$SKILL_DIR/docker" >&2 ||
    die "the tools image did not build"
}

require_docker() {
  command -v docker >/dev/null 2>&1 || die "docker is not on PATH"
}

# tools_options: sets TOOLS_OPTIONS to the docker run options of every tools container, after
# host_paths has run.
#
# The container runs as the calling user. The repository is mounted read-only and the local state
# read-write, both at their host paths, so a path means the same thing inside and out. The host's
# ~/.aws is mounted read-only, and the config file becomes the container's environment: Docker
# reads it, and no script here opens it. HOME is a tmpfs: the copy of ~/.aws the entrypoint makes
# there never reaches a disk.
tools_options() {
  local workdir
  case $PWD/ in
    "$REPO_ROOT"/* | "$STATE_ROOT"/*) workdir=$PWD ;;
    *) workdir=$REPO_ROOT ;;
  esac
  TOOLS_OPTIONS=(--user "$(id -u):$(id -g)"
    --tmpfs "$CONTAINER_HOME:mode=1777"
    --workdir "$workdir"
    --volume "$REPO_ROOT:$REPO_ROOT:ro"
    --env "SANDBOX_REPO_ROOT=$REPO_ROOT"
    --env "SANDBOX_STATE_ROOT=$STATE_ROOT"
    --env "SANDBOX_ENV_FILE=$ENV_FILE")
  if [ -d "$STATE_ROOT" ]; then
    TOOLS_OPTIONS+=(--volume "$STATE_ROOT:$STATE_ROOT")
  fi
  if [ -d "$HOME/.aws" ]; then
    TOOLS_OPTIONS+=(--volume "$HOME/.aws:$CONTAINER_HOST_AWS_DIR:ro")
  fi
  if [ -f "$ENV_FILE" ]; then
    TOOLS_OPTIONS+=(--env-file "$ENV_FILE")
  fi
}

# run_in_tools <command> [args...]: runs the command in the tools image, as tools_options sets it
# up, and as TOOLS_SANDBOX and TOOLS_DOCKER_OBJECTS ask.
run_in_tools() {
  local -a options
  require_docker
  host_paths
  tools_image
  tools_options

  options=(--rm --interactive "${TOOLS_OPTIONS[@]}")
  if [ -n "$TOOLS_SANDBOX" ]; then
    options+=(--network "$(sandbox_network "$TOOLS_SANDBOX")"
      --env "KUBECONFIG=$STATE_ROOT/$TOOLS_SANDBOX/$KUBECONFIG_FILE_NAME")
  fi
  if [ -n "$TOOLS_DOCKER_OBJECTS" ]; then
    options+=(--env "SANDBOX_DOCKER_OBJECTS=$TOOLS_DOCKER_OBJECTS")
  fi
  if [ -t 0 ] && [ -t 1 ]; then
    options+=(--tty)
  fi
  docker run "${options[@]}" "$TOOLS_IMAGE" "$@"
}

# host_select_sandbox <name-or-empty>: the sandbox a command acts on, as select_sandbox picks it in
# the tools image — the one named, or else the only one with local state — for the steps that run
# on the host. After host_paths.
host_select_sandbox() {
  local file found='' count=0
  if [ -n "$1" ]; then
    validate_name "$1"
    printf '%s\n' "$1"
    return 0
  fi
  for file in "$STATE_ROOT"/*/state.json; do
    [ -f "$file" ] || continue
    found=$found${found:+ }$(basename "$(dirname "$file")")
    count=$((count + 1))
  done
  case $count in
    0) die "no local sandbox in $STATE_ROOT; pass --name <name>" ;;
    1) printf '%s\n' "$found" ;;
    *) die "several local sandboxes ($found); pass --name <name>" ;;
  esac
}
