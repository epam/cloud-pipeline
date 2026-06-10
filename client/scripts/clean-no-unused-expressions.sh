#!/usr/bin/env bash

# fix-unused-expressions.sh
# Runs `npm run lint`, finds @typescript-eslint/no-unused-expressions errors,
# and rewrites `XXXX && XXXX(...)` patterns into proper if-statements.

DRY_RUN=false
FIXES=0
SKIPPED=0
ERRORS=0

# ── Argument parsing ────────────────────────────────────────────────────────
for arg in "$@"; do
  case "$arg" in
    --dry-run) DRY_RUN=true ;;
    *) echo "Unknown argument: $arg"; exit 1 ;;
  esac
done

if $DRY_RUN; then
  echo "🔍  DRY-RUN mode — no files will be modified."
fi
echo ""

# ── Step 1: run npm run lint ────────────────────────────────────────────────
echo "▶  Running: npm run lint"
echo "──────────────────────────────────────────────────────────"

LINT_OUTPUT=$(npm run lint 2>&1 || true)

echo "$LINT_OUTPUT"
echo ""
echo "──────────────────────────────────────────────────────────"
echo "✔  lint output captured"
echo ""

# ── Step 2 & 3: parse output into a temp TSV: filepath<TAB>lineno ───────────
TMPFILE=$(mktemp /tmp/lint-fixes.XXXXXX)
trap 'rm -f "$TMPFILE"' EXIT

current_file=""

while IFS= read -r raw_line; do
  # File path line: no leading whitespace, ends with a known extension
  if echo "$raw_line" | grep -qE '^[^[:space:]].+\.(js|ts|jsx|tsx)$'; then
    current_file="$raw_line"
    continue
  fi

  # Error line for the target rule
  if echo "$raw_line" | grep -q '@typescript-eslint/no-unused-expressions'; then
    lineno=$(echo "$raw_line" | grep -oE '^[[:space:]]+[0-9]+' | tr -d '[:space:]')
    if [ -n "$lineno" ] && [ -n "$current_file" ]; then
      printf '%s\t%s\n' "$current_file" "$lineno" >> "$TMPFILE"
    fi
  fi
done <<< "$LINT_OUTPUT"

if [ ! -s "$TMPFILE" ]; then
  echo "✅  No @typescript-eslint/no-unused-expressions errors found. Nothing to do."
  exit 0
fi

echo "📋  Files with @typescript-eslint/no-unused-expressions errors:"
while IFS=$'\t' read -r filepath lineno; do
  echo "    $filepath  →  line $lineno"
done < "$TMPFILE"
echo ""

# ── Step 4: process each file / line ───────────────────────────────────────
# Pattern (Python regex, applied per line):
#   ^(\s*)(.+?)\s*&&\s*([^(]+?)(\(.*\));?\s*$
#
# When --dry-run, line numbers can shift across multiple fixes in the same file,
# so we process files together and patch lines in reverse order.

# Get unique files from tmpfile
UNIQUE_FILES=$(awk -F'\t' '{print $1}' "$TMPFILE" | sort -u)

for filepath in $UNIQUE_FILES; do
  if [ ! -f "$filepath" ]; then
    echo "⚠   File not found, skipping: $filepath"
    ERRORS=$((ERRORS + 1))
    continue
  fi

  echo "📄  Processing: $filepath"

  # Collect all line numbers for this file, sorted descending (avoids offset drift)
  LINE_NUMBERS=$(grep -F "$filepath" "$TMPFILE" | awk -F'\t' '{print $2}' | sort -rn)

  for lineno in $LINE_NUMBERS; do
    src_line=$(sed -n "${lineno}p" "$filepath")
    trimmed=$(echo "$src_line" | sed 's/^[[:space:]]*//')
    echo "    Line $lineno: $trimmed"

    # Delegate matching + replacement to Python (consistent regex across bash versions)
    RESULT=$(python3 - "$src_line" <<'PYEOF'
import sys, re

line = sys.argv[1]
pattern = r'^(\s*)(.+?)\s*&&\s*([\w$][^(]*?)(\(.*\))\s*;?\s*$'
m = re.match(pattern, line)
if not m:
    print("NO_MATCH")
else:
    indent, condition, call_name, call_args = m.group(1), m.group(2), m.group(3), m.group(4)
    call_name = call_name.rstrip()
    replacement = f"{indent}if ({condition}) {{\n{indent}  {call_name}{call_args};\n{indent}}}"
    print("MATCH")
    print(replacement)
PYEOF
)

    match_status=$(echo "$RESULT" | head -1)

    if [ "$match_status" = "NO_MATCH" ]; then
      echo "    ⏭️   Pattern NOT matched — skipping"
      SKIPPED=$((SKIPPED + 1))
    else
      replacement=$(echo "$RESULT" | tail -n +2)
      echo "    ✏️   Pattern matched:"
      echo "         Before : $src_line"
      echo "         After  :"
      echo "$replacement" | sed 's/^/                   /'

      if ! $DRY_RUN; then
        python3 - "$filepath" "$lineno" "$replacement" <<'PYEOF'
import sys

filepath, lineno_str, replacement = sys.argv[1], sys.argv[2], sys.argv[3]
lineno = int(lineno_str)

with open(filepath, 'r', encoding='utf-8') as fh:
    lines = fh.readlines()

orig_ending = '\r\n' if lines[lineno - 1].endswith('\r\n') else '\n'
new_lines = [l + orig_ending for l in replacement.splitlines()]
lines[lineno - 1:lineno] = new_lines

with open(filepath, 'w', encoding='utf-8') as fh:
    fh.writelines(lines)
PYEOF
        echo "    ✅  Fixed (line $lineno)"
      else
        echo "    💡  [dry-run] Would apply fix above"
      fi
      FIXES=$((FIXES + 1))
    fi

    echo ""
  done
done

# ── Summary ──────────────────────────────────────────────────────────────────
echo "══════════════════════════════════════════════════════════"
if $DRY_RUN; then
  echo "  DRY-RUN summary:"
  echo "    Would fix : $FIXES line(s)"
else
  echo "  Summary:"
  echo "    Fixed     : $FIXES line(s)"
fi
echo "    Skipped   : $SKIPPED line(s) (pattern not matched)"
echo "    Errors    : $ERRORS (files not found etc.)"
echo "══════════════════════════════════════════════════════════"

if ! $DRY_RUN && [ "$FIXES" -gt 0 ]; then
  echo ""
  echo "▶  Running: npm run format"
  echo "──────────────────────────────────────────────────────────"
  npm run format
  echo "──────────────────────────────────────────────────────────"
  echo "✔  format complete"
fi
