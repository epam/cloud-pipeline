# shellcheck shell=bash
# shellcheck source-path=SCRIPTDIR
# shellcheck disable=SC2034 # the variables set here are read by the tests that source this file
# What the tests that run in the tools image share: the libraries and the commands, sourced as
# in-tools.sh sources them, with AWS, Run Command, SSH and the Kubernetes API replaced by stubs
# that record every call and answer from canned output. Sourced by the *-test.sh files here, with
# TEST_DIR set, under a throw-away SANDBOX_STATE_ROOT that sandbox-test.sh passes in. Makes no AWS
# call.

SCRIPTS_DIR=$(dirname "$TEST_DIR")
# shellcheck source=../lib/common.sh
. "$SCRIPTS_DIR/lib/common.sh"
# shellcheck source=../lib/aws.sh
. "$SCRIPTS_DIR/lib/aws.sh"
# shellcheck source=../lib/state.sh
. "$SCRIPTS_DIR/lib/state.sh"
# shellcheck source=../lib/ssm.sh
. "$SCRIPTS_DIR/lib/ssm.sh"
# shellcheck source=../commands/check.sh
. "$SCRIPTS_DIR/commands/check.sh"
# shellcheck source=../commands/ami.sh
. "$SCRIPTS_DIR/commands/ami.sh"
# shellcheck source=../commands/up.sh
. "$SCRIPTS_DIR/commands/up.sh"
# shellcheck source=../commands/extend.sh
. "$SCRIPTS_DIR/commands/extend.sh"
# shellcheck source=../commands/down.sh
. "$SCRIPTS_DIR/commands/down.sh"
# shellcheck source=../commands/status.sh
. "$SCRIPTS_DIR/commands/status.sh"
# shellcheck source=../commands/connect.sh
. "$SCRIPTS_DIR/commands/connect.sh"
# shellcheck source=../commands/services.sh
. "$SCRIPTS_DIR/commands/services.sh"

FAILURES=0
STUB_DIR=$SANDBOX_STATE_ROOT/.stub
CALLS=$STUB_DIR/aws-calls
SSM_COMMANDS=$STUB_DIR/ssm-commands
SSH_COMMANDS=$STUB_DIR/ssh-commands
KUBE_CALLS=$STUB_DIR/kube-calls
RUN_LOG=$STUB_DIR/run.log
RUN_STATUS=
ME=dev@example.com
FAKE_TOKEN=abcdef.0123456789abcdef
FAKE_HASH=4f0c6d7e5b8a9c1d2e3f405162738495a6b7c8d9e0f1a2b3c4d5e6f708192a3b
# The password of the master's registry user, as install-services.sh makes them.
FAKE_REGISTRY_PASSWORD=0123456789abcdef0123456789abcdef01234567
# Made-up credentials, as the AWS CLI exports them: temporary ones, by default.
TEMPORARY_KEY_ID=ASIAFAKEFAKEFAKEFAKE
LONG_TERM_KEY_ID=AKIAFAKEFAKEFAKEFAKE
FAKE_SECRET_KEY=fake-secret-key-that-must-never-be-printed
FAKE_SESSION_TOKEN=fake-session-token-that-must-never-be-printed
mkdir -p "$STUB_DIR"

# The config the commands read, as config_load leaves it.
reset_config() {
  SANDBOX_REGION=eu-west-1
  CP_SANDBOX_OWNER=$ME
  CP_SANDBOX_VPC_ID='vpc-0fake'
  CP_SANDBOX_SUBNET_ID='subnet-0fake'
  CP_SANDBOX_SECURITY_GROUP_IDS='sg-0fake'
  CP_SANDBOX_INSTANCE_PROFILE=cp-sandbox-profile
  CP_SANDBOX_WORKER_INSTANCE_PROFILE=cp-sandbox-profile
  CP_SANDBOX_MASTER_INSTANCE_TYPE=t3.medium
  CP_SANDBOX_WORKER_INSTANCE_TYPE=t3.medium
  CP_SANDBOX_TTL_HOURS=48
  SG_VPC=
}
# Nothing waits between polls.
SSM_POLL_SECONDS=0
SSM_COMMAND_POLL_SECONDS=0
REBOOT_POLL_SECONDS=0
IMAGE_POLL_SECONDS=0
CONNECT_POLL_SECONDS=0
TUNNEL_RETRY_SECONDS=0

NAT_ROUTES='{"RouteTables": [{"RouteTableId": "rtb-main", "Associations": [{"Main": true}], "Routes": [
  {"DestinationCidrBlock": "10.0.0.0/16", "GatewayId": "local", "State": "active"},
  {"DestinationCidrBlock": "0.0.0.0/0", "NatGatewayId": "nat-0fake", "State": "active"}]}]}'

# What the stubs answer, beyond their defaults. STUB_OUTPUT and STUB_ERROR are keyed by
# "<service> <operation>", or by "<service> <operation> <subject>" for the calls about one security
# group, instance profile or role. An error is an AWS error code, or raw:<the CLI's own message>.
declare -A STUB_OUTPUT=() STUB_ERROR=()
DESCRIBED=
IMAGES=
DESCRIBE_FAILS=0
SSM_FAILS_ON=
SSM_RUN_LOCALLY=0
# What ss on the master answers about the tunnel's API port.
SS_LISTENING=
SANDBOX_DOCKER_OBJECTS=

# reset_stubs: the default config, and the stubs' default answers.
reset_stubs() {
  reset_config
  STUB_OUTPUT=()
  STUB_ERROR=()
  DESCRIBED='{"Reservations": []}'
  IMAGES='{"Images": [{"ImageId": "ami-host", "State": "available", "Name": "cp-sandbox-host",
    "CreationDate": "2026-01-01T00:00:00.000Z"}]}'
  DESCRIBE_FAILS=0
  SSM_FAILS_ON=
  SSM_RUN_LOCALLY=0
  SS_LISTENING='LISTEN 0      128       10.0.0.7:9999      0.0.0.0:*'
  SANDBOX_DOCKER_OBJECTS=
  rm -f "$STUB_DIR"/queue.*
  # The boot ID before a reboot; every read after it answers another one.
  queue boot-id boot-old
  : >"$CALLS"
  : >"$SSM_COMMANDS"
  : >"$SSH_COMMANDS"
  : >"$KUBE_CALLS"
  : >"$SOCKS_CALLS"
}

# queue <name> <answer>...: what the stub answers to its next calls of that kind, in order.
queue() {
  local name=$1
  shift
  printf '%s\n' "$@" >>"$STUB_DIR/queue.$name"
}

# next_answer <name> <default>: the next queued answer, or the default once there is none. A file,
# because the commands run in a subshell.
next_answer() {
  local file=$STUB_DIR/queue.$1
  if [[ -s $file ]]; then
    head -n 1 "$file"
    sed -i 1d "$file"
  else
    printf '%s\n' "$2"
  fi
}

# arg_after <option> <args...>: the value given to the option.
arg_after() {
  local option=$1
  shift
  while (($#)); do
    if [[ $1 == "$option" ]]; then
      printf '%s\n' "${2-}"
      return 0
    fi
    shift
  done
}

# security_group <id> [all-from-self] [https-out]: describe-security-groups' view of a group in
# CP_SANDBOX_VPC_ID, or SG_VPC, with those rules or narrower ones.
security_group() {
  local id=$1 self=false https=false
  shift
  for feature in "$@"; do
    case $feature in
      all-from-self) self=true ;;
      https-out) https=true ;;
    esac
  done
  jq -cn --arg id "$id" --arg vpc "${SG_VPC:-$CP_SANDBOX_VPC_ID}" --argjson self "$self" \
    --argjson https "$https" '{GroupId: $id, VpcId: $vpc,
    IpPermissions: (if $self then [{IpProtocol: "-1", UserIdGroupPairs: [{GroupId: $id}]}]
      else [{IpProtocol: "tcp", FromPort: 22, ToPort: 22, IpRanges: [{CidrIp: "10.0.0.0/8"}]}] end),
    IpPermissionsEgress: (if $https then [{IpProtocol: "-1", IpRanges: [{CidrIp: "0.0.0.0/0"}]}]
      else [{IpProtocol: "tcp", FromPort: 80, ToPort: 80, IpRanges: [{CidrIp: "0.0.0.0/0"}]}] end)}'
}

# aws_call <dry-run output> <aws args...>, stubbed.
aws_call() {
  local operation subject='' key
  shift
  printf '%s\n' "$*" >>"$CALLS"
  operation="$1 $2"
  case $operation in
    "ec2 describe-security-groups") subject=$(arg_after --group-ids "$@") ;;
    "iam get-instance-profile") subject=$(arg_after --instance-profile-name "$@") ;;
    "iam list-attached-role-policies") subject=$(arg_after --role-name "$@") ;;
  esac
  for key in "$operation${subject:+ $subject}" "$operation"; do
    if [[ -n ${STUB_ERROR[$key]+set} ]]; then
      case ${STUB_ERROR[$key]} in
        raw:*) printf '\n%s\n' "${STUB_ERROR[$key]#raw:}" >&2 ;;
        *) printf '\nAn error occurred (%s) when calling the %s operation: stubbed\n' \
          "${STUB_ERROR[$key]}" "$2" >&2 ;;
      esac
      return 254
    fi
    if [[ -n ${STUB_OUTPUT[$key]+set} ]]; then
      printf '%s\n' "${STUB_OUTPUT[$key]}"
      return 0
    fi
  done
  default_answer "$@"
}

default_answer() {
  case "$1 $2" in
    "sts get-caller-identity") printf 'arn:aws:iam::123456789012:user/dev\n' ;;
    "configure export-credentials")
      printf '%s\n' "{\"Version\": 1, \"AccessKeyId\": \"$TEMPORARY_KEY_ID\", \"SecretAccessKey\": \"$FAKE_SECRET_KEY\", \"SessionToken\": \"$FAKE_SESSION_TOKEN\"}"
      ;;
    "ec2 describe-vpcs")
      if [[ " $* " == *CidrBlock* ]]; then printf '10.0.0.0/16\t10.1.0.0/16\n'; else printf '%s\n' "$CP_SANDBOX_VPC_ID"; fi
      ;;
    "ec2 describe-subnets")
      if [[ " $* " == *"Subnets[0].AvailabilityZone"* ]]; then
        printf 'eu-west-1a\n'
        return 0
      fi
      jq -cn --arg vpc "$CP_SANDBOX_VPC_ID" '{SubnetId: "subnet-0fake", VpcId: $vpc,
        State: "available", AvailableIpAddressCount: 100, AvailabilityZone: "eu-west-1a",
        MapPublicIpOnLaunch: false}'
      ;;
    "ec2 describe-key-pairs") printf '[]\n' ;;
    "ec2 import-key-pair") printf 'key-0imported\n' ;;
    "ec2 describe-route-tables") printf '%s\n' "$NAT_ROUTES" ;;
    "ec2 describe-nat-gateways") printf 'available\n' ;;
    "ec2 describe-security-groups")
      security_group "$(arg_after --group-ids "$@")" all-from-self https-out
      ;;
    "iam get-instance-profile") printf 'cp-sandbox-role\n' ;;
    "iam list-attached-role-policies")
      printf 'arn:aws:iam::aws:policy/ReadOnlyAccess\tarn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore\n'
      ;;
    "ec2 describe-instance-type-offerings") printf 't3.medium\n' ;;
    "ec2 describe-instances")
      if ((DESCRIBE_FAILS)); then
        printf 'An error occurred (RequestLimitExceeded)\n' >&2
        return 255
      fi
      if [[ " $* " == *" --filters "* ]]; then printf '%s\n' "$DESCRIBED"; else printf '10.0.0.7\n'; fi
      ;;
    "ec2 describe-images")
      if [[ " $* " == *" --owners self "* ]]; then
        printf '%s\n' "$IMAGES"
      elif [[ " $* " == *RootDeviceName* ]]; then
        printf '/dev/xvda\n'
      else
        queued_answer image-state $'available\tNone' DescribeImages
      fi
      ;;
    "ssm get-parameter") printf 'ami-stock\n' ;;
    "ec2 run-instances") next_answer run-instances i-launched ;;
    "ssm describe-instance-information") printf 'Online\n' ;;
    "ec2 create-image") printf 'ami-built\n' ;;
    "ssm send-command") next_answer command-id cmd-1 ;;
    "ssm get-command-invocation")
      queued_answer invocation '{"Status": "Success", "StandardOutputContent": "", "ResponseCode": 0}' \
        GetCommandInvocation
      ;;
  esac
}

# queued_answer <name> <default> <operation>: the next queued answer, as next_answer gives it; one
# written !<code> is that AWS error instead.
queued_answer() {
  local answer
  answer=$(next_answer "$1" "$2")
  if [[ $answer == '!'* ]]; then
    printf '\nAn error occurred (%s) when calling the %s operation: stubbed\n' "${answer#!}" "$3" >&2
    return 254
  fi
  printf '%s\n' "$answer"
}

# Run Command, stubbed: records the comment and the command, and answers a boot ID read. With
# SSM_RUN_LOCALLY set, runs the command here instead. The real one stays as real_ssm_run.
eval "real_ssm_run() $(declare -f ssm_run | sed 1d)"
ssm_run() {
  printf '%s\n%s\n--\n' "# $3" "$4" >>"$SSM_COMMANDS"
  if ((SSM_RUN_LOCALLY)); then
    bash -c "$4"
    return
  fi
  if [[ -n $SSM_FAILS_ON && $3 == "$SSM_FAILS_ON" ]]; then
    printf 'stubbed failure\n' >&2
    die "$3 on $1: Failed"
  fi
  case $4 in
    *boot_id*) next_answer boot-id boot-new ;;
  esac
}

# SSH, stubbed: records the command, and answers what the master holds. Freeing the API port
# answers from the "released" queue, nothing once it is empty; an answer written ! fails, as SSH
# does. What instance_ssh_file copies is kept as ssh-file.<name of the file on the master>.
instance_ssh() {
  local answer
  printf '%s\n' "$4" >>"$SSH_COMMANDS"
  case $4 in
    *' && cat >'*) cat >"$STUB_DIR/ssh-file.$(basename "$(sed -E 's/.* && cat >([^ ]+)\.part .*/\1/' <<<"$4")")" ;;
    *admin.conf) admin_conf ;;
    *join-token) printf '%s\n' "$FAKE_TOKEN" ;;
    *ca-cert-hash) printf '%s\n' "$FAKE_HASH" ;;
    *registry.crt) printf '%s\n' '-----BEGIN CERTIFICATE-----' 'ZmFrZS1yZWdpc3RyeQ==' '-----END CERTIFICATE-----' ;;
    *registry/password.secret) printf '%s\n' "$FAKE_REGISTRY_PASSWORD" ;;
    'ss '*) [[ -z $SS_LISTENING ]] || printf '%s\n' "$SS_LISTENING" ;;
    *'sudo kill'*)
      answer=$(next_answer released '')
      [[ $answer != '!' ]] || return 255
      [[ -z $answer ]] || printf '%s\n' "$answer"
      ;;
  esac
}

# The master's admin.conf as kubeadm writes it, with made-up keys.
admin_conf() {
  cat <<'EOF'
apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: ZmFrZS1jYQ==
    server: https://10.0.0.7:6443
  name: kubernetes
contexts:
- context:
    cluster: kubernetes
    user: kubernetes-admin
  name: kubernetes-admin@kubernetes
current-context: kubernetes-admin@kubernetes
kind: Config
preferences: {}
users:
- name: kubernetes-admin
  user:
    client-certificate-data: ZmFrZS1jZXJ0
    client-key-data: ZmFrZS1rZXk=
EOF
}

# kubectl, stubbed: records the call, and answers from the kubectl queue; an answer written
# !<message> is that error instead.
kube_call() {
  local answer
  shift
  printf '%s\n' "$*" >>"$KUBE_CALLS"
  answer=$(next_answer kubectl 'NAME   STATUS   ROLES    AGE   VERSION|ip-10-0-0-7   Ready    master   1h    v1.15.4')
  if [[ $answer == '!'* ]]; then
    printf '%s\n' "${answer#!}" >&2
    return 1
  fi
  tr '|' '\n' <<<"$answer"
}

# curl through the tunnel's SOCKS proxy, stubbed: records the call, and answers the HTTP status
# from the socks queue, 200 once it is empty.
SOCKS_CALLS=$STUB_DIR/socks-calls
socks_call() {
  shift
  printf '%s\n' "$*" >>"$SOCKS_CALLS"
  next_answer socks 200
}

check() {   # check <description> <actual> <expected>
  if [[ $2 == "$3" ]]; then
    printf 'ok      %s\n' "$1"
  else
    printf 'FAILED  %s\n        expected: %s\n        actual:   %s\n' "$1" "$3" "$2"
    FAILURES=$((FAILURES + 1))
  fi
}

# run_command <command> [args...]: runs it the way in-tools.sh does — errexit in force, a die
# ending it — and sets RUN_STATUS. Its calls start a fresh record.
run_command() {
  : >"$CALLS"
  : >"$SSM_COMMANDS"
  : >"$SSH_COMMANDS"
  : >"$KUBE_CALLS"
  : >"$SOCKS_CALLS"
  set +e
  (set -e; "$@") >"$RUN_LOG" 2>&1
  RUN_STATUS=$?
  set -e
}

called() {   # called <aws args prefix>: the recorded calls starting with it
  grep -F -- "$1" "$CALLS" || true
}

logged() {   # logged <text>: how many lines of the last run's output hold it
  count "$1" "$RUN_LOG"
}

count() {   # count <text> <file>: how many lines of the file hold the text
  grep -cF -- "$1" "$2" || true
}

# instance <id> <sandbox> <role> <state> <expires> [<owner>]: one describe-instances entry, owned
# by this config's owner unless another is given.
instance() {
  jq -cn --arg id "$1" --arg sandbox "$2" --arg role "$3" --arg state "$4" --arg expires "$5" \
    --arg owner "${6-$ME}" '
    {InstanceId: $id, State: {Name: $state}, PrivateIpAddress: "10.0.0.9", ImageId: "ami-running",
     Tags: [{Key: "cp-sandbox", Value: $sandbox}, {Key: "cp-sandbox-owner", Value: $owner},
            {Key: "cp-sandbox-role", Value: $role}, {Key: "cp-sandbox-expires", Value: $expires}]}'
}

described() {   # described <instance>...: describe-instances output holding them
  jq -cn '{Reservations: [{Instances: $ARGS.positional}]}' --jsonargs "$@"
}

finish() {
  if ((FAILURES)); then
    printf '%s check(s) failed\n' "$FAILURES"
    exit 1
  fi
}

reset_stubs
