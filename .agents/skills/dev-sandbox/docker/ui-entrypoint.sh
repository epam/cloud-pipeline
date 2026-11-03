#!/bin/bash
#
# Entrypoint of the GUI's dev server, which sandbox.sh ui runs in the Node image of client/.nvmrc.
# Exports the platform admin's token as PROXY_BEARER_TOKEN, which client/scripts/dev-proxy.js puts
# on every request it forwards to the API, read from its file, mounted read-only, then runs the
# command. The token goes to no file, to no output, and not into the container's settings, which
# docker inspect shows.
#
# The environment sandbox.sh sets: SANDBOX_TOKEN_FILE, where the token is mounted.
set -euo pipefail

: "${SANDBOX_TOKEN_FILE:?}"

if [[ ! -s $SANDBOX_TOKEN_FILE ]]; then
  printf 'ui-entrypoint: no admin token in %s: run sandbox.sh services\n' "$SANDBOX_TOKEN_FILE" >&2
  exit 1
fi
PROXY_BEARER_TOKEN=$(tr -d '[:space:]' <"$SANDBOX_TOKEN_FILE")
export PROXY_BEARER_TOKEN

exec "$@"
