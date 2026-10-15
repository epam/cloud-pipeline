# shellcheck shell=bash
# AWS calls, --dry-run, and the tags that tie every resource to its sandbox. Runs in the tools
# image.

TAG_SANDBOX=cp-sandbox
TAG_OWNER=cp-sandbox-owner
TAG_ROLE=cp-sandbox-role
TAG_EXPIRES=cp-sandbox-expires

# Every instance state but terminated.
LIVE_INSTANCE_STATES=pending,running,shutting-down,stopping,stopped

AL2023_AMI_PARAMETER=/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-6.1-x86_64

DRY_RUN=${DRY_RUN:-0}

dry_run() {
  [[ $DRY_RUN == 1 ]]
}

dry_run_note() {
  log "[dry-run] $*"
}

# aws_call <dry-run output> <aws args...>: makes the call in the sandbox's region. Under --dry-run
# it prints the call instead, and writes <dry-run output> where the call's output would go, so the
# steps that read it still run.
aws_call() {
  local output=$1
  shift
  if dry_run; then
    dry_run_note "$(print_command aws --region "$SANDBOX_REGION" "$@")"
    [[ -z $output ]] || printf '%s\n' "$output"
    return 0
  fi
  aws --region "$SANDBOX_REGION" "$@"
}

utc_now() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

# utc_after_hours <hours> [<from>]: the UTC time that many hours after <from>, or after now.
utc_after_hours() {
  local from
  from=$(date -u -d "${2:-now}" +%s)
  date -u -d "@$((from + $1 * 3600))" +%Y-%m-%dT%H:%M:%SZ
}

# sandbox_tags <name> <role> <expires>: the tags of a resource created for a sandbox, as JSON.
sandbox_tags() {
  jq -cn --arg name "$1" --arg role "$2" --arg expires "$3" --arg owner "$CP_SANDBOX_OWNER" \
    --arg tag_sandbox "$TAG_SANDBOX" --arg tag_owner "$TAG_OWNER" \
    --arg tag_role "$TAG_ROLE" --arg tag_expires "$TAG_EXPIRES" '[
      {Key: "Name", Value: "cp-sandbox-\($name)-\($role)"},
      {Key: $tag_sandbox, Value: $name},
      {Key: $tag_owner, Value: $owner},
      {Key: $tag_role, Value: $role},
      {Key: $tag_expires, Value: $expires}
    ]'
}

# instance_profile_spec <name-or-arn>: the --iam-instance-profile argument for it.
instance_profile_spec() {
  case $1 in
    arn:*) jq -cn --arg value "$1" '{Arn: $value}' ;;
    *) jq -cn --arg value "$1" '{Name: $value}' ;;
  esac
}

# stock_image: the latest Amazon Linux 2023 image of the region.
stock_image() {
  aws_call ami-0dryrun00000al2023 ssm get-parameter --name "$AL2023_AMI_PARAMETER" \
    --query Parameter.Value --output text
}

# launch_instance <name> <role> <ami> <expires> <instance-type> <root-volume-gb> [<user-data>]:
# launches one instance of the sandbox, tagged with it, in the configured subnet and security
# groups, with no public IP and IMDSv2 only, and writes its ID.
launch_instance() {
  local name=$1 role=$2 ami=$3 expires=$4 type=$5 root_gb=$6 user_data=${7-} tags root_device
  local -a options=()
  tags=$(sandbox_tags "$name" "$role" "$expires")
  root_device=$(aws_call /dev/xvda ec2 describe-images --image-ids "$ami" \
    --query 'Images[0].RootDeviceName' --output text)
  if [[ -n $user_data ]]; then
    options+=(--user-data "$user_data")
  fi

  aws_call i-0dryrun000000000 ec2 run-instances \
    --image-id "$ami" \
    --instance-type "$type" \
    --iam-instance-profile "$(instance_profile_spec "$CP_SANDBOX_INSTANCE_PROFILE")" \
    --network-interfaces "$(jq -cn --arg subnet "$CP_SANDBOX_SUBNET_ID" \
      --arg groups "$CP_SANDBOX_SECURITY_GROUP_IDS" '[{
        DeviceIndex: 0, SubnetId: $subnet, AssociatePublicIpAddress: false,
        DeleteOnTermination: true,
        Groups: ($groups | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(length > 0)))
      }]')" \
    --block-device-mappings "$(jq -cn --arg device "$root_device" --argjson size "$root_gb" '[{
        DeviceName: $device,
        Ebs: {VolumeSize: $size, VolumeType: "gp3", DeleteOnTermination: true}
      }]')" \
    --metadata-options HttpTokens=required,HttpEndpoint=enabled \
    --instance-initiated-shutdown-behavior terminate \
    --tag-specifications "$(jq -cn --argjson tags "$tags" \
      '["instance", "volume", "network-interface"] | map({ResourceType: ., Tags: $tags})')" \
    "${options[@]}" \
    --query 'Instances[0].InstanceId' --output text
}

# instances_named <name>: reads describe-instances output, and writes a JSON array of the instances
# tagged cp-sandbox=<name>. The describe calls already filter on that tag; this repeats the check,
# so that nothing without it is ever changed or terminated, whatever the filter returned.
instances_named() {
  local input
  input=$(cat)
  [[ -n $input ]] || input='{}'
  jq -c --arg name "$1" --arg tag_sandbox "$TAG_SANDBOX" --arg tag_owner "$TAG_OWNER" \
    --arg tag_role "$TAG_ROLE" --arg tag_expires "$TAG_EXPIRES" '
    [ (.Reservations // [])[].Instances[]
      | ((.Tags // []) | from_entries) as $tags
      | select($tags[$tag_sandbox] == $name)
      | { id: .InstanceId,
          state: .State.Name,
          ip: (.PrivateIpAddress // ""),
          ami: (.ImageId // ""),
          owner: ($tags[$tag_owner] // ""),
          role: ($tags[$tag_role] // ""),
          expires: ($tags[$tag_expires] // "") } ]' <<<"$input"
}

# mine, not_mine: of what instances_named writes, the instances tagged with this config's owner,
# and the rest. A name is free text, so another developer may have used it too.
mine() {
  jq -c --arg owner "$CP_SANDBOX_OWNER" '[.[] | select(.owner == $owner)]'
}

not_mine() {
  jq -c --arg owner "$CP_SANDBOX_OWNER" '[.[] | select(.owner != $owner)]'
}

# dry_run_instances <name> <role> [<instance-id>]: describe-instances output with one running
# instance of the sandbox, for --dry-run to carry on with.
dry_run_instances() {
  jq -cn --arg name "$1" --arg role "$2" --arg id "${3:-i-0dryrun000000000}" \
    --arg expires "$(utc_after_hours 1)" \
    --arg owner "$CP_SANDBOX_OWNER" --arg tag_sandbox "$TAG_SANDBOX" --arg tag_owner "$TAG_OWNER" \
    --arg tag_role "$TAG_ROLE" --arg tag_expires "$TAG_EXPIRES" '
    {Reservations: [{Instances: [{
      InstanceId: $id, State: {Name: "running"},
      PrivateIpAddress: "10.0.0.10", ImageId: "ami-0dryrun000000000",
      Tags: [{Key: $tag_sandbox, Value: $name}, {Key: $tag_owner, Value: $owner},
             {Key: $tag_role, Value: $role}, {Key: $tag_expires, Value: $expires}]
    }]}]}'
}

# sandbox_instances <name> [<dry-run output>]: the instances named for the sandbox that are not
# terminated, whoever owns them, as instances_named writes them.
sandbox_instances() {
  aws_call "${2-$(dry_run_instances "$1" master)}" ec2 describe-instances \
    --filters "Name=tag:$TAG_SANDBOX,Values=$1" "Name=instance-state-name,Values=$LIVE_INSTANCE_STATES" \
    --output json |
    instances_named "$1"
}

# print_not_mine <instances>: names the instances that a command leaves alone because another
# owner's tag is on them.
print_not_mine() {
  not_mine <<<"$1" | jq -r '.[] | "  leave     \(.id), tagged \(.owner | if . == "" then "with no owner" else "cp-sandbox-owner=\(.)" end)"'
}
