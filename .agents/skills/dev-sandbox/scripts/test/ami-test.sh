#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Checks of ami, the shared host image, with AWS and Run Command replaced by the stubs in
# stubs.sh. Runs in the tools image, started by sandbox-test.sh with a throw-away state directory;
# it makes no AWS call.
set -euo pipefail
# As in-tools.sh, which the commands run under.
shopt -s inherit_errexit

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=stubs.sh
. "$TEST_DIR/stubs.sh"

# images <id:state>...: describe-images output holding the version's images, oldest first.
images() {
  local entry day=0
  local -a list=()
  for entry in "$@"; do
    day=$((day + 1))
    list+=("$(jq -cn --arg id "${entry%%:*}" --arg state "${entry#*:}" --arg day "$day" \
      '{ImageId: $id, State: $state, Name: "cp-sandbox-host-\($id)",
        CreationDate: "2026-01-0\($day)T00:00:00.000Z"}')")
  done
  jq -cn '{Images: $ARGS.positional}' --jsonargs "${list[@]}"
}

steps() {   # steps: the comments of the Run Command calls made, in order
  sed -n 's/^# //p' "$SSM_COMMANDS" | sed 's/, part .*//' | paste -sd '|'
}

test_host_version() {
  check "the version is the start of prepare-host.sh's SHA-256" "$(host_version)" \
    "$(sha256sum "$SCRIPTS_DIR/host/prepare-host.sh" | cut -c1-12)"
  check "the builder is named for the version" "$(builder_name 0123456789ab)" ami-0123456789ab
  check "a builder's name is a valid sandbox name" \
    "$([[ $(builder_name "$(host_version)") =~ $NAME_PATTERN ]] && echo yes || echo no)" yes
}

test_ami_finds_the_image() {
  reset_stubs
  IMAGES=$(images ami-old:available ami-new:available)
  run_command cmd_ami
  check "ami succeeds when the version's image exists" "$RUN_STATUS" 0
  check "ami gives the newest image of the version" "$(logged 'prepare-host.sh: ami-new')" 1
  check "ami launches nothing when the image exists" "$(called 'ec2 run-instances')" ''
  check "ami runs the preflight check first" "$(head -n 1 "$CALLS")" \
    'sts get-caller-identity --query Arn --output text'

  reset_stubs
  IMAGES=$(images ami-pending:pending)
  queue image-state $'pending\tNone' $'pending\tNone'
  run_command cmd_ami
  check "ami waits for an image that is being created" \
    "$RUN_STATUS $(called 'ec2 describe-images --image-ids ami-pending' | wc -l)" '0 3'
  check "ami builds nothing while an image is being created" "$(called 'ec2 run-instances')" ''
}

test_ami_builds_the_image() {
  local launch
  reset_stubs
  IMAGES='{"Images": []}'
  queue run-instances i-builder
  run_command cmd_ami
  check "ami builds the image when the version has none" "$RUN_STATUS" 0
  launch=$(called 'ec2 run-instances')
  check "the builder starts from the stock Amazon Linux 2023 image" \
    "$(called 'ssm get-parameter' | sed 's/ --query.*//') $(count '--image-id ami-stock' - <<<"$launch")" \
    "ssm get-parameter --name $AL2023_AMI_PARAMETER 1"
  check "the builder is tagged as the version's builder, expiring within hours" \
    "$(jq -r '.[0].Tags | from_entries | [.["cp-sandbox"], .["cp-sandbox-role"], .["cp-sandbox-owner"]] | join(" ")' \
      <<<"$(sed -E 's/.*--tag-specifications ([^ ]+) .*/\1/' <<<"$launch")")" \
    "ami-$(host_version) ami-builder $ME"
  check "the builder gets no public IP, IMDSv2 only, a 20 GB root, and no user data" \
    "$(count '"AssociatePublicIpAddress":false' - <<<"$launch") $(count 'HttpTokens=required' - <<<"$launch") $(count '"VolumeSize":20' - <<<"$launch") $(count '--user-data' - <<<"$launch")" \
    '1 1 1 0'
  check "the builder copies prepare-host.sh, prepares, reboots, checks, and seals" "$(steps)" \
    'copy prepare-host.sh to /opt/cp-sandbox|prepare the sandbox host image|read the boot ID|read the boot ID|check the sandbox host image|seal the sandbox host image'
  check "the builder reboots between preparing and checking" \
    "$(called 'ec2 reboot-instances')" 'ec2 reboot-instances --instance-ids i-builder'
  check "ami images the builder, tagging the image and its snapshot with the version" \
    "$(called 'ec2 create-image' | sed -E 's/.*--tag-specifications ([^ ]+) .*/\1/' |
      jq -c 'map([.ResourceType, (.Tags | from_entries | .["cp-sandbox-ami"])])')" \
    "[[\"image\",\"$(host_version)\"],[\"snapshot\",\"$(host_version)\"]]"
  check "ami waits for the image, then terminates the builder" \
    "$(grep -nE '^ec2 (describe-images --image-ids ami-built|terminate-instances)' "$CALLS" | cut -d: -f2- | sed 's/ --query.*//' | paste -sd '|')" \
    'ec2 describe-images --image-ids ami-built|ec2 terminate-instances --instance-ids i-builder'
  check "ami gives the new image" "$(logged 'prepare-host.sh: ami-built')" 1
}

test_ami_terminates_a_failed_builder() {
  reset_stubs
  IMAGES='{"Images": []}'
  queue run-instances i-builder
  SSM_FAILS_ON='prepare the sandbox host image'
  run_command cmd_ami
  check "ami fails when prepare-host.sh fails" "$RUN_STATUS" 1
  check "ami terminates the builder of a failed build" \
    "$(called 'ec2 terminate-instances' | sed 's/ --query.*//')" 'ec2 terminate-instances --instance-ids i-builder'
  check "ami makes no image of a failed build" "$(called 'ec2 create-image')" ''

  reset_stubs
  IMAGES='{"Images": []}'
  queue run-instances i-builder
  queue boot-id boot-old boot-old boot-old
  REBOOT_TIMEOUT_SECONDS=0
  run_command cmd_ami
  REBOOT_TIMEOUT_SECONDS=900
  check "ami fails when the builder does not come back from its reboot" \
    "$RUN_STATUS $(logged 'did not come back from its reboot')" '1 1'
  check "ami terminates a builder that did not come back" \
    "$(called 'ec2 terminate-instances' | sed 's/ --query.*//')" 'ec2 terminate-instances --instance-ids i-builder'
}

test_ami_waits_for_an_image_not_yet_visible() {
  reset_stubs
  IMAGES='{"Images": []}'
  queue run-instances i-builder
  queue image-state '!InvalidAMIID.NotFound' None $'pending\tNone'
  run_command cmd_ami
  check "ami waits for an image create-image made but describe-images does not show yet" \
    "$RUN_STATUS $(called 'ec2 describe-images --image-ids ami-built' | wc -l)" '0 4'

  reset_stubs
  IMAGES='{"Images": []}'
  queue run-instances i-builder
  queue image-state None None None
  IMAGE_UNSEEN_POLLS=2
  run_command cmd_ami
  IMAGE_UNSEEN_POLLS=6
  check "ami gives up on an image that stays unseen, and terminates the builder" \
    "$RUN_STATUS $(logged 'image ami-built cannot be found') $(called 'ec2 terminate-instances' | wc -l)" '1 1 1'

  reset_stubs
  IMAGES='{"Images": []}'
  queue run-instances i-builder
  queue image-state '!UnauthorizedOperation'
  run_command cmd_ami
  check "ami fails on any other error reading the image, and terminates the builder" \
    "$RUN_STATUS $(logged UnauthorizedOperation) $(called 'ec2 terminate-instances' | wc -l)" '1 1 1'

  reset_stubs
  IMAGES='{"Images": []}'
  queue run-instances i-builder
  queue image-state $'failed\tClient.InternalError'
  run_command cmd_ami
  check "ami fails on an image that failed" \
    "$RUN_STATUS $(logged 'image ami-built is failed')" '1 1'
}

test_ami_refuses_a_second_build() {
  reset_stubs
  IMAGES='{"Images": []}'
  DESCRIBED=$(described "$(instance i-other "ami-$(host_version)" ami-builder running 2030-01-01T00:00:00Z someone@example.com)")
  run_command cmd_ami
  check "ami fails while another build of the version runs" "$RUN_STATUS" 1
  check "ami names the running builder and how to remove a leftover" \
    "$(logged 'i-other  running  someone@example.com') $(logged "sandbox.sh down --name ami-$(host_version)")" '1 1'
  check "ami launches nothing next to a running build" "$(called 'ec2 run-instances')" ''

  reset_stubs
  IMAGES='{"Images": []}'
  DESCRIBED=$(described "$(instance i-old "ami-$(host_version)" ami-builder running 2020-01-01T00:00:00Z someone@example.com)")
  queue run-instances i-builder
  run_command cmd_ami
  check "ami builds past a builder whose expiry has passed, and leaves it alone" \
    "$RUN_STATUS $(logged 'Left alone: builder i-old of someone@example.com, running, expired 2020-01-01T00:00:00Z') $(called 'ec2 terminate-instances' | sed 's/ --query.*//')" \
    '0 1 ec2 terminate-instances --instance-ids i-builder'
}

test_ami_launches_nothing_when_the_check_fails() {
  reset_stubs
  IMAGES='{"Images": []}'
  STUB_ERROR["ec2 describe-route-tables"]=UnauthorizedOperation
  run_command cmd_ami
  check "ami fails when the preflight check fails" "$RUN_STATUS" 1
  check "ami launches nothing, and looks for no image, when the check fails" \
    "$(called 'ec2 run-instances')$(called 'ec2 describe-images')" ''
}

test_ami_rebuild() {
  reset_stubs
  IMAGES=$(images ami-old:available)
  run_command cmd_ami --rebuild
  check "ami --rebuild without --yes succeeds" "$RUN_STATUS" 0
  check "ami --rebuild lists the image it would take the tag off" \
    "$(logged 'untag     ami-old (cp-sandbox-host-ami-old, available): loses cp-sandbox-ami, stays registered')" 1
  check "ami --rebuild without --yes changes nothing" \
    "$(called 'ec2 run-instances')$(called 'ec2 create-tags')$(called 'ec2 delete-tags')$(logged 'Nothing was changed')" 1

  reset_stubs
  IMAGES=$(images ami-old:available)
  queue run-instances i-builder
  run_command cmd_ami --rebuild --yes
  check "ami --rebuild --yes builds a new image although one exists" \
    "$RUN_STATUS $(called 'ec2 create-image' | wc -l)" '0 1'
  check "ami --rebuild --yes marks the old image replaced, then takes the version's tag off it" \
    "$(grep -E '^ec2 (create|delete)-tags' "$CALLS" | paste -sd '|')" \
    "ec2 create-tags --resources ami-old --tags Key=cp-sandbox-ami-replaced,Value=$(host_version)|ec2 delete-tags --resources ami-old --tags Key=cp-sandbox-ami"
  check "ami --rebuild --yes deregisters nothing" \
    "$(called 'ec2 deregister-image')$(called 'ec2 delete-snapshot')" ''

  reset_stubs
  run_command cmd_ami --yes
  check "ami refuses --yes without --rebuild" "$RUN_STATUS" 1
  run_command cmd_ami --force
  check "ami refuses an unknown option" "$RUN_STATUS" 1
}

test_host_version
test_ami_finds_the_image
test_ami_builds_the_image
test_ami_terminates_a_failed_builder
test_ami_waits_for_an_image_not_yet_visible
test_ami_refuses_a_second_build
test_ami_launches_nothing_when_the_check_fails
test_ami_rebuild
finish
