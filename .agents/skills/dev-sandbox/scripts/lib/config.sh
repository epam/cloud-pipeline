# shellcheck shell=bash
# The developer config, as Docker passed it in from the env file. Runs in the tools image.
# Messages name keys, never values.

CONFIG_REQUIRED_KEYS=(
  AWS_REGION
  CP_SANDBOX_VPC_ID
  CP_SANDBOX_SUBNET_ID
  CP_SANDBOX_SECURITY_GROUP_IDS
  CP_SANDBOX_INSTANCE_PROFILE
  CP_SANDBOX_OWNER
)
DEFAULT_INSTANCE_TYPE=t3.medium
DEFAULT_TTL_HOURS=48

# config_load: checks the config and fills in the defaults. Sets SANDBOX_REGION. The image's
# entrypoint has already unset the AWS variables the file left empty.
config_load() {
  local key missing=() misread=()

  if [[ -z ${AWS_PROFILE:-} && (-z ${AWS_ACCESS_KEY_ID:-} || -z ${AWS_SECRET_ACCESS_KEY:-}) ]]; then
    missing+=("AWS_PROFILE (or AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)")
  fi
  for key in "${CONFIG_REQUIRED_KEYS[@]}"; do
    [[ -n ${!key:-} ]] || missing+=("$key")
  done
  if ((${#missing[@]})); then
    die "missing in $SANDBOX_ENV_FILE: $(printf '%s, ' "${missing[@]}" | sed 's/, $//')"
  fi

  # The image's entrypoint has removed one pair of matching quotes around a value. Docker keeps
  # the rest as part of the value: a quote that is not closed, and a comment after a value.
  for key in $(compgen -v); do
    case $key in
      AWS_* | CP_SANDBOX_*)
        case ${!key} in
          \"* | \'* | *[[:space:]]\#*) misread+=("$key") ;;
        esac
        ;;
    esac
  done
  if ((${#misread[@]})); then
    die "values in $SANDBOX_ENV_FILE that Docker does not read as meant: ${misread[*]}. \
Write KEY=value or KEY=\"value\", with no comment after it: a comment goes on a line of its own."
  fi

  CP_SANDBOX_WORKER_INSTANCE_PROFILE=${CP_SANDBOX_WORKER_INSTANCE_PROFILE:-$CP_SANDBOX_INSTANCE_PROFILE}
  CP_SANDBOX_MASTER_INSTANCE_TYPE=${CP_SANDBOX_MASTER_INSTANCE_TYPE:-$DEFAULT_INSTANCE_TYPE}
  CP_SANDBOX_WORKER_INSTANCE_TYPE=${CP_SANDBOX_WORKER_INSTANCE_TYPE:-$DEFAULT_INSTANCE_TYPE}
  CP_SANDBOX_TTL_HOURS=${CP_SANDBOX_TTL_HOURS:-$DEFAULT_TTL_HOURS}
  is_positive_int "$CP_SANDBOX_TTL_HOURS" ||
    die "CP_SANDBOX_TTL_HOURS in $SANDBOX_ENV_FILE must be a whole number of hours, above 0"

  # shellcheck disable=SC2034 # read by aws.sh and the commands
  SANDBOX_REGION=$AWS_REGION
}
