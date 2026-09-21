#!/usr/bin/env bash
# Installs Claude Code if it is missing, then writes ~/.claude/settings.json for this team's Amazon
# Bedrock inference profiles. Prompts for the four values it cannot know, never echoing the secret
# ones; an existing settings file is backed up before it is replaced.
# Windows has its own copy of this, setup-claude-code.ps1. See README.md.
set -uo pipefail

settings_dir="$HOME/.claude"
settings_file="$settings_dir/settings.json"

if [ ! -t 0 ]; then
  echo "This script asks questions, so run it directly rather than piping it into a shell." >&2
  exit 1
fi

# --- Claude Code -------------------------------------------------------------------------------

if command -v claude >/dev/null 2>&1; then
  echo "Claude Code is already installed: $(command -v claude)"
else
  echo "Installing Claude Code..."
  if ! curl -fsSL https://claude.ai/install.sh | bash; then
    echo "The installer failed. Install Claude Code by hand, then run this script again." >&2
    exit 1
  fi
  if ! command -v claude >/dev/null 2>&1; then
    echo "Installed, but 'claude' is not on PATH in this shell yet — open a new one, or add"
    echo "~/.local/bin to PATH. The settings file below is written either way."
  fi
fi

# --- the values only you have ------------------------------------------------------------------

abort() {
  echo
  echo "Aborted — no settings were written." >&2
  exit 1
}

clean() { # trim the surrounding whitespace, drop control characters
  printf '%s' "$1" | tr -d '[:cntrl:]' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

current() { # current <env-key> — what the settings file you already have holds, if anything
  [ -f "$settings_file" ] || return 0
  command -v python3 >/dev/null 2>&1 || return 0
  python3 - "$settings_file" "$1" 2>/dev/null <<'PY'
import json, sys
try:
    env = json.load(open(sys.argv[1])).get("env") or {}
except Exception:
    sys.exit(0)
value = env.get(sys.argv[2])
if isinstance(value, str):
    print(value)
PY
}

ask() { # ask <variable-name> <prompt> <current-value> [hidden]
  local name="$1" prompt="$2" current="$3" hidden="${4:-}" shown="$2" value=""
  if [ -n "$current" ]; then
    if [ -n "$hidden" ]; then
      shown="$prompt [Enter keeps the current one]"
    else
      shown="$prompt [$current]"
    fi
  fi
  while true; do
    if [ -n "$hidden" ]; then
      read -rsp "$shown: " value || abort
      echo
    else
      read -rp "$shown: " value || abort
    fi
    value="$(clean "$value")"
    [ -n "$value" ] || value="$current"
    [ -n "$value" ] && break
    echo "  A value is required."
  done
  printf -v "$name" '%s' "$value"
}

json_escape() {
  local s="$1"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '%s' "$s"
}

echo
echo "Paste the values you were given. Ask your team lead if you do not have them."
ask sonnet_profile "Sonnet 5 inference profile ID" "$(current ANTHROPIC_DEFAULT_SONNET_MODEL)"
ask opus_profile   "Opus 5 inference profile ID"   "$(current ANTHROPIC_DEFAULT_OPUS_MODEL)"
ask access_key     "AWS access key ID"             "$(current AWS_ACCESS_KEY_ID)"
ask secret_key     "AWS secret access key"         "$(current AWS_SECRET_ACCESS_KEY)" hidden
region_default="$(current AWS_REGION)"
ask aws_region     "AWS region"                    "${region_default:-us-east-1}"

# --- write the settings ------------------------------------------------------------------------

umask 077
mkdir -p "$settings_dir" || abort

backup=""
if [ -f "$settings_file" ]; then
  backup="$settings_file.bak.$(date +%Y%m%d%H%M%S)"
  cp "$settings_file" "$backup" || abort
  chmod 600 "$backup"
  echo "Replacing $settings_file — the previous one is now $backup"
fi

restore_and_fail() {
  echo "$1" >&2
  if [ -n "$backup" ] && [ -f "$backup" ]; then
    cp "$backup" "$settings_file" && echo "Your previous $settings_file is back in place." >&2
  fi
  exit 1
}

cat > "$settings_file" <<JSON
{
  "env": {
    "CLAUDE_CODE_USE_BEDROCK": "1",
    "AWS_REGION": "$(json_escape "$aws_region")",
    "ANTHROPIC_DEFAULT_SONNET_MODEL": "$(json_escape "$sonnet_profile")",
    "ANTHROPIC_DEFAULT_OPUS_MODEL": "$(json_escape "$opus_profile")",
    "AWS_ACCESS_KEY_ID": "$(json_escape "$access_key")",
    "AWS_SECRET_ACCESS_KEY": "$(json_escape "$secret_key")"
  },
  "model": "sonnet",
  "modelPicker": {
    "options": [
      {
        "model": "$(json_escape "$sonnet_profile")",
        "behavesAs": "claude-sonnet-5",
        "label": "Sonnet 5 (Inference profile)",
        "description": "EPM-CMBI Sonnet 5 Amazon Bedrock Application Inference Profile for Cost Tracking"
      },
      {
        "model": "$(json_escape "$opus_profile")",
        "behavesAs": "claude-opus-5",
        "label": "Opus 5 (Inference profile)",
        "description": "EPM-CMBI Opus 5 Amazon Bedrock Application Inference Profile for Cost Tracking"
      }
    ],
    "replaceBuiltInOptions": true
  },
  "effortLevel": "high"
}
JSON
write_status=$?
[ "$write_status" -eq 0 ] || restore_and_fail "Could not write $settings_file."
chmod 600 "$settings_file"

if command -v python3 >/dev/null 2>&1 && ! python3 -m json.tool "$settings_file" >/dev/null 2>&1; then
  restore_and_fail "$settings_file came out invalid — check the values you pasted, then run this again."
fi

echo "Wrote $settings_file"
echo "Next: run 'claude' from the repository root, then ask it to configure the development environment."
