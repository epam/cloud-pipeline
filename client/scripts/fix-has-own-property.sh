#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${1:-src}"

find "$ROOT_DIR" \
  -type f \
  \( -name "*.js" -o -name "*.jsx" -o -name "*.ts" -o -name "*.tsx" \) \
  -print0 |
while IFS= read -r -d '' file; do
  perl -i -pe '
    s/Object\.(?:prototype\.)?hasOwnProperty\.call\s*\(\s*([^,]+?)\s*,\s*([^)]+?)\s*\)/Object.hasOwn($1, $2)/g;
    s/((?:[A-Za-z_$][\w$]*)(?:\.[A-Za-z_$][\w$]*|\[[^\]]+\])+)\.hasOwnProperty\s*\(\s*([^)]+?)\s*\)/Object.hasOwn($1, $2)/g;
    s/([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)+)\?\.hasOwnProperty\s*\(\s*([^)]+?)\s*\)/Object.hasOwn($1, $2)/g;
    s/\b([A-Za-z_$][A-Za-z0-9_$.]*)\.hasOwnProperty\s*\(\s*([^)]+?)\s*\)/Object.hasOwn($1, $2)/g;
  ' "$file"
done

echo "Done."
