# shellcheck shell=bash
# status: the local sandboxes, their containers and networks on the developer's Docker, and every
# instance tagged with this owner in the configured region. Runs in the tools image, which cannot
# reach Docker: sandbox.sh lists the Docker objects for it.

cmd_status() {
  local now name id ip expires instances role state kind object
  (($# == 0)) || die "status: unknown option $1"
  now=$(utc_now)

  printf 'Local sandboxes in %s:\n' "$SANDBOX_STATE_ROOT"
  local_row NAME INSTANCE REGION 'PRIVATE IP' EXPIRES ''
  while IFS= read -r name; do
    [[ -n $name ]] || continue
    expires=$(state_get "$name" expires)
    local_row "$name" "$(state_get "$name" instance_id)" "$(state_get "$name" region)" \
      "$(state_get "$name" private_ip)" "$expires" "$(expired_mark "$expires" "$now")"
  done < <(local_sandboxes)

  printf '\nOn Docker here, labelled %s:\n' "$TAG_SANDBOX"
  docker_row SANDBOX KIND NAME STATE
  while read -r name kind object state; do
    docker_row "$name" "$kind" "$object" "$state"
  done < <(local_docker_objects)

  printf '\nInstances tagged %s=%s in %s, not terminated:\n' \
    "$TAG_OWNER" "$CP_SANDBOX_OWNER" "$SANDBOX_REGION"
  instances=$(aws_call "$(dry_run_instances dry-run master)" ec2 describe-instances \
    --filters "Name=tag:$TAG_OWNER,Values=$CP_SANDBOX_OWNER" \
    "Name=instance-state-name,Values=$LIVE_INSTANCE_STATES" \
    --output json)
  instance_row SANDBOX INSTANCE ROLE STATE 'PRIVATE IP' EXPIRES ''
  while IFS=$'\t' read -r name id role state ip expires; do
    instance_row "$name" "$id" "$role" "$state" "$ip" "$expires" "$(expired_mark "$expires" "$now")"
  done < <(jq -r --arg tag_sandbox "$TAG_SANDBOX" --arg tag_role "$TAG_ROLE" \
    --arg tag_expires "$TAG_EXPIRES" '
    (.Reservations // [])[].Instances[]
    | ((.Tags // []) | from_entries) as $tags
    | [($tags[$tag_sandbox] // "-"), .InstanceId, ($tags[$tag_role] // "-"), .State.Name,
       (.PrivateIpAddress // "-"), ($tags[$tag_expires] // "-")]
    | @tsv' <<<"$instances")
}

local_row() {
  printf '  %-28s %-21s %-15s %-15s %-21s %s\n' "$1" "${2:--}" "${3:--}" "${4:--}" "${5:--}" "$6"
}

instance_row() {
  printf '  %-28s %-21s %-8s %-14s %-15s %-21s %s\n' "$@"
}

docker_row() {
  printf '  %-28s %-10s %-45s %s\n' "$@"
}

# expired_mark <expires> <now>: "expired" once the expiry has passed. Both are UTC ISO-8601 in
# the same format, so they compare as strings.
expired_mark() {
  if [[ $1 == ????-??-??T??:??:??Z && $1 < $2 ]]; then
    printf 'expired'
  fi
}
