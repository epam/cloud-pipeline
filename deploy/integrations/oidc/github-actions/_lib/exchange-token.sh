#!/usr/bin/env bash
# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Trades this run's GitHub OIDC token for a Cloud Pipeline API token (RFC 8693).
# Shared by every action here, called through $GITHUB_ACTION_PATH. Internal: no
# workflow uses this path directly.
#
# Reads   INPUT_API_URL, ACTIONS_ID_TOKEN_REQUEST_URL, ACTIONS_ID_TOKEN_REQUEST_TOKEN
# Writes  $GITHUB_OUTPUT  token, api-url, expires-in
#         $GITHUB_ENV     API, API_TOKEN

set -euo pipefail

fail() {
  echo "::error::$1"
  shift
  for line in "$@"; do
    echo "$line"
  done
  exit 1
}

for tool in curl jq; do
  command -v "$tool" >/dev/null 2>&1 || fail \
    "$tool is required and was not found on this runner." \
    "GitHub-hosted runners have it; a self-hosted runner may need it installed."
done

# This value is written to GITHUB_ENV and is the audience GitHub signs, so it is
# checked: whitespace could add lines of its own, and http would expose the token.
API_URL="${INPUT_API_URL:-}"
[ -n "$API_URL" ] || fail \
  "The api-url input is empty." \
  "A misspelled key is only a warning from GitHub, so check it reads exactly 'api-url'."
case "$API_URL" in
  *[[:space:]]*) fail "The api-url input contains whitespace: '$API_URL'." ;;
esac
case "$API_URL" in
  https://?*) ;;
  http://*) fail \
    "The api-url input must be https: '$API_URL'." \
    "The OIDC token is sent to it, and over plain HTTP anyone on the path can read it." ;;
  *) fail \
    "The api-url input must be an https URL: '$API_URL'." \
    "For example https://cloud-pipeline.example.com" ;;
esac

while [ "${API_URL%/}" != "$API_URL" ]; do
  API_URL="${API_URL%/}"
done
case "$API_URL" in
  */restapi) ;;
  *) API_URL="$API_URL/pipeline/restapi" ;;
esac
echo "Cloud Pipeline API: $API_URL"

if [ -z "${ACTIONS_ID_TOKEN_REQUEST_URL:-}" ] || [ -z "${ACTIONS_ID_TOKEN_REQUEST_TOKEN:-}" ]; then
  fail \
    "No OIDC token is available to this job." \
    "Add 'permissions: { id-token: write }' to the workflow or the job."
fi

# Both requests are configured on stdin. A token given as a curl argument can be
# read from /proc/<pid>/cmdline by anything else on the host.
GITHUB_RESPONSE="$(printf '%s\n' \
  "url = \"$ACTIONS_ID_TOKEN_REQUEST_URL\"" \
  "header = \"Authorization: bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN\"" \
  "data-urlencode = \"audience=$API_URL\"" \
  | curl -sS -G -w '\n%{http_code}' --config - )" || fail \
    "Could not reach GitHub to request an OIDC token."
GITHUB_STATUS="${GITHUB_RESPONSE##*$'\n'}"
GITHUB_OIDC_TOKEN="$(printf '%s' "${GITHUB_RESPONSE%$'\n'*}" \
  | jq -r '.value // empty' 2>/dev/null || true)"

if [ "$GITHUB_STATUS" != "200" ] || [ -z "$GITHUB_OIDC_TOKEN" ]; then
  fail "GitHub returned no OIDC token for audience $API_URL (HTTP $GITHUB_STATUS)."
fi
# masked too: it can be traded for the platform token, so it is a credential itself
echo "::add-mask::$GITHUB_OIDC_TOKEN"

# Kept in memory, not a temp file. A cancelled step runs no EXIT trap, which on a
# self-hosted runner would leave the token on disk.
RESPONSE="$(printf '%s\n' \
  "url = \"$API_URL/oauth/token\"" \
  'data-urlencode = "grant_type=urn:ietf:params:oauth:grant-type:token-exchange"' \
  "data-urlencode = \"subject_token=$GITHUB_OIDC_TOKEN\"" \
  'data-urlencode = "subject_token_type=urn:ietf:params:oauth:token-type:jwt"' \
  | curl -sS -w '\n%{http_code}' --config - )" || fail \
    "Could not reach $API_URL to exchange the token." \
    "A GitHub-hosted runner is on the public internet, so a deployment with a private API" \
    "needs a self-hosted runner inside the network."
STATUS="${RESPONSE##*$'\n'}"
BODY="${RESPONSE%$'\n'*}"
TOKEN="$(printf '%s' "$BODY" | jq -r '.access_token // empty' 2>/dev/null || true)"

if [ "$STATUS" != "200" ] || [ -z "$TOKEN" ]; then
  ERROR="$(printf '%s' "$BODY" | jq -r '.error // empty' 2>/dev/null || true)"
  # Status first, then the error code. When the exchange is off the route does not
  # exist, so Spring answers with its own "Not Found" in the error field, never an
  # OAuth code. Reading the code first would miss this case.
  case "$STATUS" in
    404)
      fail "The endpoint is not registered. The exchange is off." \
           "Set oidc.token.exchange.enabled on the API service and restart it." \
           "The request never reaches the endpoint, so the API log has nothing to show." ;;
    30*)
      fail "$API_URL/oauth/token redirected (HTTP $STATUS); redirects are not followed." \
           "Check that api-url names the host the API is actually served on." ;;
  esac
  # The API returns a code and nothing more on purpose: this endpoint needs no
  # platform token, so a caller must not learn which check refused it.
  case "$ERROR" in
    unauthorized_client)
      fail "No configured client matched this run (unauthorized_client)." \
           "Check the claim conditions of the client: the repository info, and the ref or" \
           "environment it is pinned to." ;;
    invalid_grant)
      fail "The token was not accepted, or the user it names cannot be acted as." \
           "Most often the configured audience does not match $API_URL, or the" \
           "platform user named by the client does not exist." ;;
    unsupported_grant_type|invalid_request)
      fail "The request was malformed. This is a fault of the action, not the setup;" \
           "please report it." ;;
    *)
      fail "Cloud Pipeline refused the exchange (HTTP $STATUS${ERROR:+, $ERROR})." \
           "See the Cloud Pipeline API log for the reason." ;;
  esac
fi

echo "::add-mask::$TOKEN"

EXPIRES_IN="$(printf '%s' "$BODY" | jq -r '.expires_in // empty')"
echo "Issued a token valid for ${EXPIRES_IN:-an unreported number of} seconds"

{
  echo "token=$TOKEN"
  echo "api-url=$API_URL"
  echo "expires-in=$EXPIRES_IN"
} >>"$GITHUB_OUTPUT"

# API and API_TOKEN are the two variables `pipe` reads
{
  echo "API=$API_URL/"
  echo "API_TOKEN=$TOKEN"
} >>"$GITHUB_ENV"
