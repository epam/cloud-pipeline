#!/usr/bin/env bash
# Renames .js files containing JSX to .jsx and stages them in git.
# Detection: file imports from 'react' OR contains JSX syntax (angle-bracket tags).

set -euo pipefail

DRY_RUN=false
SEARCH_DIR="src"
VERBOSE=false
OVERWRITE=false

usage() {
  echo "Usage: $0 [--dry-run] [--dir <path>] [--verbose] [--overwrite]"
  echo "  --dry-run   Print renames without executing them"
  echo "  --dir       Directory to search (default: src)"
  echo "  --verbose   Print skipped files too"
  echo "  --overwrite Overwrite existing .jsx file if it already exists"
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)   DRY_RUN=true ;;
    --dir)       SEARCH_DIR="$2"; shift ;;
    --verbose)   VERBOSE=true ;;
    --overwrite) OVERWRITE=true ;;
    --help|-h)   usage ;;
    *) echo "Unknown option: $1"; usage ;;
  esac
  shift
done

if [[ ! -d "$SEARCH_DIR" ]]; then
  echo "Error: directory '$SEARCH_DIR' not found" >&2
  exit 1
fi

# Check we're inside a git repo
if ! git rev-parse --git-dir &>/dev/null; then
  echo "Error: not inside a git repository" >&2
  exit 1
fi

has_jsx() {
  local file="$1"

  # 1. Imports from 'react' (covers hooks, components, etc.)
  grep -qE "from ['\"]react['\"]" "$file" && return 0

  # 2. JSX fragment syntax <>...</>
  grep -qE '<>|</>' "$file" && return 0

  # 3. Capital-letter component tags: <Component, <Component.Sub
  grep -qE '<[A-Z][A-Za-z0-9.]*[ \t\n/>]' "$file" && return 0

  # 4. Common HTML tags used in JSX context after return / => / (
  grep -qE '(return|=>|\()\s*<[a-z]+[ \t\n/>]' "$file" && return 0

  return 1
}

total=0
matched=0
conflict=0
skipped=0

while IFS= read -r -d '' file; do
  total=$((total + 1))

  if has_jsx "$file"; then
    new_file="${file%.js}.jsx"

    if [[ -e "$new_file" ]]; then
      if [[ "$OVERWRITE" == false ]]; then
        conflict=$((conflict + 1))
        echo "  conflict $file  →  $new_file  (already exists, use --overwrite to replace)"
        continue
      fi
    fi

    matched=$((matched + 1))
    if [[ "$DRY_RUN" == true ]]; then
      if [[ -e "$new_file" ]]; then
        echo "  rename  $file  →  $new_file  (overwrites existing)"
      else
        echo "  rename  $file  →  $new_file"
      fi
    else
      git mv ${OVERWRITE:+--force} "$file" "$new_file"
      echo "  renamed $file  →  $new_file"
    fi
  else
    skipped=$((skipped + 1))
    if [[ "$VERBOSE" == true ]]; then
      echo "  skip    $file"
    fi
  fi
done < <(find "$SEARCH_DIR" -name "*.js" -type f -print0 | sort -z)

echo ""
echo "Scanned  : $total .js files"
if [[ "$DRY_RUN" == true ]]; then
  echo "Would rename : $matched  |  conflict: $conflict  |  skip: $skipped"
else
  echo "Renamed  : $matched  |  conflict: $conflict  |  skipped: $skipped"
fi
