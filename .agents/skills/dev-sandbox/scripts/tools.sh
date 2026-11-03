#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Runs one command in the dev sandbox tools image (AWS CLI v2, the Session Manager plugin, OpenSSH,
# jq, kubectl 1.15), building the image first if docker/ has changed.
#
# Usage: tools.sh [--sandbox <name>] <command> [args...]
#   tools.sh aws sts get-caller-identity
#   tools.sh kubectl version --client
#   tools.sh --sandbox <name> kubectl get nodes
#
# The command runs as the calling user, with the repository mounted read-only, the local sandbox
# state read-write, the host's ~/.aws read-only, and the developer config (.env.sandbox.local) as
# its environment. That environment holds credentials: never run a command that prints it.
#
# --sandbox <name>: the container joins the sandbox's Docker network, where sandbox.sh connect runs
# the tunnel to its master, with KUBECONFIG set to the sandbox's kubeconfig.
set -euo pipefail

SCRIPTS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=lib/common.sh
. "$SCRIPTS_DIR/lib/common.sh"
# shellcheck source=lib/host.sh
. "$SCRIPTS_DIR/lib/host.sh"

if [ "${1-}" = --sandbox ]; then
  TOOLS_SANDBOX=$(option_value --sandbox "${2-}")
  shift 2
  validate_name "$TOOLS_SANDBOX"
  require_docker
  docker network inspect "$(sandbox_network "$TOOLS_SANDBOX")" >/dev/null 2>&1 ||
    die "sandbox $TOOLS_SANDBOX has no Docker network here: run sandbox.sh connect first"
fi

if [ $# -eq 0 ]; then
  log "Usage: tools.sh [--sandbox <name>] <command> [args...]    for example: tools.sh aws sts get-caller-identity"
  exit 1
fi

run_in_tools "$@"
