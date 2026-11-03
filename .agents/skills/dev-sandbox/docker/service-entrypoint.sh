#!/bin/bash
#
# Entrypoint of the platform's optional services — billing, search and monitoring — that
# sandbox.sh services --with runs in the API's image. Exports the platform admin's token, which
# their settings name as ${CP_API_JWT_ADMIN}, read from its file in the sandbox's local state,
# mounted read-only, then runs the command. The token goes to no file, and to no output.
#
# The environment sandbox.sh sets: SANDBOX_DIR, the sandbox's local state.
set -euo pipefail

: "${SANDBOX_DIR:?}"

TOKEN_FILE=$SANDBOX_DIR/admin-token.secret

if [[ ! -s $TOKEN_FILE ]]; then
  printf 'service-entrypoint: no admin token in %s: run sandbox.sh services\n' "$TOKEN_FILE" >&2
  exit 1
fi
CP_API_JWT_ADMIN=$(tr -d '[:space:]' <"$TOKEN_FILE")
export CP_API_JWT_ADMIN

exec "$@"
