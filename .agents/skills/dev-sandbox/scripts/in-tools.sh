#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# The sandbox commands that call AWS. Runs in the tools image, started by sandbox.sh, which mounts
# the local state and passes the developer config in as the environment.
#
# Usage: in-tools.sh <check|ami|up|status|extend|down> [options] [--dry-run]
#        in-tools.sh <connect-prepare|connect-check|connect-stop|tunnel> --name <name> [--dry-run]
#        in-tools.sh <services-prepare|services-master|services-sequences|services-wait|
#                     services-configure|services-api> --name <name> [options] [--dry-run]
# The second and third forms are the steps of connect, services and api in the tools image, which
# only sandbox.sh runs.
set -euo pipefail
# Without it, bash runs every $(...) with errexit off, so a function whose output is captured
# carries on past a failed AWS call and reports what it had so far as the answer.
shopt -s inherit_errexit
umask 077

SCRIPTS_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=lib/common.sh
. "$SCRIPTS_DIR/lib/common.sh"
# shellcheck source=lib/config.sh
. "$SCRIPTS_DIR/lib/config.sh"
# shellcheck source=lib/aws.sh
. "$SCRIPTS_DIR/lib/aws.sh"
# shellcheck source=lib/state.sh
. "$SCRIPTS_DIR/lib/state.sh"
# shellcheck source=lib/ssm.sh
. "$SCRIPTS_DIR/lib/ssm.sh"
# shellcheck source=commands/check.sh
. "$SCRIPTS_DIR/commands/check.sh"
# shellcheck source=commands/ami.sh
. "$SCRIPTS_DIR/commands/ami.sh"
# shellcheck source=commands/up.sh
. "$SCRIPTS_DIR/commands/up.sh"
# shellcheck source=commands/status.sh
. "$SCRIPTS_DIR/commands/status.sh"
# shellcheck source=commands/extend.sh
. "$SCRIPTS_DIR/commands/extend.sh"
# shellcheck source=commands/down.sh
. "$SCRIPTS_DIR/commands/down.sh"
# shellcheck source=commands/connect.sh
. "$SCRIPTS_DIR/commands/connect.sh"
# shellcheck source=commands/services.sh
. "$SCRIPTS_DIR/commands/services.sh"

main() {
  local command=${1-} arg
  local -a args=()
  [[ -n $command ]] || die "no command given"
  shift
  for arg in "$@"; do
    if [[ $arg == --dry-run ]]; then
      DRY_RUN=1
    else
      args+=("$arg")
    fi
  done

  : "${SANDBOX_STATE_ROOT:?is set by sandbox.sh}" "${SANDBOX_ENV_FILE:?is set by sandbox.sh}"
  config_load

  case $command in
    check) cmd_check "${args[@]}" ;;
    ami) cmd_ami "${args[@]}" ;;
    up) cmd_up "${args[@]}" ;;
    status) cmd_status "${args[@]}" ;;
    extend) cmd_extend "${args[@]}" ;;
    down) cmd_down "${args[@]}" ;;
    connect-prepare) cmd_connect_prepare "${args[@]}" ;;
    connect-check) cmd_connect_check "${args[@]}" ;;
    connect-stop) cmd_connect_stop "${args[@]}" ;;
    tunnel) cmd_tunnel "${args[@]}" ;;
    services-prepare) cmd_services_prepare "${args[@]}" ;;
    services-master) cmd_services_master "${args[@]}" ;;
    services-sequences) cmd_services_sequences "${args[@]}" ;;
    services-wait) cmd_services_wait "${args[@]}" ;;
    services-configure) cmd_services_configure "${args[@]}" ;;
    services-api) cmd_services_api "${args[@]}" ;;
    *) die "unknown command: $command" ;;
  esac
}

main "$@"
