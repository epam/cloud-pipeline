# shellcheck shell=bash
# extend: moves the expiry of every instance of the sandbox n hours later. Runs in the tools image.

cmd_extend() {
  local name='' hours='' named instances current now new
  local -a ids
  while (($#)); do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --hours) hours=$(option_value "$1" "${2-}"); shift 2 ;;
      *) die "extend: unknown option $1" ;;
    esac
  done
  is_positive_int "$hours" || die "extend: --hours <n> is required, a whole number above 0"
  name=$(select_sandbox "$name")
  use_sandbox_region "$name"

  named=$(sandbox_instances "$name")
  instances=$(mine <<<"$named")
  mapfile -t ids < <(jq -r '.[].id' <<<"$instances")
  print_not_mine "$named"
  ((${#ids[@]})) ||
    die "no instance tagged $TAG_SANDBOX=$name and $TAG_OWNER=$CP_SANDBOX_OWNER in $SANDBOX_REGION"

  # The latest expiry among the instances, or now if that has passed: extending a sandbox that is
  # about to be reaped gives it n hours from now, not n hours from the past.
  now=$(utc_now)
  current=$(jq -r '[.[].expires | select(test("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ$"))]
    | max // empty' <<<"$instances")
  if [[ -z $current || $current < $now ]]; then
    new=$(utc_after_hours "$hours")
  else
    new=$(utc_after_hours "$hours" "$current")
  fi

  aws_call '' ec2 create-tags --resources "${ids[@]}" --tags "Key=$TAG_EXPIRES,Value=$new"
  if [[ -f $(state_file "$name") ]]; then
    state_set "$name" expires "$new"
  fi
  printf 'Sandbox %s expires %s (was %s): %s\n' "$name" "$new" "${current:-unset}" "${ids[*]}"
}
