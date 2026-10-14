# shellcheck shell=bash
# ami: the shared host image that sandbox masters and the platform's workers start from: Amazon
# Linux 2023 as scripts/host/prepare-host.sh leaves it, rebooted into cgroup v1. There is one per
# version of prepare-host.sh, shared by everyone in the account, and nothing here deletes one:
# --rebuild builds another and takes the version's tag off the old one. Runs in the tools image.

TAG_AMI=cp-sandbox-ami
TAG_AMI_REPLACED=cp-sandbox-ami-replaced
TAG_AMI_BUILT_BY=cp-sandbox-ami-built-by
BUILDER_ROLE=ami-builder
# The builder's expiry tag. It is terminated as soon as the image exists, or the build fails.
BUILDER_TTL_HOURS=3
HOST_IMAGE_ROOT_VOLUME_GB=20
PREPARE_TIMEOUT_SECONDS=1800
REBOOT_TIMEOUT_SECONDS=900
REBOOT_POLL_SECONDS=15
IMAGE_TIMEOUT_SECONDS=3600
IMAGE_POLL_SECONDS=20
# How many polls an image just created may go unseen.
IMAGE_UNSEEN_POLLS=6
DRY_RUN_NEW_IMAGE=ami-0dryrun000000new

# The builder being run, terminated on the way out whatever happens; and the image found or built.
BUILDER_ID=''
BUILDER_NAME=''
HOST_IMAGE=''

cmd_ami() {
  local rebuild=0 yes=0 version images
  while (($#)); do
    case $1 in
      --rebuild) rebuild=1; shift ;;
      --yes) yes=1; shift ;;
      *) die "ami: unknown option $1" ;;
    esac
  done
  ((rebuild == 1 || yes == 0)) || die "ami: --yes confirms --rebuild, and goes only with it"
  run_check >&2 || die "the preflight check failed, so ami launched nothing. Fix the items marked \
FAIL above; sandbox.sh check runs the check alone."
  version=$(host_version)

  if ((rebuild == 0)); then
    ensure_host_image "$version"
    printf 'Host image, version %s of prepare-host.sh: %s\n' "$version" "$HOST_IMAGE"
    return 0
  fi

  images=$(host_images "$version")
  printf 'Rebuild of the host image, version %s of prepare-host.sh, in %s:\n' \
    "$version" "$SANDBOX_REGION"
  printf '  build     a new image, from %s\n' "$AL2023_AMI_PARAMETER"
  jq -r --arg tag "$TAG_AMI" '.[]
    | "  untag     \(.id) (\(.name), \(.state)): loses \($tag),"
      + " stays registered for whatever still uses it"' <<<"$images"
  if ((yes == 0)); then
    printf 'Nothing was changed. Run ami --rebuild --yes to do the above.\n'
    return 0
  fi
  build_host_image "$version"
  replace_host_images "$version" "$images"
  printf 'Host image, version %s of prepare-host.sh: %s\n' "$version" "$HOST_IMAGE"
}

# host_version: the version of prepare-host.sh, which names the image it makes.
host_version() {
  sha256sum "$SCRIPTS_DIR/host/prepare-host.sh" | cut -c1-12
}

builder_name() {
  printf 'ami-%s\n' "$1"
}

# host_images <version>: the account's images of that version, pending or available, oldest
# first, as a JSON array of {id, state, name, created}.
host_images() {
  aws_call "$(dry_run_host_images "$1")" ec2 describe-images --owners self \
    --filters "Name=tag:$TAG_AMI,Values=$1" Name=state,Values=pending,available --output json |
    jq -c '[(.Images // [])[] | {id: .ImageId, state: .State, name: (.Name // ""),
      created: (.CreationDate // "")}] | sort_by(.created)'
}

# ensure_host_image <version>: sets HOST_IMAGE to the version's image, waiting for it while it is
# being created, and building it when there is none.
ensure_host_image() {
  local images
  images=$(host_images "$1")
  HOST_IMAGE=$(jq -r 'map(select(.state == "available")) | last | .id // empty' <<<"$images")
  [[ -z $HOST_IMAGE ]] || return 0
  HOST_IMAGE=$(jq -r 'map(select(.state == "pending")) | last | .id // empty' <<<"$images")
  if [[ -n $HOST_IMAGE ]]; then
    log "Host image $HOST_IMAGE, version $1, is being created; waiting for it."
    wait_image_available "$HOST_IMAGE"
    return 0
  fi
  log "There is no host image for version $1 of prepare-host.sh yet. Building it, which takes \
about 20 minutes, once for everyone in the account."
  build_host_image "$1"
}

# build_host_image <version>: builds the version's image and sets HOST_IMAGE to it.
build_host_image() {
  local version=$1 stock image
  BUILDER_NAME=$(builder_name "$version")
  refuse_running_builder "$version"
  stock=$(stock_image)
  BUILDER_ID=$(launch_instance "$BUILDER_NAME" "$BUILDER_ROLE" "$stock" \
    "$(utc_after_hours "$BUILDER_TTL_HOURS")" "$CP_SANDBOX_MASTER_INSTANCE_TYPE" \
    "$HOST_IMAGE_ROOT_VOLUME_GB")
  trap terminate_builder EXIT
  log "Launched builder $BUILDER_ID from $stock, tagged $TAG_SANDBOX=$BUILDER_NAME."

  aws_call '' ec2 wait instance-running --instance-ids "$BUILDER_ID"
  wait_ssm_online "$BUILDER_ID"
  ssm_send_files "$BUILDER_ID" "$SCRIPTS_DIR/host/prepare-host.sh"
  log "Preparing the host, up to $((PREPARE_TIMEOUT_SECONDS / 60)) minutes."
  ssm_run_script "$BUILDER_ID" "$PREPARE_TIMEOUT_SECONDS" "prepare the sandbox host image" \
    prepare-host.sh prepare >&2
  reboot_instance "$BUILDER_ID"
  ssm_run_script "$BUILDER_ID" 300 "check the sandbox host image" prepare-host.sh check >&2
  ssm_run_script "$BUILDER_ID" 300 "seal the sandbox host image" prepare-host.sh seal >&2

  image=$(aws_call "$DRY_RUN_NEW_IMAGE" ec2 create-image --instance-id "$BUILDER_ID" \
    --name "cp-sandbox-host-$version-$(date -u +%Y%m%d%H%M%S)" \
    --description "Dev sandbox host: Amazon Linux 2023 for Kubernetes 1.15, prepare-host.sh $version" \
    --tag-specifications "$(host_image_tags "$version")" \
    --query ImageId --output text)
  log "Creating host image $image from $BUILDER_ID."
  wait_image_available "$image"
  terminate_builder
  trap - EXIT
  HOST_IMAGE=$image
}

# refuse_running_builder <version>: fails when an instance that has not expired carries the
# version's builder name, whoever owns it: another build is under way. A builder past its expiry
# is one left over from a build that stopped, and no reason to wait.
refuse_running_builder() {
  local named active now
  now=$(utc_now)
  named=$(sandbox_instances "$BUILDER_NAME" '{"Reservations": []}')
  jq -r --arg now "$now" '.[] | select(.expires | test("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ$"))
    | select(.expires < $now)
    | "Left alone: builder \(.id) of \(.owner), \(.state), expired \(.expires)."' <<<"$named" >&2
  active=$(jq -c --arg now "$now" '[.[] | select((.expires
    | test("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ$") and . < $now) | not)]' <<<"$named")
  [[ $(jq length <<<"$active") != 0 ]] || return 0
  jq -r '.[] | "  \(.id)  \(.state)  \(.owner)  expires \(.expires)"' <<<"$active" >&2
  die "a build of host image version $1 is under way already: wait for it to end, then run \
this again. A builder of yours left over from a build that stopped goes with: \
sandbox.sh down --name $BUILDER_NAME"
}

terminate_builder() {
  [[ -n $BUILDER_ID ]] || return 0
  log "Terminating builder $BUILDER_ID."
  aws_call '' ec2 terminate-instances --instance-ids "$BUILDER_ID" \
    --query 'TerminatingInstances[].[InstanceId, CurrentState.Name]' --output text >&2 ||
    log "Could not terminate builder $BUILDER_ID. It expires within $BUILDER_TTL_HOURS hours; \
sandbox.sh down --name $BUILDER_NAME removes it."
  BUILDER_ID=''
}

boot_id() {
  ssm_run "$1" 60 "read the boot ID" 'cat /proc/sys/kernel/random/boot_id'
}

# reboot_instance <instance-id>: reboots it, and waits until it runs again under a new boot ID.
reboot_instance() {
  local id=$1 before after deadline=$((SECONDS + REBOOT_TIMEOUT_SECONDS))
  before=$(boot_id "$id")
  aws_call '' ec2 reboot-instances --instance-ids "$id"
  if dry_run; then
    return 0
  fi
  log "Rebooting $id."
  while :; do
    sleep "$REBOOT_POLL_SECONDS"
    # A command sent as the instance goes down may fail; one sent later waits for it to come back.
    after=$(boot_id "$id" 2>/dev/null) || after=''
    if [[ -n $after && $after != "$before" ]]; then
      return 0
    fi
    ((SECONDS < deadline)) ||
      die "$id did not come back from its reboot within $((REBOOT_TIMEOUT_SECONDS / 60)) minutes"
  done
}

wait_image_available() {
  local image=$1 state error_file misses=0 deadline=$((SECONDS + IMAGE_TIMEOUT_SECONDS))
  local -a describe=(ec2 describe-images --image-ids "$image"
    --query 'Images[0].[State, StateReason.Message]' --output text)
  if dry_run; then
    aws_call available "${describe[@]}" >/dev/null
    return 0
  fi
  log "Waiting for image $image to be available, up to $((IMAGE_TIMEOUT_SECONDS / 60)) minutes."
  error_file=$(mktemp)
  while :; do
    if state=$(aws_call '' "${describe[@]}" 2>"$error_file"); then
      :
    elif grep -q InvalidAMIID.NotFound "$error_file"; then
      state=None
    else
      cat "$error_file" >&2
      rm -f "$error_file"
      die "could not read image $image"
    fi
    # Right after create-image, the image may not be visible yet.
    if [[ $state == None ]]; then
      if ((misses >= IMAGE_UNSEEN_POLLS)); then
        rm -f "$error_file"
        die "image $image cannot be found"
      fi
      misses=$((misses + 1))
      state=pending
    fi
    case ${state%%[[:space:]]*} in
      available) rm -f "$error_file"; return 0 ;;
      pending) ;;
      *) rm -f "$error_file"; die "image $image is $state" ;;
    esac
    if ((SECONDS >= deadline)); then
      rm -f "$error_file"
      die "image $image is still pending after $((IMAGE_TIMEOUT_SECONDS / 60)) minutes"
    fi
    sleep "$IMAGE_POLL_SECONDS"
  done
}

host_image_tags() {
  jq -cn --arg version "$1" --arg tag "$TAG_AMI" --arg tag_built_by "$TAG_AMI_BUILT_BY" \
    --arg owner "$CP_SANDBOX_OWNER" '
    [{Key: "Name", Value: "cp-sandbox-host-\($version)"}, {Key: $tag, Value: $version},
     {Key: $tag_built_by, Value: $owner}] as $tags
    | ["image", "snapshot"] | map({ResourceType: ., Tags: $tags})'
}

# replace_host_images <version> <images>: takes the version's tag off the images listed, all but
# HOST_IMAGE, and marks them replaced. They stay registered.
replace_host_images() {
  local -a ids
  mapfile -t ids < <(jq -r --arg new "$HOST_IMAGE" '.[] | select(.id != $new) | .id' <<<"$2")
  ((${#ids[@]})) || return 0
  aws_call '' ec2 create-tags --resources "${ids[@]}" --tags "Key=$TAG_AMI_REPLACED,Value=$1"
  aws_call '' ec2 delete-tags --resources "${ids[@]}" --tags "Key=$TAG_AMI"
  log "Untagged ${ids[*]}, which stay registered: deregister them by hand once nothing uses them."
}

dry_run_host_images() {
  jq -cn --arg version "$1" '{Images: [{ImageId: "ami-0dryrun000000000", State: "available",
    Name: "cp-sandbox-host-\($version)-dry-run", CreationDate: "2026-01-01T00:00:00.000Z"}]}'
}
