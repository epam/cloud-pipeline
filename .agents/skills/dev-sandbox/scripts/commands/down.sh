# shellcheck shell=bash
# down: lists the sandbox's instances, its EC2 key pair, its local state and its containers,
# network and volumes on the developer's Docker; with --yes, terminates the instances, waits for
# them to go, deletes the key pair and removes the local state. Runs in the tools image, which
# cannot reach Docker: sandbox.sh lists the Docker objects for it, and removes them once this has
# succeeded.

# sandbox_key_pairs <name>: the EC2 key pairs tagged for the sandbox, whoever owns them, as JSON:
# [{id, name, owner}].
sandbox_key_pairs() {
  aws_call '[]' ec2 describe-key-pairs --filters "Name=tag:$TAG_SANDBOX,Values=$1" \
    --query 'KeyPairs[].{id: KeyPairId, name: KeyName, tags: Tags}' --output json |
    jq -c --arg name "$1" --arg tag_sandbox "$TAG_SANDBOX" --arg tag_owner "$TAG_OWNER" '
      [ (. // [])[] | ((.tags // []) | from_entries) as $tags
        | select($tags[$tag_sandbox] == $name)
        | {id, name, owner: ($tags[$tag_owner] // "")} ]'
}

cmd_down() {
  local name='' yes=0 named instances dir docker_objects kind object state key_pairs id
  local -a ids key_ids
  while (($#)); do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --yes) yes=1; shift ;;
      *) die "down: unknown option $1" ;;
    esac
  done
  name=$(select_sandbox "$name")
  use_sandbox_region "$name"
  dir=$(state_dir "$name")

  named=$(sandbox_instances "$name")
  instances=$(mine <<<"$named")
  mapfile -t ids < <(jq -r '.[].id' <<<"$instances")

  printf 'Sandbox %s, in %s, owner %s:\n' "$name" "$SANDBOX_REGION" "$CP_SANDBOX_OWNER"
  jq -r '.[] | [.id, .role, .state, .ip] | map(if . == "" then "-" else . end)
    | "  terminate " + join("  ")' <<<"$instances"
  print_not_mine "$named"
  key_pairs=$(sandbox_key_pairs "$name")
  mapfile -t key_ids < <(mine <<<"$key_pairs" | jq -r '.[].id')
  mine <<<"$key_pairs" | jq -r '.[] | "  delete    key pair \(.name) (\(.id))"'
  print_not_mine "$key_pairs"
  if [[ -d $dir ]]; then
    printf '  remove    %s\n' "$dir"
  fi
  docker_objects=$(local_docker_objects "$name")
  while read -r _ kind object state; do
    [[ -n $object ]] || continue
    if [[ $state == - ]]; then state=''; else state=" ($state)"; fi
    printf "  remove    %s %s%s, on this workstation's Docker\n" "$kind" "$object" "$state"
  done <<<"$docker_objects"
  if ((${#ids[@]} == 0 && ${#key_ids[@]} == 0)) && [[ ! -d $dir && -z $docker_objects ]]; then
    printf '  nothing: no instance or key pair tagged %s=%s with this owner, %s\n' "$TAG_SANDBOX" \
      "$name" 'no local state, and nothing on Docker'
    return 0
  fi
  if ((yes == 0)); then
    printf 'Nothing was removed. Run down again with --yes to remove the above.\n'
    return 0
  fi

  if ((${#ids[@]})); then
    aws_call '' ec2 terminate-instances --instance-ids "${ids[@]}" \
      --query 'TerminatingInstances[].[InstanceId, CurrentState.Name]' --output text >&2
    log "Waiting for ${#ids[@]} instance(s) to terminate."
    aws_call '' ec2 wait instance-terminated --instance-ids "${ids[@]}"
  fi
  for id in ${key_ids[@]+"${key_ids[@]}"}; do
    aws_call '' ec2 delete-key-pair --key-pair-id "$id" >/dev/null
  done
  state_remove "$name"
  if dry_run; then
    printf '[dry-run] Nothing was removed.\n'
  else
    printf 'Sandbox %s is removed.\n' "$name"
  fi
}
