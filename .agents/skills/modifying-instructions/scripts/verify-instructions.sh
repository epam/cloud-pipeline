#!/usr/bin/env bash
#
# Validates factual claims in the agent instruction files against the repository.
#
# Checks:
#   1. `./gradlew :module:task` references point at modules declared in settings.gradle
#   2. Every AGENTS.md has its CLAUDE.md twin, holding the import that makes it visible
#   3. Every repo-relative shape in .cursorignore is also denied to Claude Code and gitignored
#   4. Every pattern-scoped rule exists as all three vendor files, sharing one stem
#   5. Every skill in .agents/skills/ is spec-legal, and .claude/skills/ links to them
#
# It executes no build tooling. Written for bash 3.2 and POSIX awk, which is what macOS ships:
# no associative arrays, no `mapfile`, none of gawk's extensions. Keep it inside that, and inside
# grep, find and sed.
#
# Usage, from anywhere in the working tree:
#   .agents/skills/modifying-instructions/scripts/verify-instructions.sh
# Exit code 0 = clean, 1 = problems found.
#
# It is a support script of the modifying-instructions skill, per the Agent Skills package layout.
# Run it locally before committing instruction changes; CI runs it on those paths as well.
# See ../SKILL.md.

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

# Every path below is relative to the root, which keeps the reports, the file walk and the awk
# filenames in one vocabulary.
cd "$ROOT" || exit 1

TMP=$(mktemp -d "${TMPDIR:-/tmp}/verify-instructions.XXXXXX") || exit 1
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

  # The gitignored directories under .agents/ — a local plan, a ledger — are on disk and read like
  # the rest: a module named in one is named for this build.
  if [ -d .agents ]; then
    find .agents -type f -name '*.md' 2>/dev/null | sed 's|^\./||'
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

# ----------------------------------------------------------- module references

# A module name means the same thing wherever it is written, so every line of every file is read —
# a `./gradlew` line inside a fence is exactly where one is most likely to appear.
cat > "$TMP/module-refs.awk" <<'AWK'
{
  rest = $0
  while (match(rest, /\.\/gradlew[ \t]+(:[A-Za-z0-9_.-]+)+/)) {
    ref = substr(rest, RSTART, RLENGTH)
    rest = substr(rest, RSTART + RLENGTH)
    sub(/^\.\/gradlew[ \t]+/, "", ref)
    # the last segment is the task name; everything before it is the module path
    n = split(ref, seg, ":")
    mod = ""
    for (i = 2; i < n; i++) mod = (mod == "" ? seg[i] : mod ":" seg[i])
    if (mod != "") print FILENAME "\t" FNR "\t" mod
  }
}
AWK

# xargs, not "$(cat)": the file list is long enough that one command line is not guaranteed.
xargs awk -f "$TMP/module-refs.awk" \
  < "$TMP/instruction-files" > "$TMP/module-refs" 2>/dev/null

TAB=$(printf '\t')

while IFS=$TAB read -r file line value; do
  [ -n "$value" ] || continue
  grep -Fxq -- "$value" "$TMP/modules" ||
    report "$file" "$line" "gradle module ':$value' is not declared in settings.gradle"
done < "$TMP/module-refs"

# `dirname` in a command substitution is a fork; this is the same answer inline.
dir_of () {
  case $1 in
    */*) DIR=${1%/*} ;;
    *) DIR=. ;;
  esac
}

# ---------------------------------------------------------------------- twins

# Claude Code reads CLAUDE.md and not AGENTS.md, so an AGENTS.md with no twin beside it is
# invisible to it — no error, no warning, and nothing in the diff to see. The twin's whole job is
# the import line, so a twin that has lost it fails exactly as an absent one does.
#
# `/AGENTS.md`, not `AGENTS.md`: the .agents/ discovery rule above admits any .md, and a file
# merely ending in the name — MODULE_AGENTS.md — is not one and owes no twin.
#
# The twin's content is mechanical, so the report below names the script that writes it — a run of
# that, then a re-run of this, closes it out.
TWINS=.agents/skills/modifying-instructions/scripts/claude-md-twins.sh

while IFS= read -r file; do
  case $file in
    AGENTS.md|*/AGENTS.md) ;;
    *) continue ;;
  esac
  dir_of "$file"
  twin=$DIR/CLAUDE.md
  twin=${twin#./}
  if [ ! -f "$twin" ]; then
    report "$file" 1 "no CLAUDE.md beside it, so Claude Code never reads it — run $TWINS, \
or create $twin containing: @AGENTS.md"
  elif ! grep -q '^[[:space:]]*@AGENTS\.md[[:space:]]*$' "$twin"; then
    report "$twin" 1 "does not import the AGENTS.md beside it, so Claude Code reads this file \
instead of that one — its contents should be: @AGENTS.md"
  fi
done < "$TMP/instruction-files"

# ------------------------------------------------------------- deny-read lists

# The same repo-relative shapes have to be in three files, in three syntaxes, and none of them
# warns when one falls behind. .cursorignore is the plain list of them, so it is the one to read
# from; Claude's deny list also carries ~/ credential stores, which belong nowhere else.
#
# gitignore syntax implies what Claude's patterns must spell out: a bare *.ext matches at any depth
# (**/*.ext), and a trailing / means a directory's contents (/**). Those two rules cover the shapes
# this repository uses; a pattern needing a third is a sign the list has outgrown a mechanical check.
if [ -f .cursorignore ]; then
  grep -v '^[[:space:]]*#' .cursorignore | grep -v '^[[:space:]]*$' |
    sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' > "$TMP/shapes"

  while IFS= read -r shape; do
    case $shape in
      */)  claude="${shape}**" ;;
      \**) claude="**/$shape" ;;
      *)   claude=$shape ;;
    esac

    if [ -f .claude/settings.json ] &&
       ! grep -qF "\"Read($claude)\"" .claude/settings.json; then
      report .cursorignore 1 "'$shape' is not denied for Claude Code — add \"Read($claude)\" to \
permissions.deny in .claude/settings.json, or drop the shape from .cursorignore"
    fi

    if [ -f .gitignore ] && ! grep -qxF "$shape" .gitignore; then
      report .cursorignore 1 "'$shape' is unreadable but still committable — add it to .gitignore, \
or drop the shape from .cursorignore"
    fi
  done < "$TMP/shapes"
fi

# -------------------------------------------------------- pattern-scoped rules

# One rule is three files, one per vendor, sharing a stem — no vendor reads another's directory, and
# each spells the glob field its own way, so a stem present in one directory and absent from another
# is a rule that silently does not apply for that vendor.
#
# Existence is all that is checked: the prose is the author's, and the glob differs by vendor by
# construction. The stem is the basename, so a rule may sit in a subdirectory of any of the three.
#
# An NN- prefix marks an always-applied pointer at AGENTS.md rather than a rule — a different kind of
# file, which stands alone and owes no siblings. README is prose about the directory, likewise.
missing_rule () {   # missing_rule <dir> <filename> <stem>
  report_bare "$1/$2 — absent, so the '$3' rule is off for that vendor. One pattern-scoped rule is \
three files, one per vendor, sharing a stem"
}

stems_of () {   # stems_of <file> <dir> <find-name> <suffix-to-strip>
  : > "$1"
  [ -d "$2" ] || return 0
  find "$2" -type f -name "$3" 2>/dev/null | sed -e 's|.*/||' -e "s|$4\$||" |
    grep -v '^$' | grep -vx 'README' | grep -v '^[0-9][0-9]*-' | sort -u > "$1"
  return 0
}

stems_of "$TMP/rules-claude"   .claude/rules        '*.md'              '\.md'
stems_of "$TMP/rules-cursor"   .cursor/rules        '*.mdc'             '\.mdc'
stems_of "$TMP/rules-copilot"  .github/instructions '*.instructions.md' '\.instructions\.md'

sort -u "$TMP/rules-claude" "$TMP/rules-cursor" "$TMP/rules-copilot" > "$TMP/rule-stems"

while IFS= read -r stem; do
  [ -n "$stem" ] || continue
  grep -qx "$stem" "$TMP/rules-claude"  || missing_rule .claude/rules "$stem.md" "$stem"
  grep -qx "$stem" "$TMP/rules-cursor"  || missing_rule .cursor/rules "$stem.mdc" "$stem"
  grep -qx "$stem" "$TMP/rules-copilot" ||
    missing_rule .github/instructions "$stem.instructions.md" "$stem"
done < "$TMP/rule-stems"

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
vendor can read them. Move anything inside it there and $HINT"
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
    echo 'These are stale claims in agent instructions — fix the file, or what it names.'
  else
    echo 'No problems found.'
  fi
} > "$OUT"

cat "$OUT"
[ -s "$PROBLEMS" ] && exit 1
exit 0
