# shellcheck shell=bash
# Local state: a directory per sandbox under SANDBOX_STATE_ROOT, holding state.json, the sandbox's
# SSH key and the master's credentials. Runs in the tools image.
#
# state.json holds no secret. Every file that does ends in .pem or .secret, which puts it under the
# deny-read rules for secrets.

state_dir() {
  printf '%s/%s\n' "$SANDBOX_STATE_ROOT" "$1"
}

state_file() {
  printf '%s/state.json\n' "$(state_dir "$1")"
}

ssh_key_file() {
  printf '%s/ssh-key.pem\n' "$(state_dir "$1")"
}

known_hosts_file() {
  printf '%s/known_hosts\n' "$(state_dir "$1")"
}

# The master's /etc/kubernetes/admin.conf: cluster-admin on the sandbox's Kubernetes.
kubeconfig_file() {
  printf '%s/%s\n' "$(state_dir "$1")" "$KUBECONFIG_FILE_NAME"
}

# The token the platform's nodes join the cluster with.
join_token_file() {
  printf '%s/kube-join-token.secret\n' "$(state_dir "$1")"
}

# local_sandboxes: the names of the sandboxes with local state, one per line.
local_sandboxes() {
  local file
  [[ -d $SANDBOX_STATE_ROOT ]] || return 0
  for file in "$SANDBOX_STATE_ROOT"/*/state.json; do
    if [[ -f $file ]]; then
      basename "$(dirname "$file")"
    fi
  done
}

# state_get <name> <key>: one value from the sandbox's state.json, empty when there is none.
state_get() {
  local file
  file=$(state_file "$1")
  [[ -f $file ]] || return 0
  jq -r --arg key "$2" '.[$key] // empty' "$file"
}

# state_set <name> <key> <value> [<key> <value>...]: records values in the sandbox's state.json.
state_set() {
  local name=$1 file current filter='.' args=()
  shift
  file=$(state_file "$name")
  if dry_run; then
    dry_run_note "record in $file: $*"
    return 0
  fi
  while (($#)); do
    args+=(--arg "$1" "$2")
    filter+=" | .[\"$1\"] = \$$1"
    shift 2
  done
  current='{}'
  [[ ! -f $file ]] || current=$(<"$file")
  mkdir -p "$(dirname "$file")"
  jq "${args[@]}" "$filter" <<<"$current" >"$file.tmp"
  mv "$file.tmp" "$file"
}

# state_unset <name> <key>...: removes keys from the sandbox's state.json, when it has one.
state_unset() {
  local name=$1 file
  shift
  file=$(state_file "$name")
  [[ -f $file ]] || return 0
  if dry_run; then
    dry_run_note "remove from $file: $*"
    return 0
  fi
  jq --args 'delpaths($ARGS.positional | map([.]))' "$@" <"$file" >"$file.tmp"
  mv "$file.tmp" "$file"
}

state_remove() {
  local dir
  validate_name "$1"
  dir=$(state_dir "$1")
  [[ -d $dir ]] || return 0
  if dry_run; then
    dry_run_note "remove $dir"
    return 0
  fi
  rm -rf -- "$dir"
}

# local_docker_objects [<name>]: the containers and networks on the developer's Docker labelled for
# the sandbox, or for any sandbox, one per line: <sandbox> <container|network> <name> <state>.
# sandbox.sh lists them before it starts the tools container, in SANDBOX_DOCKER_OBJECTS: this
# container cannot reach Docker.
local_docker_objects() {
  local sandbox kind object state
  while read -r sandbox kind object state; do
    [[ -n $sandbox && -n $object ]] || continue
    [[ -z ${1-} || $sandbox == "$1" ]] || continue
    printf '%s %s %s %s\n' "$sandbox" "$kind" "$object" "${state:--}"
  done <<<"${SANDBOX_DOCKER_OBJECTS-}"
}

# use_sandbox_region <name>: a sandbox stays in the region it was created in, whatever the config
# says now.
use_sandbox_region() {
  local region
  region=$(state_get "$1" region)
  if [[ -n $region && $region != "$SANDBOX_REGION" ]]; then
    log "Sandbox $1 is in $region, not in the configured $SANDBOX_REGION; using $region."
    SANDBOX_REGION=$region
  fi
}

# select_sandbox <name-or-empty>: the sandbox a command acts on — the one named, or else the only
# one with local state.
select_sandbox() {
  local names=()
  if [[ -n $1 ]]; then
    validate_name "$1"
    printf '%s\n' "$1"
    return 0
  fi
  mapfile -t names < <(local_sandboxes)
  case ${#names[@]} in
    0) die "no local sandbox in $SANDBOX_STATE_ROOT; pass --name <name>" ;;
    1) printf '%s\n' "${names[0]}" ;;
    *) die "several local sandboxes (${names[*]}); pass --name <name>" ;;
  esac
}
