#!/usr/bin/env bash
# Starts the client dev server against the deployment named in client/.env.test.local, on ports
# distinct from a developer's own `npm start`. Never prints CP_TEST_API_URL/CP_TEST_API_TOKEN or
# the SERVER/PROXY_BEARER_TOKEN derived from them. Run check-ui-local-available.sh first. See
# ../SKILL.md. Long-running — run this in the background.
set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
env_file="$repo_root/client/.env.test.local"

if [ ! -f "$env_file" ]; then
  echo "client/.env.test.local does not exist — run check-ui-local-available.sh first." >&2
  exit 1
fi

cp_url=$(grep -E '^CP_TEST_API_URL=' "$env_file" | tail -1 | cut -d= -f2-)
cp_token=$(grep -E '^CP_TEST_API_TOKEN=' "$env_file" | tail -1 | cut -d= -f2-)

if [ -z "$cp_url" ]; then
  echo "CP_TEST_API_URL is empty in client/.env.test.local — run check-ui-local-available.sh first." >&2
  exit 1
fi

export SERVER="$cp_url"
if [ -n "$cp_token" ]; then
  export PROXY_BEARER_TOKEN="$cp_token"
fi
export PORT=3100

# PROXY_PORT alone only moves where the proxy listens — the bundle's own injected SERVER value is
# a *separate* computation that defaults to a hardcoded http://localhost:9999 unless PROXY_SERVER
# is set explicitly. Setting PROXY_SERVER drives both sides consistently (it also takes priority
# for the proxy's own listen host/port), carrying whatever path prefix the real SERVER has.
cp_path=$(printf '%s' "$cp_url" | sed -E 's#^[a-zA-Z]+://[^/]+##')
export PROXY_SERVER="http://127.0.0.1:9199${cp_path}"

cd "$repo_root/client"

# client needs the Node major version named in .nvmrc — the shell's default is often a newer LTS,
# and this build fails on it with an opaque OpenSSL error rather than a clear version complaint.
required_major=$(cat .nvmrc | tr -d '[:space:]')
current_major=$(node -e 'console.log(process.versions.node.split(".")[0])' 2>/dev/null || echo '')

if [ "$current_major" != "$required_major" ]; then
  # Don't assume nvm, or where it lives — .agents/localenv/node.md (if present) records how this
  # workstation actually has Node set up; only fall back to the common default if it says nothing.
  nvm_dir="${NVM_DIR:-}"
  if [ -z "$nvm_dir" ] && [ -f "$repo_root/.agents/localenv/node.md" ]; then
    nvm_dir=$(grep -oE '~/\.nvm' "$repo_root/.agents/localenv/node.md" | head -1 | sed "s|~|$HOME|")
  fi
  nvm_dir="${nvm_dir:-$HOME/.nvm}"

  if [ -s "$nvm_dir/nvm.sh" ]; then
    # shellcheck disable=SC1091
    . "$nvm_dir/nvm.sh"
    nvm use >/dev/null 2>&1 || true
    current_major=$(node -e 'console.log(process.versions.node.split(".")[0])' 2>/dev/null || echo '')
  fi
fi

if [ "$current_major" != "$required_major" ]; then
  echo "Node $required_major is needed here (client/.nvmrc), but $(node --version 2>/dev/null || echo 'none') is active and nvm wasn't found at $nvm_dir." >&2
  echo "Check .agents/localenv/node.md for how this workstation has Node set up, switch manually, and re-run." >&2
  exit 1
fi

echo "Starting client on http://localhost:$PORT (local proxy on ${PROXY_SERVER#http://}) against the configured deployment..."
exec npm start
