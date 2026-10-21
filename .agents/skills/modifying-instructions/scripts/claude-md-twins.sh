#!/usr/bin/env bash
#
# Creates the CLAUDE.md twin beside every AGENTS.md that has none, and stages it. The twin holds one
# line — `@AGENTS.md` — and without it Claude Code reads no AGENTS.md at all.
#
# It writes nothing else: whether a directory earns an AGENTS.md, and what it says, is yours.
#
# A CLAUDE.md that exists but has lost the import is reported, not replaced: it holds something a
# person wrote. What is created gets staged, since an untracked file is invisible to the checks, to
# the diff and to the commit.
#
# Usage, from anywhere in the working tree:
#   .agents/skills/modifying-instructions/scripts/claude-md-twins.sh [<dir> ...]
# With no arguments it covers every AGENTS.md in the working tree. A directory argument may be
# written relative to where you are, relative to the repository root, or absolute.
# Exit code 0 = every twin is in place, 1 = something is left for you.
#
# Written for bash 3.2 and POSIX tools, which is what macOS ships.
#
# It is a support script of the modifying-instructions skill, per the Agent Skills package layout.
# Missing twins are reported by verify-instructions.sh in this directory, which names this script
# in the report. See ../SKILL.md.

# Reachable directly or through the .claude/skills/ symlink, so walk up to the marker that defines
# the repository root rather than counting directories.
ROOT=$(cd "$(dirname "$0")" && pwd -P)
while [ ! -f "$ROOT/settings.gradle" ]; do
  parent=$(dirname "$ROOT")
  if [ "$parent" = "$ROOT" ]; then
    echo "Cannot locate the repository root: no settings.gradle in any parent of this script." >&2
    exit 1
  fi
  ROOT=$parent
done

TMP=$(mktemp -d "${TMPDIR:-/tmp}/claude-md-twins.XXXXXX") || exit 1
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

VERIFIER=.agents/skills/modifying-instructions/scripts/verify-instructions.sh

status=0
created=0

# Arguments are resolved here, while the invoking directory is still the current one. cd + pwd -P
# does the whole job: absolute or relative, trailing slashes, `..`, a symlinked path — all of it
# comes back as one physical path to compare against the root.
root_relative () {   # root_relative <dir> — print it relative to the repository root, or fail
  local dir
  dir=$(cd "$1" 2>/dev/null && pwd -P) || return 1
  case $dir in
    "$ROOT")   printf '.' ;;
    "$ROOT"/*) printf '%s' "${dir#"$ROOT"/}" ;;
    *)         return 1 ;;               # outside the repository
  esac
}

if [ $# -gt 0 ]; then
  : > "$TMP/dirs"
  for arg in "$@"; do
    arg=${arg%/AGENTS.md}                 # a path to the file itself is what you have in hand
    [ -n "$arg" ] && arg=${arg%/}
    [ -n "$arg" ] || arg=/
    # Where you are, then the repository root: both spellings of a directory name work.
    if ! dir=$(root_relative "$arg") && ! dir=$(root_relative "$ROOT/$arg"); then
      printf '%s: not a directory inside this repository.\n' "$arg" >&2
      status=1
      continue
    fi
    printf '%s\n' "$dir" >> "$TMP/dirs"
  done
else
  # git's own listing, rather than a find that has to skip build outputs and vendored trees: those
  # are gitignored already, and an AGENTS.md git cannot see is not in the repository. quotePath off,
  # or a path outside ASCII comes back C-quoted and matches nothing below.
  #
  # -C the root, because a pathspec and the listing it prints are both relative to where git runs.
  # Run from a subdirectory without it, this covered that subdirectory alone and called the result
  # every AGENTS.md in the repository; run from outside the tree it covered nothing and still
  # reported every twin in place.
  #
  # Its exit status decides whether the listing means anything: piped straight into sed, a git that
  # cannot run — absent from PATH, a source export with no .git, a checkout git calls dubiously
  # owned — yields an empty listing, which reads as a repository whose every twin is already there.
  if ! git -C "$ROOT" -c core.quotePath=false \
       ls-files -co --exclude-standard -- 'AGENTS.md' '*/AGENTS.md' > "$TMP/listing"; then
    printf 'git could not list the AGENTS.md files under %s, so nothing was checked.\n' "$ROOT" >&2
    exit 1
  fi
  sed -e 's|/AGENTS\.md$||' -e 's|^AGENTS\.md$|.|' "$TMP/listing" > "$TMP/dirs"
fi

# Every path from here on is relative to the root, which keeps what is printed, what is written and
# what is staged in one vocabulary.
cd "$ROOT" || exit 1

while IFS= read -r dir; do
  [ -n "$dir" ] || continue
  dir=${dir#./}

  agents=$dir/AGENTS.md
  agents=${agents#./}
  if [ ! -f "$agents" ]; then
    printf '%s: no AGENTS.md here — write that one first.\n' "$dir" >&2
    status=1
    continue
  fi

  twin=$dir/CLAUDE.md
  twin=${twin#./}
  if [ -f "$twin" ]; then
    grep -q '^[[:space:]]*@AGENTS\.md[[:space:]]*$' "$twin" && continue
    printf '%s: exists but does not import @AGENTS.md — left alone, a person wrote it.\n' \
      "$twin" >&2
    status=1
    continue
  fi

  printf '@AGENTS.md\n' > "$twin" || exit 1
  created=$((created + 1))
  printf 'created  %s\n' "$twin"
  git add -- "$twin" 2>/dev/null && continue
  printf '%s: created, but git add failed — stage it yourself.\n' "$twin" >&2
  status=1
done < "$TMP/dirs"

if [ "$created" -gt 0 ]; then
  printf '\nNow re-run the verifier: %s\n' "$VERIFIER"
elif [ "$status" -eq 0 ]; then
  echo 'Nothing to create: every AGENTS.md has its twin.'
fi

[ "$status" -eq 0 ] || exit 1
exit 0
