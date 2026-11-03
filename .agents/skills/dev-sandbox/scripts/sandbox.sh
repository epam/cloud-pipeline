#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# A throw-away AWS sandbox for live testing: a master instance on EC2, owned by one developer and
# tagged so that it can be found, extended and removed.
#
# Runs on the host, which needs Docker and git and nothing else: the commands that call AWS run in
# the tools image, through tools.sh, and what they need of Docker runs here (lib/tunnel.sh).
# `sandbox.sh help` lists the commands.
set -euo pipefail

SCRIPTS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=lib/common.sh
. "$SCRIPTS_DIR/lib/common.sh"
# shellcheck source=lib/host.sh
. "$SCRIPTS_DIR/lib/host.sh"
# shellcheck source=lib/tunnel.sh
. "$SCRIPTS_DIR/lib/tunnel.sh"
# shellcheck source=lib/services.sh
. "$SCRIPTS_DIR/lib/services.sh"
# shellcheck source=lib/ui.sh
. "$SCRIPTS_DIR/lib/ui.sh"

usage() {
  cat <<'EOF'
Usage: sandbox.sh <command> [options]

Commands:
  check                        Check the config against the AWS account, changing nothing: list
                               every item that fails. up and ami run it first.
  ami [--rebuild [--yes]]      The shared host image masters and workers start from: find it, or
                               build it when there is none for this version of prepare-host.sh.
                               --rebuild lists what a new build would replace; with --yes, builds
                               one and takes the version's tag off the old one.
  up [--name <name>] [--new]   Launch the master from the host image, or pick up the one already
                               running, install Kubernetes on it, and keep its kubeconfig and join
                               token in the local state. Without --name: the only local sandbox,
                               or a new one if there is none. --new: always a new one.
  connect [--name <name>] [--stop]
                               Open a tunnel to the master, SSH through Session Manager, run by a
                               container on the Docker network cp-sandbox-<name>. Containers there
                               reach the Kubernetes API at https://cp-sandbox-<name>-master:6443,
                               and the VPC through the SOCKS proxy at
                               cp-sandbox-<name>-master:1080; the master's <private IP>:9999
                               reaches api:9999 there. --stop closes it.
  services [--name <name>] [--with <services>] [--pipe-common-from-checkout] [--build] [--stop]
           [--log [<container>] [--tail <lines>]]
                               Run the platform's API here, built from this checkout, with its
                               database, on the sandbox's network, and configure it for the
                               sandbox: region, preferences, the master's registry and a tool.
                               Needs the tunnel. --with billing,search,monitoring, or all, runs
                               those services too, with an Elasticsearch.
                               --pipe-common-from-checkout serves the runs this checkout's
                               workflows/pipe-common in place of the published build's; without
                               it, the build's is served again. --build builds the jars again;
                               --stop removes the containers and keeps the volumes; --log shows
                               the log of the API, or of db, elasticsearch, billing, search or
                               monitoring, its tokens and credentials blanked out.
  api [--name <name>] [--jq <filter>] <GET|POST|PUT|DELETE> <path> [<json body>]
                               One call to the REST API of the API services runs, as its admin, at
                               <path> under https://api:9999/pipeline/restapi/. Writes the payload,
                               its secrets blanked out, through the jq filter.
  ui [--name <name>] [--stop]  Run the GUI of this checkout's client/ (npm start, in Node's image)
                               on the sandbox's network, against the API services runs, as its
                               admin, and wait for it to compile: http://localhost:3100/, on
                               127.0.0.1 only. --stop removes its container.
  status                       Local sandboxes, their containers and networks on Docker, and the
                               instances tagged with this owner.
  extend --hours <n> [--name <name>]
                               Move the sandbox's expiry n hours later.
  down [--name <name>] [--yes] List the sandbox's instances, key pair, local state, and its
                               containers, network and volumes on Docker; with --yes, terminate the
                               instances and remove the rest.

Every command takes --dry-run: print the AWS calls instead of making them, and change nothing.

Config: .env.sandbox.local in the repository root (see sandbox.env.example); in a worktree that
has none, the main checkout's. Local state: .agents/sandboxes.local/<name>/ in the main checkout.
CP_SANDBOX_ENV_FILE and CP_SANDBOX_STATE_DIR override them.
EOF
}

# The config holds credentials, so nothing on the host opens it: Docker reads it into the tools
# image's environment, and the image checks what arrived.
require_config() {
  local dry_run=$1
  [ -f "$ENV_FILE" ] && return 0
  if [ "$dry_run" = 1 ]; then
    die "no config at $ENV_FILE. Without --dry-run, the template would be copied there to fill in."
  fi
  mkdir -p "$(dirname "$ENV_FILE")"
  (umask 077 && cp "$SKILL_DIR/sandbox.env.example" "$ENV_FILE")
  die "no config yet: copied the template to $ENV_FILE. Fill it in, then run this again."
}

# The state root ignores itself: the main checkout it defaults to may be on a branch whose
# .gitignore does not list it.
create_state_root() {
  (umask 077 && mkdir -p "$STATE_ROOT")
  if [ ! -f "$STATE_ROOT/.gitignore" ]; then
    printf '*\n' >"$STATE_ROOT/.gitignore"
  fi
}

main() {
  local command dry_run=0 arg
  if [ $# -eq 0 ]; then
    usage >&2
    exit 1
  fi
  command=$1

  for arg in "$@"; do
    case $arg in
      --dry-run) dry_run=1 ;;
      -h | --help) usage; return 0 ;;
    esac
  done

  case $command in
    check | ami | up | connect | services | api | ui | status | extend | down)
      require_docker
      host_paths
      require_config "$dry_run"
      if [ "$dry_run" = 0 ]; then
        create_state_root
      fi
      shift
      case $command in
        connect) cmd_connect "$@" ;;
        services) cmd_services "$@" ;;
        api) cmd_api "$@" ;;
        ui) cmd_ui "$@" ;;
        down) host_down "$@" ;;
        status)
          TOOLS_DOCKER_OBJECTS=$(docker_objects)
          run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" status "$@"
          ;;
        *) run_in_tools bash "$SCRIPTS_DIR/in-tools.sh" "$command" "$@" ;;
      esac
      ;;
    help) usage ;;
    *)
      usage >&2
      die "unknown command: $command"
      ;;
  esac
}

main "$@"
