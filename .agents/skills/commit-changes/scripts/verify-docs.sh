#!/usr/bin/env bash
#
# Validates factual claims in the agent instruction files against the repository.
#
# Checks:
#   1. `./gradlew :module:task` references point at modules declared in settings.gradle
#   2. Backticked file and directory paths exist — including paths inside a backticked command
#   3. Every AGENTS.md has its CLAUDE.md twin, holding the import that makes it visible
#   4. Every skill in .agents/skills/ is spec-legal, and .claude/skills/ links to them
#
# Every check is decidable from the repository as a whole. A claim whose truth depends on which
# directory the reader stands in — which package.json a script belongs to, which runner a module
# has — belongs in that directory's AGENTS.md, where the reader is known.
#
# It executes no build tooling. Written for bash 3.2 and POSIX awk, which is what macOS ships:
# no associative arrays, no `mapfile`, none of gawk's extensions. Keep it inside that, and inside
# grep, find, sed and git.
#
# Usage, from anywhere in the working tree:
#   .agents/skills/commit-changes/scripts/verify-docs.sh
# Exit code 0 = clean, 1 = problems found.
#
# It is a support script of the commit-changes skill, per the Agent Skills package layout, and
# has no CI job: it is run locally before committing. See ../SKILL.md.

# This file lives inside a skill package, so its depth below the repository root is a property of
# the skill layout, not something to hardcode — and it can be reached either directly or through
# the .claude/skills/ symlink. Walk up to a marker instead: settings.gradle defines the root here,
# and the checks below read it anyway.
ROOT=$(cd "$(dirname "$0")" && pwd -P)
while [ ! -f "$ROOT/settings.gradle" ]; do
  parent=$(dirname "$ROOT")
  if [ "$parent" = "$ROOT" ]; then
    echo "Cannot locate the repository root: no settings.gradle in any parent of this script." >&2
    exit 1
  fi
  ROOT=$parent
done

# Every path below is relative to the root, which keeps the reports, the git queries and the awk
# filenames in one vocabulary.
cd "$ROOT" || exit 1

TMP=$(mktemp -d "${TMPDIR:-/tmp}/verify-docs.XXXXXX") || exit 1
trap 'rm -rf "$TMP"' EXIT HUP INT TERM

# LC_ALL=C so the file listing sorts by byte, the same order on every machine.
LC_ALL=C
export LC_ALL

PROBLEMS=$TMP/problems
: > "$PROBLEMS"

report () {   # report <file> <line> <message>
  printf '%s:%s — %s\n' "$1" "$2" "$3" >> "$PROBLEMS"
}

report_bare () {   # report <message>, where there is no line to point at
  printf '%s\n' "$1" >> "$PROBLEMS"
}

# ------------------------------------------------------------------- discovery

# Build outputs and vendored trees hold copies of anything, and none of it is an instruction.
SKIP=( -name node_modules -o -name build -o -name dist -o -name .gradle \
       -o -name site -o -name venv -o -name __pycache__ )

# A nested checkout holds a second copy of AGENTS.md and CLAUDE.md, which would be reported at the
# copy's path instead of the original's. Match the marker rather than a path: a worktree's .git is
# a file, a clone's is a directory, and either can sit anywhere.
find . \( "${SKIP[@]}" \) -prune -o -name .git -print -prune 2>/dev/null |
  sed -e 's|^\./||' -e 's|/*\.git$||' |
  grep -v '^\.git$' | grep -v '^$' | sed 's|$|/|' | sort -u > "$TMP/nested"

# Two patterns match at any depth; every other one is anchored at the root, so only these need the
# tree walked at all.
find . \( "${SKIP[@]}" -o -name .git \) -prune -o \
  -type f \( -name AGENTS.md -o -name CLAUDE.md \) -print 2>/dev/null |
  sed 's|^\./||' > "$TMP/found"

# A plan or a ledger is a work artifact, not an instruction: a plan names the files a task is about
# to create, so checking that its paths exist would fail by design. Both directories are exempt —
# the local one is gitignored but still on disk, so the walk finds it. The README in the tracked one
# is prose about the layout, so it is checked like anything else.
is_plan_artifact () {
  case $1 in
    */README.md) return 1 ;;
    .agents/plans/*|.agents/plans.local/*) return 0 ;;
  esac
  return 1
}

{
  while IFS= read -r f; do
    [ -n "$f" ] || continue
    skip=false
    while IFS= read -r prefix; do
      [ -n "$prefix" ] || continue
      case "$f/" in "$prefix"*) skip=true; break ;; esac
    done < "$TMP/nested"
    $skip || printf '%s\n' "$f"
  done < "$TMP/found"

  for anchored in .claude/rules .cursor/rules .github/instructions; do
    [ -d "$anchored" ] && find "$anchored" -type f 2>/dev/null
  done
  [ -f .github/copilot-instructions.md ] && echo .github/copilot-instructions.md

  if [ -d .agents ]; then
    find .agents -type f -name '*.md' 2>/dev/null | sed 's|^\./||' |
      while IFS= read -r f; do
        is_plan_artifact "$f" || printf '%s\n' "$f"
      done
  fi
} | sort -u > "$TMP/instruction-files"

# ------------------------------------------------------------------ known facts

# `include 'data-sharing-service:api'` also implies `data-sharing-service`.
awk '
  /^[ \t]*include[ \t]+'"'"'/ {
    if (!match($0, /'"'"'[^'"'"']+'"'"'/)) next
    path = substr($0, RSTART + 1, RLENGTH - 2)
    print path
    n = split(path, seg, ":")
    acc = ""
    for (i = 1; i <= n; i++) {
      acc = (acc == "" ? seg[i] : acc ":" seg[i])
      print acc
    }
  }
' settings.gradle | sort -u > "$TMP/modules"

find . -mindepth 1 -maxdepth 1 2>/dev/null | sed 's|^\./||' | sort > "$TMP/toplevel"

# ------------------------------------------------------------------ candidates

# awk finds what the files claim; the shell decides whether each claim holds. Splitting it that way
# keeps the file-system and git questions in the language that can ask them.
cat > "$TMP/candidates.awk" <<'AWK'
BEGIN {
  while ((getline name < TOPLEVEL) > 0) TOP[name] = 1
}

function looks_like_path (s,   head) {
  if (index(s, "/") == 0) return 0
  if (s ~ /[ \t]/) return 0
  if (s ~ /[<>{}$*?|]/) return 0                       # placeholders and globs
  if (s ~ /^(https?|mailto):/) return 0
  if (index(s, "://") > 0) return 0
  if (s ~ /^[A-Z_]+=/) return 0                        # env assignments
  head = s
  sub(/^\.\.?\//, "", head)
  sub(/\/.*$/, "", head)
  if (head in TOP) return 1
  if (s ~ /^\.\.?\//) return 1
  return (s ~ /\.(md|js|mjs|json|java|py|sql|xml|sh|gradle|properties|yml|yaml|css|conf|lua|svg|tar\.gz|jar|zip|vsix)$/)
}

FNR == 1 { in_fence = 0 }

{
  if ($0 ~ /^[ \t]*```/) in_fence = !in_fence

  # --- gradle module references (read inside fences too: that is where commands live)
  rest = $0
  while (match(rest, /\.\/gradlew[ \t]+(:[A-Za-z0-9_.-]+)+/)) {
    ref = substr(rest, RSTART, RLENGTH)
    rest = substr(rest, RSTART + RLENGTH)
    sub(/^\.\/gradlew[ \t]+/, "", ref)
    # the last segment is the task name; everything before it is the module path
    n = split(ref, seg, ":")
    mod = ""
    for (i = 2; i < n; i++) mod = (mod == "" ? seg[i] : mod ":" seg[i])
    if (mod != "") print "G\t" FILENAME "\t" FNR "\t" mod
  }

  if (in_fence) next                                   # path checks only outside code fences

  # --- backticked paths
  split("", seen)
  rest = $0
  while (match(rest, /`[^`]+`/)) {
    span = substr(rest, RSTART + 1, RLENGTH - 2)
    rest = substr(rest, RSTART + RLENGTH)
    # A backtick usually holds a whole command rather than a bare path — `node a/b/c.mjs`,
    # `cat api/foo.xml`. Check every whitespace-separated token, because skipping anything
    # containing a space meant a renamed script silently rotted each doc showing how to run it.
    m = split(span, toks, /[ \t]+/)
    for (i = 1; i <= m; i++) {
      c = toks[i]
      sub(/^["']+/, "", c)                             # quoted argument
      sub(/["']+$/, "", c)
      sub(/[),.:;]+$/, "", c)                          # trailing prose punctuation
      if (c == "" || (c in seen)) continue
      seen[c] = 1
      if (looks_like_path(c)) print "P\t" FILENAME "\t" FNR "\t" c
    }
  }
}
AWK

# xargs, not "$(cat)": the file list is long enough that one command line is not guaranteed.
xargs awk -v TOPLEVEL="$TMP/toplevel" -f "$TMP/candidates.awk" \
  < "$TMP/instruction-files" > "$TMP/candidates" 2>/dev/null

# ------------------------------------------------------------------ resolution

# Collapse . and .. textually, because the path is a claim and need not exist yet. Sets NORM, and
# returns 1 for a path that climbs out of the repository — the caller's signal to give up on it.
# It assigns rather than printing because `$(normalize ...)` would fork once per candidate per
# base, and there are hundreds.
normalize () {
  local part out=
  local old_ifs=$IFS
  set -f                      # a candidate can hold a bracket; do not let the shell glob it
  IFS=/
  for part in $1; do
    case $part in
      ''|.) ;;
      ..) case $out in
            '') set +f; IFS=$old_ifs; return 1 ;;      # above the root
            *) out=${out%/*} ;;
          esac ;;
      *) out=$out/$part ;;
    esac
  done
  set +f
  IFS=$old_ifs
  NORM=${out#/}
}

# `dirname` in a command substitution is a fork; this is the same answer inline.
dir_of () {
  case $1 in
    */*) DIR=${1%/*} ;;
    *) DIR=. ;;
  esac
}

TAB=$(printf '\t')

# One `git check-ignore` for the whole run, not one per path. A gitignored path is a build output
# or a local-only file: documenting it is correct even though it is absent from a clean checkout.
# Ask git rather than maintaining a list.
: > "$TMP/queries"
while IFS=$TAB read -r kind file line value; do
  [ "$kind" = P ] || continue
  dir_of "$file"
  for base in "$DIR" .; do
    normalize "$base/$value" || continue
    [ -n "$NORM" ] || continue
    printf '%s\n%s/\n' "$NORM" "$NORM" >> "$TMP/queries"
  done
done < "$TMP/candidates"

: > "$TMP/ignored"
if [ -s "$TMP/queries" ] && command -v git >/dev/null 2>&1; then
  sort -u "$TMP/queries" > "$TMP/queries.uniq"
  # A directory-only .gitignore pattern (`foo/`) only matches a query that keeps the trailing
  # slash, so both forms went in above. Paths git refuses are simply not ignored.
  git check-ignore --stdin < "$TMP/queries.uniq" > "$TMP/ignored" 2>/dev/null
fi

path_holds () {   # path_holds <file> <candidate>
  local base
  dir_of "$1"
  for base in "$DIR" .; do
    normalize "$base/$2" || continue
    [ -n "$NORM" ] || continue
    [ -e "$NORM" ] && return 0
    grep -Fxq "$NORM" "$TMP/ignored" && return 0
    grep -Fxq "$NORM/" "$TMP/ignored" && return 0
  done
  return 1
}

while IFS=$TAB read -r kind file line value; do
  case $kind in
    G)
      grep -Fxq "$value" "$TMP/modules" ||
        report "$file" "$line" "gradle module ':$value' is not declared in settings.gradle"
      ;;
    P)
      path_holds "$file" "$value" ||
        report "$file" "$line" "path '$value' does not exist"
      ;;
  esac
done < "$TMP/candidates"

# ---------------------------------------------------------------------- twins

# Claude Code reads CLAUDE.md and not AGENTS.md, so an AGENTS.md with no twin beside it is
# invisible to it — no error, no warning, and nothing in the diff to see. The twin's whole job is
# the import line, so a twin that has lost it fails exactly as an absent one does.
#
# `/AGENTS.md`, not `AGENTS.md`: the .agents/ discovery rule above admits any .md, and a file
# merely ending in the name — MODULE_AGENTS.md — is not one and owes no twin.
while IFS= read -r file; do
  case $file in
    AGENTS.md|*/AGENTS.md) ;;
    *) continue ;;
  esac
  dir_of "$file"
  twin=$DIR/CLAUDE.md
  twin=${twin#./}
  if [ ! -f "$twin" ]; then
    report "$file" 1 "no CLAUDE.md beside it, so Claude Code never reads it — create $twin \
containing: @AGENTS.md"
  elif ! grep -q '^[[:space:]]*@AGENTS\.md[[:space:]]*$' "$twin"; then
    report "$twin" 1 "does not import the AGENTS.md beside it, so Claude Code reads this file \
instead of that one — its contents should be: @AGENTS.md"
  fi
done < "$TMP/instruction-files"

# ------------------------------------------------- skills (Agent Skills spec)

# The spec (https://agentskills.io/specification) allows exactly these six fields. Anything else is
# a hard error when a skill is packaged or uploaded, so reject it here rather than letting it fail
# at distribution time.
# Space separated so `case` can match one word against it; the report reads better with commas.
SKILL_FIELDS="name description license compatibility metadata allowed-tools"
SKILL_FIELDS_LIST=$(printf '%s' "$SKILL_FIELDS" | sed 's/ /, /g')

SKILLS_SRC=.agents/skills
SKILLS_LINK_DIR=.claude/skills

# Top-level keys only, and nothing past the closing delimiter. Prints key<TAB>value; exits 1 when
# there is no frontmatter at all, or it never terminates.
cat > "$TMP/frontmatter.awk" <<'AWK'
NR == 1 {
  if ($0 !~ /^---[ \t]*$/) exit 1
  next
}
/^---[ \t]*$/ { closed = 1; exit 0 }
{
  if (match($0, /^[A-Za-z][A-Za-z0-9_-]*[ \t]*:/)) {
    key = substr($0, 1, RLENGTH)
    sub(/[ \t]*:$/, "", key)
    value = substr($0, RLENGTH + 1)
    sub(/^[ \t]+/, "", value)
    sub(/[ \t]+$/, "", value)
    print key "\t" value
  }
}
END { if (!closed) exit 1 }
AWK

is_quoted () {
  case $1 in
    \'*\'|\"*\") return 0 ;;
  esac
  return 1
}

if [ -d "$SKILLS_SRC" ]; then
  for path in "$SKILLS_SRC"/*; do
    [ -d "$path" ] || continue                          # README.md and friends
    dir=$(basename "$path")
    skill_md=$path/SKILL.md

    if [ ! -f "$skill_md" ]; then
      report_bare "$SKILLS_SRC/$dir — no SKILL.md; a skill directory must contain one"
      continue
    fi

    if ! awk -f "$TMP/frontmatter.awk" "$skill_md" > "$TMP/fm" 2>/dev/null; then
      report "$skill_md" 1 "missing or unterminated YAML frontmatter (--- ... ---)"
      continue
    fi

    name=
    description=
    while IFS=$TAB read -r key value; do
      [ -n "$key" ] || continue
      case " $SKILL_FIELDS " in
        *" $key "*) ;;
        *) report_bare "$skill_md — frontmatter field '$key' is not in the Agent Skills spec \
(allowed: $SKILL_FIELDS_LIST)" ;;
      esac
      # An unquoted YAML scalar cannot contain ': ' — the loader reads it as a nested mapping and
      # the whole skill silently fails to load.
      case $value in
        *": "*) is_quoted "$value" || report_bare "$skill_md — frontmatter '$key' contains ': ' \
in an unquoted value, which does not parse as YAML; drop the colon or quote the value" ;;
      esac
      case $key in
        name) name=$value ;;
        description) description=$value ;;
      esac
    done < "$TMP/fm"

    name_re='^[a-z0-9]+(-[a-z0-9]+)*$'
    if [ -z "$name" ]; then
      report_bare "$skill_md — frontmatter is missing the required 'name' field"
    elif [ "$name" != "$dir" ]; then
      report_bare "$skill_md — name '$name' must equal the directory name '$dir'"
    elif ! [[ $name =~ $name_re ]] || [ ${#name} -gt 64 ]; then
      report_bare "$skill_md — name '$name' must be 1-64 chars of lowercase a-z0-9 and single \
hyphens, not leading or trailing"
    fi

    if [ -z "$description" ]; then
      report_bare "$skill_md — frontmatter is missing the required 'description' field"
    elif [ ${#description} -gt 1024 ]; then
      report_bare "$skill_md — description is ${#description} chars; the spec caps it at 1024"
    fi
  done
fi

# Claude Code reads only .claude/skills/, which is a symlink to .agents/skills/ — one link for
# every skill, so there is nothing per-skill to check and no stale link to go looking for.
HINT='run: ln -s ../.agents/skills .claude/skills'
if [ ! -L "$SKILLS_LINK_DIR" ] && [ ! -e "$SKILLS_LINK_DIR" ]; then
  report_bare ".claude/skills — missing; Claude Code can see no skill at all. $HINT"
elif [ -L "$SKILLS_LINK_DIR" ] && [ ! -e "$SKILLS_LINK_DIR" ]; then
  report_bare ".claude/skills — broken symlink, points at nothing. $HINT"
elif [ ! -L "$SKILLS_LINK_DIR" ] && [ -f "$SKILLS_LINK_DIR" ]; then
  # git with core.symlinks=false checks a symlink out as a text file holding its target.
  report_bare ".claude/skills — a regular file, not a symlink. git checked the link out as text \
(core.symlinks=false on Windows). Set core.symlinks=true and re-checkout, or $HINT"
elif [ ! -L "$SKILLS_LINK_DIR" ]; then
  report_bare ".claude/skills — a real directory, but skills live in .agents/skills/ so every \
client can read them. Move anything inside it there and $HINT"
else
  target=$(cd "$SKILLS_LINK_DIR" 2>/dev/null && pwd -P)
  intended=$(cd "$SKILLS_SRC" 2>/dev/null && pwd -P)
  if [ "$target" != "$intended" ]; then
    report_bare ".claude/skills — symlink resolves to $target, not .agents/skills"
  fi
fi

# ---------------------------------------------------------------------- output

# Assembled first, then written by one `cat`. A shell builtin that meets a closed pipe — the
# `| head` every reader eventually types — reports "write error: Broken pipe" on stderr rather
# than dying quietly, and a verifier must not invent output of its own.
OUT=$TMP/out
count=$(wc -l < "$TMP/instruction-files" | tr -d ' ')
{
  echo "Checked $count instruction file(s):"
  sed 's|^|  |' "$TMP/instruction-files"
  echo ''
  if [ -s "$PROBLEMS" ]; then
    cat "$PROBLEMS"
    problem_count=$(wc -l < "$PROBLEMS" | tr -d ' ')
    printf '\n%s problem(s) found.\n' "$problem_count"
    echo 'These are stale claims in agent instructions — fix the doc, or the path/command it names.'
  else
    echo 'No problems found.'
  fi
} > "$OUT"

cat "$OUT"
[ -s "$PROBLEMS" ] && exit 1
exit 0
