#!/usr/bin/env bash
# Reports the local toolchain against what this repository needs.
# Read-only: installs nothing, edits nothing. See ../SKILL.md.
set -u

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"

have() { command -v "$1" >/dev/null 2>&1; }

row() { # row <label> <command> [version-args...]
  local label=$1 out; shift
  printf '  %-13s ' "$label"
  if ! have "$1"; then echo '(absent)'; return; fi
  # On PATH but unrunnable happens: a stale shim, a wrong-arch binary. Say so
  # rather than letting the error text masquerade as a version.
  local rc=0
  out=$("$@" 2>&1) || rc=$?
  out=$(printf '%s\n' "$out" | head -1)
  if [ "$rc" -ne 0 ] || [ -z "$out" ]; then
    echo "(on PATH at $(command -v "$1") but does not run: rc=$rc)"
  else
    echo "$out"
  fi
}

echo "== platform =="
printf '  %-13s %s %s\n' os "$(uname -s)" "$(uname -m)"
printf '  %-13s %s\n' shell "${SHELL:-unknown}"
printf '  %-13s %s\n' repo "$repo_root"

echo
echo "== node (ui: needs 14 · ui-modern: 16-18) =="
row node node --version
row npm npm --version
# nvm is a shell function, so command -v misses it; look for the directory.
printf '  %-13s ' nvm
if [ -s "${NVM_DIR:-$HOME/.nvm}/nvm.sh" ]; then
  echo "present ($( . "${NVM_DIR:-$HOME/.nvm}/nvm.sh" >/dev/null 2>&1 && nvm --version 2>/dev/null || echo 'unknown version' ))"
else
  echo '(absent)'
fi
row fnm fnm --version       # nvm alternatives; any of these can pin 14
row volta volta --version
row asdf asdf --version
# An installed-but-not-active Node 14 is the common case, and the active
# version says nothing about it. Enumerate the managers' version directories,
# as the java block does its JDK roots; the directory name is the version.
echo '  installed node versions:'
found_node=
for ver in \
  "${NVM_DIR:-$HOME/.nvm}"/versions/node/* \
  "$HOME"/.local/share/fnm/node-versions/*/installation \
  "$HOME"/Library/Application\ Support/fnm/node-versions/*/installation \
  "$HOME"/.volta/tools/image/node/* \
  "$HOME"/.asdf/installs/nodejs/*
do
  [ -x "$ver/bin/node" ] || continue
  found_node=1
  printf '    %s\n' "$ver"
done
[ -n "$found_node" ] || echo '    (none found outside PATH)'
printf '  %-13s ' client/.nvmrc
[ -f "$repo_root/client/.nvmrc" ] && cat "$repo_root/client/.nvmrc" || echo '(absent)'
printf '  %-13s ' node_modules
[ -d "$repo_root/client/node_modules" ] && echo 'client/ installed' || echo 'client/ not installed'

echo
echo "== java (needs JDK 8 · gradle wrapper 4.10.2 fails on 9+) =="
row java java -version
row javac javac -version
printf '  %-13s %s\n' JAVA_HOME "${JAVA_HOME:-(unset)}"
# Every platform hides its JDKs somewhere else, and an installed-but-not-active
# JDK 8 is the common case worth reporting. Glob the known roots; the shell
# leaves an unmatched pattern as-is, so test each hit for a real java binary.
echo '  installed JDKs:'
found_jdk=
for home in \
  /Library/Java/JavaVirtualMachines/*/Contents/Home \
  /usr/lib/jvm/* \
  /usr/java/* \
  /opt/java/* \
  "${SDKMAN_DIR:-$HOME/.sdkman}"/candidates/java/* \
  "$HOME"/.jdks/* \
  "$HOME"/.jabba/jdk/* \
  /c/Program\ Files/Java/* \
  /c/Program\ Files/Eclipse\ Adoptium/*
do
  [ -x "$home/bin/java" ] || continue
  found_jdk=1
  printf '    %-52s %s\n' "$home" "$("$home/bin/java" -version 2>&1 | head -1)"
done
[ -n "$found_jdk" ] || echo '    (none found in the standard locations)'
row gradle gradle --version   # a system gradle is a footgun here, not a requirement
printf '  %-13s %s\n' wrapper "$( [ -x "$repo_root/gradlew" ] && echo './gradlew present' || echo 'MISSING' )"

echo
echo "== database (api hardcodes port 5432; two databases, one per profile) =="
row psql psql --version
# The native installers put psql outside PATH, and an already-serving postgres
# is the case the setup must not duplicate. Report the listener, not just the client.
printf '  %-13s ' 'psql (native)'
native_psql=
for p in /Library/PostgreSQL/*/bin/psql /usr/pgsql-*/bin/psql /opt/homebrew/opt/postgresql*/bin/psql; do
  [ -x "$p" ] || continue
  native_psql=1
  printf '%s ' "$p"
done
[ -n "$native_psql" ] && echo || echo '(none outside PATH)'
printf '  %-13s ' 'server :5432'
# lsof cannot see a listener owned by another user (the native servers run as
# `postgres`), so ask the process table, not the socket.
pg_proc=$(pgrep -fl 'postgres -D|postmaster' 2>/dev/null | head -1)
pg_ctr=$(docker ps --filter ancestor=postgres --format '{{.Image}} {{.Ports}}' 2>/dev/null | head -1)
if [ -n "$pg_proc" ]; then echo "native: $pg_proc"
elif [ -n "$pg_ctr" ]; then echo "container: $pg_ctr"
else echo 'no server found (neither a postgres process nor a container)'
fi

echo
echo "== python (pipe-cli pins a 2.7-era set; other services are 3.x) =="
row python python --version
row python2 python2 --version
row python3 python3 --version
row pip3 pip3 --version
row pyenv pyenv --version
row conda conda --version
row mamba mamba --version

echo
echo "== github cli (gh over Bash is the only supported way agents touch GitHub) =="
row gh gh --version
printf '  %-13s ' auth
if ! have gh; then
  echo '(gh absent)'
else
  auth_out=$(gh auth status 2>&1)
  if [ $? -eq 0 ]; then
    echo "$auth_out" | grep -m1 'Logged in' | sed 's/^ *//'
  else
    echo 'not authenticated (gh auth login needed)'
  fi
fi

echo
echo "== docs / docker / git =="
row pipx pipx --version
row mkdocs mkdocs --version
row docker docker --version
row podman podman --version
printf '  %-13s ' daemon
if docker info >/dev/null 2>&1; then echo 'docker responding'
elif podman info >/dev/null 2>&1; then echo 'podman responding'
else echo 'not responding'; fi
row git git --version

echo
echo "== managers available =="
for m in brew apt-get dnf yum port winget; do have "$m" && printf '  %s\n' "$m"; done
[ -s "${SDKMAN_DIR:-$HOME/.sdkman}/bin/sdkman-init.sh" ] && echo '  sdkman'

echo
echo "== recorded decisions (.agents/localenv) =="
if [ -d "$repo_root/.agents/localenv" ]; then
  ls -1 "$repo_root/.agents/localenv" | sed 's/^/  /'
else
  echo '  (none - first run)'
fi
