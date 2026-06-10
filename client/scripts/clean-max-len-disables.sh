#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=false
if [[ "${1:-}" == "--dry-run" ]]; then
  DRY_RUN=true
fi

echo "Running npm lint..."
LINT_OUTPUT=$(npm run lint 2>&1 || true)

echo ""
echo "Scanning lint output..."

# Match ANY unused eslint-disable directive (with or without rule name)
FILES=$(echo "$LINT_OUTPUT" | awk '
  /^[\/]/ { file=$0 }
  /Unused eslint-disable directive/ { print file }
')

if [[ -z "$FILES" ]]; then
  echo "No matching files found."
  exit 0
fi

echo ""
echo "Found files:"
echo "$FILES"
echo ""

MODIFIED=0

while IFS= read -r file; do
  [[ -z "$file" ]] && continue

  if [[ ! -f "$file" ]]; then
    echo "Skipping (not found): $file"
    continue
  fi

  # Check for ANY eslint-disable max-len or plain eslint-disable-next-line
  if grep -qE \
    '^[[:space:]]*/\* eslint-disable.*\*/$|^[[:space:]]*// eslint-disable-next-line([[:space:]]+max-len)?$' \
    "$file"; then

    echo "Match found in: $file"

    if [[ "$DRY_RUN" == true ]]; then
      echo "  [DRY RUN] Would remove unused eslint-disable directives"
    else
      if [[ "$OSTYPE" == "darwin"* ]]; then
        sed -i '' \
          -e '/^[[:space:]]*\/\* eslint-disable.*\*\/$/d' \
          -e '/^[[:space:]]*\/\/ eslint-disable-next-line\([[:space:]]\+max-len\)\?$/d' \
          "$file"
      else
        sed -i \
          -e '/^[[:space:]]*\/\* eslint-disable.*\*\/$/d' \
          -e '/^[[:space:]]*\/\/ eslint-disable-next-line\([[:space:]]\+max-len\)\?$/d' \
          "$file"
      fi

      echo "  Removed unused eslint-disable lines"
    fi

    ((MODIFIED++))
  else
    echo "No removable eslint-disable lines in: $file"
  fi
done <<< "$FILES"

echo ""
if [[ "$DRY_RUN" == true ]]; then
  echo "Dry run complete. Files that would be modified: $MODIFIED"
else
  echo "Done. Files modified: $MODIFIED"
fi
