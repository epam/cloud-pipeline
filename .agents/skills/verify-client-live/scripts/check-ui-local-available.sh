#!/usr/bin/env bash
# Reports whether client/.env.test.local is configured for a live deployment, without ever
# printing its contents — the agent calling this must not learn CP_TEST_API_URL/CP_TEST_API_TOKEN,
# only whether they're set. See ../SKILL.md.
set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
env_file="$repo_root/client/.env.test.local"

if [ ! -f "$env_file" ]; then
  echo "not available: client/.env.test.local does not exist"
  echo "Ask the user to create it with CP_TEST_API_URL (and CP_TEST_API_TOKEN, if the deployment needs one)."
  exit 1
fi

if grep -qE '^CP_TEST_API_URL=.+' "$env_file"; then
  echo "available"
  exit 0
fi

echo "not available: client/.env.test.local exists but CP_TEST_API_URL is empty or missing"
echo "Ask the user to set CP_TEST_API_URL in it (and CP_TEST_API_TOKEN, if the deployment needs one)."
exit 1
