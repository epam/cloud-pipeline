#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Tests of the sandbox scripts that need no AWS account: every command under --dry-run, the config
# checks, the steps of connect, services, ui and down on Docker with docker stubbed, the libraries
# and the commands with AWS stubbed (in-tools-test.sh, check-test.sh, ami-test.sh, connect-test.sh
# and services-test.sh, in the tools image), the host scripts and the containers' entrypoints with
# the commands they run stubbed (host-test.sh, services-host-test.sh and api-entrypoint-test.sh, as
# root in containers of their own), the registry's settings with the registry's image, and the
# reaper in lambda/ (its pytest tests, with EC2 stubbed, and cfn-lint over its template). Nothing
# here calls AWS.
#
# Usage: .agents/skills/dev-sandbox/scripts/test/sandbox-test.sh
# Needs Docker. Runs on the host, so bash 3.2 like the other host scripts.
set -euo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
SCRIPTS_DIR=$(dirname "$TEST_DIR")
SKILL_DIR=$(dirname "$SCRIPTS_DIR")
REPO_ROOT=$(git -C "$SKILL_DIR" rev-parse --show-toplevel)
# shellcheck source=../lib/common.sh
. "$SCRIPTS_DIR/lib/common.sh"
# shellcheck source=../lib/host.sh
. "$SCRIPTS_DIR/lib/host.sh"
# shellcheck source=../lib/tunnel.sh
. "$SCRIPTS_DIR/lib/tunnel.sh"
# shellcheck source=../lib/services.sh
. "$SCRIPTS_DIR/lib/services.sh"
# shellcheck source=../lib/ui.sh
. "$SCRIPTS_DIR/lib/ui.sh"
REAL_REPO_ROOT=$REPO_ROOT

# Every case names its own config and state; a developer's own settings must not leak in.
unset CP_SANDBOX_ENV_FILE CP_SANDBOX_STATE_DIR

# Under the repository rather than the system temp directory, which Docker may not be able to
# mount (Colima shares only the home directory). Gitignored.
SCRATCH_PARENT=$REPO_ROOT/.agents/.temp
SECRET=fake-secret-that-must-never-be-printed
OWNER=dev@example.com

FAILURES=0
OUTPUT=
STATUS=

# The image services-host-test.sh runs in: it needs python3, which the master has, and tar and
# openssl.
SERVICES_HOST_TEST_IMAGE=python:3.12
# The image the reaper's tests and cfn-lint run in, built from docker/lambda-test.Dockerfile and
# tagged with a hash of it.
LAMBDA_TEST_IMAGE_REPOSITORY=cp-sandbox-lambda-test
# A sandbox name no developer has, for the case that makes a real Docker network.
NETWORK_SANDBOX=sandbox-test-$$
# The registry settings of the platform's deployment, which services-host-test.sh compares with.
DEPLOY_REGISTRY_CONFIG=deploy/docker/cp-docker-registry/v2.7.1/update_config.sh
# The real registry the settings install-services.sh writes are tried with, on a network of its own.
REGISTRY_TEST_CONTAINER=cp-sandbox-test-registry-$$
REGISTRY_TEST_NETWORK=cp-sandbox-test-registry-$$
mkdir -p "$SCRATCH_PARENT"
SCRATCH=$(mktemp -d "$SCRATCH_PARENT/dev-sandbox-test.XXXXXX")
REGISTRY_SETTINGS=$SCRATCH/registry-settings
trap 'rm -rf "$SCRATCH"; docker rm --force "$(tunnel_container "$NETWORK_SANDBOX")" >/dev/null 2>&1 || true
  docker network rm "$(sandbox_network "$NETWORK_SANDBOX")" >/dev/null 2>&1 || true
  docker volume rm "$(service_volume "$NETWORK_SANDBOX" db)" >/dev/null 2>&1 || true
  docker rm --force "$REGISTRY_TEST_CONTAINER" >/dev/null 2>&1 || true
  docker network rm "$REGISTRY_TEST_NETWORK" >/dev/null 2>&1 || true' EXIT

write_config() {   # write_config <file>: a complete config, with values nobody's account has
  cat >"$1" <<EOF
AWS_PROFILE=
AWS_ACCESS_KEY_ID=AKIAFAKEFAKEFAKEFAKE
AWS_SECRET_ACCESS_KEY=$SECRET
AWS_SESSION_TOKEN=
AWS_REGION=eu-west-1
CP_SANDBOX_VPC_ID=vpc-0fake
CP_SANDBOX_SUBNET_ID=subnet-0fake
CP_SANDBOX_SECURITY_GROUP_IDS=sg-0fake1, sg-0fake2
CP_SANDBOX_INSTANCE_PROFILE=cp-sandbox-profile
CP_SANDBOX_OWNER=$OWNER
EOF
}

fake_state() {   # fake_state <name>: local state for a sandbox, as up leaves it
  mkdir -p "$STATE/$1"
  printf '{"name":"%s","region":"eu-west-1","instance_id":"i-0local","private_ip":"10.0.0.5","expires":"2020-01-01T00:00:00Z"}\n' \
    "$1" >"$STATE/$1/state.json"
  : >"$STATE/$1/ssh-key.pem"
  : >"$STATE/$1/kubeconfig.secret"
}

# fake_services_state <name>: local state for a sandbox as up, connect and services-prepare leave
# it: what services-prepare reads, and what it writes for the API's container.
fake_services_state() {
  fake_state "$1"
  printf '{"name":"%s","region":"eu-west-1","instance_id":"i-0local","private_ip":"10.0.0.5","expires":"2020-01-01T00:00:00Z","ami":"ami-0host","kube_ca_cert_hash":"%s"}\n' \
    "$1" 0000000000000000000000000000000000000000000000000000000000000000 >"$STATE/$1/state.json"
  : >"$STATE/$1/kube-join-token.secret"
  for file in api.properties api.env api-tls.crt jwt-public-key.pem jwt-private-key.pem; do
    printf 'fake %s\n' "$file" >"$STATE/$1/$file"
  done
}

# fake_repo: a checkout holding what the API's image is built from, the Elasticsearch's
# Dockerfile, and the jars of the API, jwt-generator and the optional services built, as REPO_ROOT
# for services; real_repo puts the real one back.
fake_repo() {
  local file service
  FAKE_REPO=$CASE_DIR/repo
  for file in $API_IMAGE_SOURCES "$DB_VERSION_FILE" "$ELASTICSEARCH_DOCKERFILE"; do
    mkdir -p "$FAKE_REPO/$(dirname "$file")"
    cp "$REAL_REPO_ROOT/$file" "$FAKE_REPO/$file"
  done
  for file in "$API_JAR" "$JWT_GENERATOR_JAR" $(for service in $OPTIONAL_SERVICES; do service_jar "$service"; done); do
    mkdir -p "$FAKE_REPO/$(dirname "$file")"
    printf 'fake %s\n' "$file" >"$FAKE_REPO/$file"
  done
  REPO_ROOT=$FAKE_REPO
}

# fake_service_settings <name>: the settings of every optional service, as services-prepare
# writes them, in the sandbox's local state.
fake_service_settings() {
  local service
  for service in $OPTIONAL_SERVICES; do
    printf 'fake %s settings\n' "$service" >"$STATE/$1/$service.properties"
  done
}

# fake_lookup <key> <key=value ...>: the value of the key in the list, if it has one.
fake_lookup() {
  local pair
  for pair in $2; do
    case $pair in
      "$1="*) printf '%s\n' "${pair#*=}"; return 0 ;;
    esac
  done
}

# role_filter <docker args...>: the role a docker ps filters on.
role_filter() {
  while [ $# -gt 0 ]; do
    case $1 in
      label=cp-sandbox-role=*) printf '%s\n' "${1#label=cp-sandbox-role=}"; return 0 ;;
    esac
    shift
  done
}

real_repo() {
  REPO_ROOT=$REAL_REPO_ROOT
}

# fake_client [<node_modules>]: the fake checkout's client/, with client/.nvmrc as the real one
# has it, and with node_modules installed when asked. After fake_repo.
fake_client() {
  mkdir -p "$FAKE_REPO/client"
  cp "$REAL_REPO_ROOT/$UI_NVMRC" "$FAKE_REPO/$UI_NVMRC"
  if [ "${1-}" = node_modules ]; then
    mkdir -p "$FAKE_REPO/client/node_modules"
  fi
}

# host_stubbed <function> [args...]: runs a host function from lib/tunnel.sh or lib/services.sh
# with docker and run_in_tools replaced by stubs, which record their calls in $CASE_DIR/calls; sets
# OUTPUT and STATUS. The stubs answer from FAKE_*: the state of the tunnel, API and database
# containers, the API container's config label, the objects docker lists, whether the network and
# the database volume exist and the network's subnets, whether the API's image is built, whether
# docker rm and docker network rm fail, which of the tools image's steps fails, and what its
# services-api writes, on stdout and stderr. The other containers' states and config labels come
# from FAKE_STATES and FAKE_CONFIGS, "<role>=<value>" lists; their logs, read whole, from
# FAKE_SERVICE_LOG, which says that the service started unless set; and FAKE_ES_FAILS makes the
# Elasticsearch's health check fail. Every other docker call succeeds, so the tools image counts as
# built.
host_stubbed() {
  FAKE_TUNNEL_STATE=${FAKE_TUNNEL_STATE-} FAKE_OBJECTS=${FAKE_OBJECTS-} FAKE_NETWORK=${FAKE_NETWORK-0}
  FAKE_TOOLS_FAILS_ON=${FAKE_TOOLS_FAILS_ON-} FAKE_RM_FAILS=${FAKE_RM_FAILS-0}
  FAKE_NETWORK_RM_FAILS=${FAKE_NETWORK_RM_FAILS-0} FAKE_API_STATE=${FAKE_API_STATE-}
  FAKE_DB_STATE=${FAKE_DB_STATE-} FAKE_API_CONFIG=${FAKE_API_CONFIG-} FAKE_VOLUME=${FAKE_VOLUME-0}
  FAKE_SUBNETS=${FAKE_SUBNETS-} FAKE_NO_API_IMAGE=${FAKE_NO_API_IMAGE-0} FAKE_LOG=${FAKE_LOG-}
  FAKE_API_ANSWER=${FAKE_API_ANSWER-} FAKE_API_ERROR=${FAKE_API_ERROR-} FAKE_STATES=${FAKE_STATES-}
  FAKE_CONFIGS=${FAKE_CONFIGS-} FAKE_ES_FAILS=${FAKE_ES_FAILS-0}
  FAKE_SERVICE_LOG=${FAKE_SERVICE_LOG-'[INFO ] Started FakeApplication in 12.5 seconds (JVM running for 14.0)'}
  : >"$CASE_DIR/calls"
  mkdir -p "$STATE"
  STATE_ROOT=$STATE
  ENV_FILE=$CONFIG
  HOST_DRY_RUN=0
  # A subshell of its own, not a $(...), whose case patterns bash 3.2 cannot parse; and outside any
  # condition, where errexit would be off whatever the subshell set. The stubs in it are called by
  # the functions under test.
  set +e
  # shellcheck disable=SC2329
  (
    docker() {
      printf 'docker %s\n' "$*" >>"$CASE_DIR/calls"
      case "$1 $2" in
        "ps --all")
          case " $* " in
            *" label=cp-sandbox-role=tunnel "*) [ -z "$FAKE_TUNNEL_STATE" ] || printf '%s\n' "$FAKE_TUNNEL_STATE" ;;
            *" label=cp-sandbox-role=api "*cp-sandbox-config*) [ -z "$FAKE_API_CONFIG" ] || printf '%s\n' "$FAKE_API_CONFIG" ;;
            *" label=cp-sandbox-role=api "*) [ -z "$FAKE_API_STATE" ] || printf '%s\n' "$FAKE_API_STATE" ;;
            *" label=cp-sandbox-role=db "*) [ -z "$FAKE_DB_STATE" ] || printf '%s\n' "$FAKE_DB_STATE" ;;
            *" label=cp-sandbox-role="*cp-sandbox-config*) fake_lookup "$(role_filter "$@")" "$FAKE_CONFIGS" ;;
            *" label=cp-sandbox-role="*) fake_lookup "$(role_filter "$@")" "$FAKE_STATES" ;;
            *) printf '%s' "$FAKE_OBJECTS" | grep ' container ' || true ;;
          esac
          ;;
        "network ls") printf '%s' "$FAKE_OBJECTS" | grep ' network ' || true ;;
        "volume ls") printf '%s' "$FAKE_OBJECTS" | grep ' volume ' || true ;;
        "network inspect")
          [ "$FAKE_NETWORK" = 1 ] || return 1
          case " $* " in *" --format "*) printf '%s\n' "$FAKE_SUBNETS" ;; esac
          ;;
        "volume inspect") [ "$FAKE_VOLUME" = 1 ] ;;
        "image inspect")
          case $3 in "$API_IMAGE_REPOSITORY":*) [ "$FAKE_NO_API_IMAGE" = 0 ] ;; esac
          ;;
        "build -f")
          find "$(dirname "$3")" -mindepth 1 -maxdepth 1 -exec basename {} \; | sort | paste -sd ' ' - |
            sed 's/^/context: /' >>"$CASE_DIR/calls"
          ;;
        "network rm") [ "$FAKE_NETWORK_RM_FAILS" = 0 ] ;;
        "rm --force") [ "$FAKE_RM_FAILS" = 0 ] ;;
        "logs --tail") printf '%s\n' "${FAKE_LOG:-ssh: Could not resolve hostname}" ;;
        "logs "*) printf '%s\n' "$FAKE_SERVICE_LOG" ;;
        "run --rm")
          case " $* " in *" --entrypoint java "*) printf 'eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJwaXBlX2FkbWluIn0.c2ln\n' ;; esac
          ;;
        "exec -i") sed 's/^/sql: /' >>"$CASE_DIR/calls" ;;
        "exec "*)
          case " $* " in *" curl "*) [ "$FAKE_ES_FAILS" = 0 ] ;; esac
          ;;
      esac
    }
    run_in_tools() {
      printf 'tools %s|sandbox=%s|objects=%s\n' "$*" "$TOOLS_SANDBOX" \
        "$(printf '%s' "$TOOLS_DOCKER_OBJECTS" | tr '\n' ';')" >>"$CASE_DIR/calls"
      if [ -n "$FAKE_TOOLS_FAILS_ON" ]; then
        case " $* " in
          *" $FAKE_TOOLS_FAILS_ON "*) return 1 ;;
        esac
      fi
      case " $* " in
        *" services-sequences "*) printf "SELECT setval('pipeline.s_pipeline_run', 123400000, false);\n" ;;
        *" services-api "*)
          printf 'api-arg [%s]\n' "$@" >>"$CASE_DIR/calls"
          printf '%s\n' "$FAKE_API_ANSWER"
          [ -z "$FAKE_API_ERROR" ] || printf '%s\n' "$FAKE_API_ERROR" >&2
          ;;
      esac
    }
    set -e
    "$@"
  ) >"$CASE_DIR/output" 2>&1
  STATUS=$?
  set -e
  OUTPUT=$(cat "$CASE_DIR/output")
  unset FAKE_TUNNEL_STATE FAKE_OBJECTS FAKE_NETWORK FAKE_TOOLS_FAILS_ON FAKE_RM_FAILS \
    FAKE_NETWORK_RM_FAILS FAKE_API_STATE FAKE_DB_STATE FAKE_API_CONFIG FAKE_VOLUME FAKE_SUBNETS \
    FAKE_NO_API_IMAGE FAKE_LOG FAKE_API_ANSWER FAKE_API_ERROR FAKE_STATES FAKE_CONFIGS \
    FAKE_ES_FAILS FAKE_SERVICE_LOG
}

# lambda_test_image: sets LAMBDA_TEST_IMAGE, building it when its Dockerfile has changed since the
# last build.
lambda_test_image() {
  local dockerfile=$SKILL_DIR/docker/lambda-test.Dockerfile
  LAMBDA_TEST_IMAGE=$LAMBDA_TEST_IMAGE_REPOSITORY:$(git hash-object "$dockerfile" | cut -c1-12)
  docker image inspect "$LAMBDA_TEST_IMAGE" >/dev/null 2>&1 && return 0
  docker build -f "$dockerfile" -t "$LAMBDA_TEST_IMAGE" "$SKILL_DIR/docker" >&2
}

# lambda_check <name> <command> [args...]: runs the command in that image, as the calling user,
# with no network, in lambda/, mounted read-only, and fails the case, as <name>, when it fails.
# Python writes no bytecode there, and pytest no cache.
lambda_check() {
  local name=$1
  shift
  OUTPUT=
  if ! lambda_test_image; then
    fail "the image $LAMBDA_TEST_IMAGE did not build"
    return 0
  fi
  if OUTPUT=$(docker run --rm --network none --user "$(id -u):$(id -g)" --env HOME=/tmp \
    --env PYTHONDONTWRITEBYTECODE=1 --env PYTHONPATH=/lambda --env AWS_EC2_METADATA_DISABLED=true \
    --volume "$SKILL_DIR/lambda:/lambda:ro" --workdir /lambda "$LAMBDA_TEST_IMAGE" "$@" 2>&1); then
    [ -z "$OUTPUT" ] || printf '%s\n' "$OUTPUT" | sed 's/^/  /'
  else
    fail "$name failed"
  fi
}

calls() {   # calls <prefix>: the recorded calls starting with it
  grep -F -- "$1" "$CASE_DIR/calls" || true
}

# call_order <prefix>...: the line numbers of the first recorded call starting with each prefix,
# or "-" for one never made.
call_order() {
  local prefix line sep=''
  for prefix in "$@"; do
    line=$(grep -nF -- "$prefix" "$CASE_DIR/calls" | head -n 1 | cut -d: -f1)
    printf '%s%s' "$sep" "${line:--}"
    sep=' '
  done
  printf '\n'
}

expect() {   # expect <description> <actual> <expected>
  [ "$2" = "$3" ] || fail "$1: expected '$3', got '$2'"
}

# in_order <prefix>...: "yes" when a call starting with each prefix was made, each after the one
# before.
in_order() {
  local order line previous=0
  order=$(call_order "$@")
  for line in $order; do
    if [ "$line" = - ] || [ "$line" -le "$previous" ]; then
      printf 'no: %s\n' "$order"
      return 0
    fi
    previous=$line
  done
  printf 'yes\n'
}

counted() {   # counted <prefix>: how many recorded calls hold it
  grep -cF -- "$1" "$CASE_DIR/calls" || true
}

# sandbox <args...>: runs sandbox.sh against this test's config and state; sets OUTPUT and STATUS.
sandbox() {
  in_case_config sandbox.sh "$@"
}

# tools <command> [args...]: runs tools.sh the same way.
tools() {
  in_case_config tools.sh "$@"
}

in_case_config() {
  local script=$1
  shift
  if OUTPUT=$(CP_SANDBOX_ENV_FILE=$CONFIG CP_SANDBOX_STATE_DIR=$STATE \
    bash "$SCRIPTS_DIR/$script" "$@" 2>&1); then
    STATUS=0
  else
    STATUS=$?
  fi
}

# bare_git <args...>: git without the developer's own config, whose signing or hooks a
# throw-away repository must not run into.
bare_git() {
  GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1 \
    git -c user.name=test -c user.email=test@example.com "$@"
}

fail() {
  printf 'FAILED  %s: %s\n' "$CASE" "$1"
  printf '%s\n' "$OUTPUT" | sed 's/^/        | /'
  FAILURES=$((FAILURES + 1))
}

expect_status() {
  [ "$STATUS" = "$1" ] || fail "exit status $STATUS, expected $1"
}

expect_output() {
  case $OUTPUT in
    *"$1"*) ;;
    *) fail "output lacks: $1" ;;
  esac
}

expect_no_output() {
  case $OUTPUT in
    *"$1"*) fail "output has: $1" ;;
  esac
}

# start_case <description>: a fresh config and an empty state directory for each case.
start_case() {
  CASE=$1
  CASE_DIR=$(mktemp -d "$SCRATCH/case.XXXXXX")
  CONFIG=$CASE_DIR/sandbox.env
  STATE=$CASE_DIR/state
  write_config "$CONFIG"
  FAILURES_BEFORE=$FAILURES
}

end_case() {
  [ "$FAILURES" != "$FAILURES_BEFORE" ] || printf 'ok      %s\n' "$CASE"
}

start_case "the config and the state default to the main checkout; a worktree's own config wins"
MAIN=$CASE_DIR/main
WORKTREE=$CASE_DIR/worktree
bare_git init -q -b main "$MAIN"
bare_git -C "$MAIN" commit -q --allow-empty -m init
bare_git -C "$MAIN" worktree add -q -b work "$WORKTREE"
MAIN=$(cd "$MAIN" && pwd -P)
config_paths "$WORKTREE"
[ "$ENV_FILE" = "$MAIN/.env.sandbox.local" ] || fail "worktree without a config: ENV_FILE=$ENV_FILE"
[ "$STATE_ROOT" = "$MAIN/.agents/sandboxes.local" ] || fail "worktree: STATE_ROOT=$STATE_ROOT"
: >"$WORKTREE/.env.sandbox.local"
config_paths "$WORKTREE"
[ "$ENV_FILE" = "$WORKTREE/.env.sandbox.local" ] || fail "worktree with a config: ENV_FILE=$ENV_FILE"
[ "$STATE_ROOT" = "$MAIN/.agents/sandboxes.local" ] || fail "worktree: STATE_ROOT=$STATE_ROOT"
config_paths "$MAIN"
[ "$ENV_FILE" = "$MAIN/.env.sandbox.local" ] || fail "main checkout: ENV_FILE=$ENV_FILE"
CP_SANDBOX_ENV_FILE=other.env CP_SANDBOX_STATE_DIR=other-state config_paths "$WORKTREE"
[ "$ENV_FILE" = "$PWD/other.env" ] || fail "override: ENV_FILE=$ENV_FILE"
[ "$STATE_ROOT" = "$PWD/other-state" ] || fail "override: STATE_ROOT=$STATE_ROOT"
end_case

start_case "the tools image unsets the AWS variables the config leaves empty"
# shellcheck disable=SC2016 # expanded by the shell in the tools image
tools bash -c 'echo "profile:${AWS_PROFILE+set} token:${AWS_SESSION_TOKEN+set} key:${AWS_ACCESS_KEY_ID:+set}"'
expect_status 0
expect_output "profile: token: key:set"
end_case

start_case "a command creates the state directory, ignored by git whatever the branch"
sandbox extend
expect_status 1
[ "$(cat "$STATE/.gitignore" 2>/dev/null)" = '*' ] || fail "$STATE/.gitignore does not ignore everything"
end_case

start_case "a missing config is replaced by the template, and the command stops"
rm "$CONFIG"
sandbox status
expect_status 1
expect_output "copied the template to $CONFIG"
cmp -s "$CONFIG" "$SKILL_DIR/sandbox.env.example" || fail "the config is not a copy of the template"
case $(ls -l "$CONFIG") in -rw-------*) ;; *) fail "the copied config is readable by others" ;; esac
end_case

start_case "--dry-run does not copy the template"
rm "$CONFIG"
sandbox status --dry-run
expect_status 1
[ ! -e "$CONFIG" ] || fail "the template was copied"
end_case

start_case "missing keys are named, and no value is printed"
printf 'AWS_SECRET_ACCESS_KEY=%s\nAWS_REGION=eu-west-1\n' "$SECRET" >"$CONFIG"
sandbox status --dry-run
expect_status 1
expect_output "AWS_PROFILE (or AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY)"
expect_output CP_SANDBOX_SUBNET_ID
expect_output CP_SANDBOX_OWNER
expect_no_output "$SECRET"
end_case

start_case "the tools image removes one pair of matching quotes around a value"
sed -e 's/^AWS_REGION=.*/AWS_REGION="eu-west-1"/' -e "s/^CP_SANDBOX_OWNER=.*/CP_SANDBOX_OWNER='$OWNER'/" \
  -e 's/^AWS_SESSION_TOKEN=.*/AWS_SESSION_TOKEN=""/' "$CONFIG" >"$CONFIG.new" && mv "$CONFIG.new" "$CONFIG"
printf 'OTHER_VARIABLE="kept"\n' >>"$CONFIG"
# shellcheck disable=SC2016 # expanded by the shell in the tools image
tools bash -c 'echo "region:$AWS_REGION owner:$CP_SANDBOX_OWNER token:${AWS_SESSION_TOKEN+set} other:$OTHER_VARIABLE"'
expect_status 0
expect_output "region:eu-west-1 owner:$OWNER token: other:\"kept\""
sandbox status --dry-run
expect_status 0
expect_output "cp-sandbox-owner=$OWNER in eu-west-1"
end_case

start_case "an unclosed quote, or a comment after a value, is refused, by key"
sed -e 's/^AWS_REGION=.*/AWS_REGION="eu-west-1" # Ireland/' -e 's/^CP_SANDBOX_VPC_ID=.*/CP_SANDBOX_VPC_ID="vpc-0fake/' \
  "$CONFIG" >"$CONFIG.new" && mv "$CONFIG.new" "$CONFIG"
printf 'CP_SANDBOX_TTL_HOURS=4 # hours\n' >>"$CONFIG"
sandbox status --dry-run
expect_status 1
expect_output "does not read as meant: AWS_REGION CP_SANDBOX_TTL_HOURS CP_SANDBOX_VPC_ID"
expect_no_output "Ireland"
end_case

start_case "check --dry-run prints its read-only calls, and passes on what they would answer"
sandbox check --dry-run
expect_status 0
expect_output "aws --region eu-west-1 sts get-caller-identity"
expect_output "ec2 describe-vpcs --vpc-ids vpc-0fake"
expect_output "ec2 describe-subnets --subnet-ids subnet-0fake"
expect_output "ec2 describe-route-tables --filters Name=vpc-id,Values=vpc-0fake"
expect_output "ec2 describe-nat-gateways --nat-gateway-ids nat-0dryrun"
expect_output "ec2 describe-security-groups --group-ids sg-0fake1"
expect_output "ec2 describe-security-groups --group-ids sg-0fake2"
expect_output "iam get-instance-profile --instance-profile-name cp-sandbox-profile"
expect_output "iam list-attached-role-policies --role-name cp-sandbox-dry-run-role"
expect_output "ec2 describe-instance-type-offerings --location-type availability-zone --filters Name=location,Values=eu-west-1a"
expect_output "aws --region eu-west-1 configure export-credentials --format process"
expect_output "0 failed, 0 warning(s)."
expect_no_output "$SECRET"
end_case

start_case "ami --dry-run looks up this version's host image, and launches nothing"
sandbox ami --dry-run
expect_status 0
expect_output "ec2 describe-images --owners self --filters Name=tag:cp-sandbox-ami,Values="
expect_output "Host image, version "
expect_no_output "run-instances"
end_case

start_case "ami --rebuild lists what it would replace, and changes nothing"
sandbox ami --rebuild --dry-run
expect_status 0
expect_output "untag     ami-0dryrun000000000"
expect_output "Nothing was changed. Run ami --rebuild --yes"
expect_no_output "run-instances"
expect_no_output "delete-tags"
end_case

start_case "ami --rebuild --yes --dry-run prints the whole build"
sandbox ami --rebuild --yes --dry-run
expect_status 0
expect_output "ssm get-parameter --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-6.1-x86_64"
expect_output "ec2 run-instances --image-id ami-0dryrun00000al2023"
expect_output '{"Key":"cp-sandbox-role","Value":"ami-builder"}'
expect_output "ssm send-command --instance-ids i-0dryrun000000000 --document-name AWS-RunShellScript --comment 'copy prepare-host.sh to /opt/cp-sandbox"
expect_output "bash /opt/cp-sandbox/prepare-host.sh prepare"
expect_output "ec2 reboot-instances --instance-ids i-0dryrun000000000"
expect_output "bash /opt/cp-sandbox/prepare-host.sh check"
expect_output "bash /opt/cp-sandbox/prepare-host.sh seal"
expect_output "ec2 create-image --instance-id i-0dryrun000000000"
expect_output "ec2 terminate-instances --instance-ids i-0dryrun000000000"
expect_output "ec2 delete-tags --resources ami-0dryrun000000000 --tags Key=cp-sandbox-ami"
expect_no_output "deregister-image"
end_case

start_case "up --dry-run prints the launch of a tagged, private, IMDSv2-only master, and its install"
sandbox up --dry-run
expect_status 0
expect_output "sts get-caller-identity"
expect_output "ec2 describe-images --owners self --filters Name=tag:cp-sandbox-ami,Values="
expect_output "ec2 run-instances --image-id ami-0dryrun000000000 --instance-type t3.medium"
expect_output "--iam-instance-profile '{\"Name\":\"cp-sandbox-profile\"}'"
expect_output '"SubnetId":"subnet-0fake","AssociatePublicIpAddress":false'
expect_output '"Groups":["sg-0fake1","sg-0fake2"]'
expect_output "--metadata-options HttpTokens=required,HttpEndpoint=enabled"
expect_output '{"Key":"cp-sandbox-owner","Value":"dev@example.com"},{"Key":"cp-sandbox-role","Value":"master"},{"Key":"cp-sandbox-expires","Value":"'
expect_output '"ResourceType":"volume"'
expect_output "ssh_authorized_keys:"
expect_output "ec2 wait instance-running --instance-ids i-0dryrun000000000"
expect_output "ssm describe-instance-information --filters Key=InstanceIds,Values=i-0dryrun000000000"
expect_output "copy install-master.sh kubeadm-init-config.yaml canal.yaml to /opt/cp-sandbox"
expect_output "bash /opt/cp-sandbox/install-master.sh /opt/cp-sandbox/kubeadm-init-config.yaml /opt/cp-sandbox/canal.yaml"
expect_output "--document-name AWS-StartSSHSession"
expect_output "ec2-user@i-0dryrun000000000 'sudo cat /etc/kubernetes/admin.conf'"
expect_output "kubeconfig.secret"
expect_output "kube-join-token.secret"
expect_no_output "$SECRET"
expect_no_output "ssm get-parameter"
[ ! -e "$STATE" ] || fail "--dry-run created $STATE"
end_case

start_case "up picks up the only local sandbox"
fake_state dev-abcd
sandbox up --dry-run
expect_status 0
expect_output "Name=tag:cp-sandbox,Values=dev-abcd"
end_case

start_case "up refuses to guess between several local sandboxes"
fake_state dev-abcd
fake_state dev-efgh
sandbox up --dry-run
expect_status 1
expect_output "several local sandboxes"
expect_no_output "run-instances"
end_case

start_case "a name outside the pattern is refused"
sandbox up --dry-run --name Bad_Name
expect_status 1
expect_output "invalid sandbox name"
end_case

start_case "status --dry-run lists the local state and asks for the owner's instances"
fake_state dev-abcd
sandbox status --dry-run
expect_status 0
expect_output "dev-abcd"
expect_output "i-0local"
expect_output "expired"
expect_output "On Docker here, labelled cp-sandbox:"
expect_output "ec2 describe-instances --filters Name=tag:cp-sandbox-owner,Values=dev@example.com"
end_case

start_case "extend needs --hours, a whole number above 0"
fake_state dev-abcd
sandbox extend --dry-run
expect_status 1
sandbox extend --hours 0 --dry-run
expect_status 1
sandbox extend --hours 1.5 --dry-run
expect_status 1
end_case

start_case "extend --dry-run moves the expiry tag of the sandbox's instances"
sandbox extend --hours 5 --name dev-abcd --dry-run
expect_status 0
expect_output "Name=tag:cp-sandbox,Values=dev-abcd"
expect_output "ec2 create-tags --resources i-0dryrun000000000 --tags Key=cp-sandbox-expires,Value="
end_case

start_case "down without --yes lists what it would remove and removes nothing"
fake_state dev-abcd
sandbox down --dry-run
expect_status 0
expect_output "terminate i-0dryrun000000000"
expect_output "remove    $STATE/dev-abcd"
expect_output "Nothing was removed. Run down again with --yes"
expect_no_output "terminate-instances"
[ -f "$STATE/dev-abcd/state.json" ] || fail "the local state is gone"
end_case

start_case "down --yes --dry-run prints the termination and keeps everything"
fake_state dev-abcd
sandbox down --yes --dry-run
expect_status 0
expect_output "ec2 terminate-instances --instance-ids i-0dryrun000000000"
expect_output "ec2 wait instance-terminated --instance-ids i-0dryrun000000000"
[ -f "$STATE/dev-abcd/state.json" ] || fail "the local state is gone"
end_case

start_case "down needs --name when several sandboxes are local"
fake_state dev-abcd
fake_state dev-efgh
sandbox down --yes --dry-run
expect_status 1
expect_output "several local sandboxes"
expect_no_output "terminate-instances"
end_case

start_case "connect --dry-run prints the tunnel, its container and its check, and starts nothing"
fake_state dev-abcd
sandbox connect --dry-run
expect_status 0
expect_output "ec2 describe-instances --filters Name=tag:cp-sandbox,Values=dev-abcd"
expect_output "Sandbox dev-abcd: master i-0local (10.0.0.5) is running."
expect_output "ec2-user@i-0local 'pids=\$(sudo ss -Hltnp "
expect_output "-N -T -o ExitOnForwardFailure=yes -L 0.0.0.0:6443:127.0.0.1:6443 -R 10.0.0.5:9999:api:9999 -D 0.0.0.0:1080 ec2-user@i-0local"
expect_output "[dry-run] docker network create --label cp-sandbox=dev-abcd cp-sandbox-dev-abcd"
expect_output "[dry-run] docker run --detach --init --name cp-sandbox-dev-abcd-tunnel --label cp-sandbox=dev-abcd --label cp-sandbox-role=tunnel --network cp-sandbox-dev-abcd --network-alias cp-sandbox-dev-abcd-master"
expect_output "--env-file $CONFIG"
expect_output "in-tools.sh tunnel --name dev-abcd"
expect_output "[dry-run] kubectl --kubeconfig $STATE/dev-abcd/kubeconfig.secret get nodes -o wide"
expect_output "ec2-user@i-0local 'ss -Hltn"
expect_output "[dry-run] curl --silent --insecure --output /dev/null --write-out '%{http_code}' --max-time 10 --socks5 cp-sandbox-dev-abcd-master:1080 https://10.0.0.5:6443/healthz"
expect_output "[dry-run] Nothing was started or recorded."
expect_no_output "$SECRET"
[ -z "$(docker ps --all --quiet --filter label=cp-sandbox=dev-abcd)" ] || fail "a container was started"
if docker network inspect cp-sandbox-dev-abcd >/dev/null 2>&1; then fail "the network was created"; fi
if grep -q tunnel_ "$STATE/dev-abcd/state.json"; then fail "the tunnel was recorded"; fi
end_case

start_case "connect --stop --dry-run forgets nothing, and removes nothing"
fake_state dev-abcd
sandbox connect --stop --dry-run
expect_status 0
expect_output "[dry-run] remove from $STATE/dev-abcd/state.json: tunnel_container"
expect_output "Sandbox dev-abcd has no tunnel container."
end_case

start_case "connect checks the sandbox, starts the tunnel container on its network, then checks the tunnel from there"
host_stubbed cmd_connect --name dev-abcd
expect_status 0
expect "the steps, in order" "$(in_order "in-tools.sh connect-prepare --name dev-abcd|sandbox=|" \
  "docker network create --label cp-sandbox=dev-abcd cp-sandbox-dev-abcd" \
  "docker run --detach --init --name cp-sandbox-dev-abcd-tunnel" \
  "in-tools.sh connect-check --name dev-abcd|sandbox=dev-abcd|")" yes
expect "the container is labelled, and on the network under the name of the master's tunnel" \
  "$(counted "--label cp-sandbox=dev-abcd --label cp-sandbox-role=tunnel --network cp-sandbox-dev-abcd --network-alias cp-sandbox-dev-abcd-master --user $(id -u):$(id -g) --tmpfs /home/sandbox:mode=1777")" 1
expect "the container has the state and the config, as every tools container does" \
  "$(counted "--volume $STATE:$STATE") $(counted "--env-file $CONFIG")" '1 1'
expect "the container runs the tools image, and its process is the tunnel" \
  "$(calls 'docker run --detach' | grep -cE " $TOOLS_IMAGE_REPOSITORY:[0-9a-f]{12} bash $SCRIPTS_DIR/in-tools.sh tunnel --name dev-abcd\$" || true)" 1
end_case

start_case "connect keeps a tunnel container that runs, and checks it again"
FAKE_NETWORK=1
FAKE_TUNNEL_STATE=running
host_stubbed cmd_connect --name dev-abcd
expect_status 0
expect "no network made, no container started or removed" \
  "$(calls 'docker network create')$(calls 'docker run')$(calls 'docker rm')" ''
expect "the check runs" "$(counted 'connect-check --name dev-abcd|sandbox=dev-abcd|')" 1
expect_output "The tunnel container cp-sandbox-dev-abcd-tunnel is running already."
end_case

start_case "connect replaces a tunnel container that has stopped"
FAKE_NETWORK=1
FAKE_TUNNEL_STATE=exited
host_stubbed cmd_connect --name dev-abcd
expect_status 0
expect "the stopped container goes, then a new one starts" \
  "$(in_order 'docker rm --force cp-sandbox-dev-abcd-tunnel' 'docker run --detach')" yes
end_case

start_case "connect shows the tunnel's log, and removes its container, when the check fails"
FAKE_TOOLS_FAILS_ON=connect-check
host_stubbed cmd_connect --name dev-abcd
expect_status 1
expect "the check, the log, then the container removed" "$(in_order connect-check \
  'docker logs --tail 20 cp-sandbox-dev-abcd-tunnel' 'docker rm --force cp-sandbox-dev-abcd-tunnel')" yes
expect_output "  | ssh: Could not resolve hostname"
expect_output "the tunnel did not work, so connect removed its container"
end_case

start_case "connect touches nothing on Docker when the sandbox fails its check"
FAKE_TOOLS_FAILS_ON=connect-prepare
host_stubbed cmd_connect --name dev-abcd
expect_status 1
expect "no docker call" "$(calls docker)" ''
end_case

start_case "connect --stop forgets the tunnel, and removes its container but not the network"
FAKE_TUNNEL_STATE=running
host_stubbed cmd_connect --name dev-abcd --stop
expect_status 0
expect "the state first, then the container" \
  "$(in_order 'connect-stop --name dev-abcd|' 'docker rm --force cp-sandbox-dev-abcd-tunnel')" yes
expect "the network stays" "$(calls 'docker network')" ''
expect_output "Closed the tunnel of sandbox dev-abcd: removed the container cp-sandbox-dev-abcd-tunnel."
end_case

start_case "connect --stop --dry-run, with a tunnel container, names it and removes nothing"
FAKE_TUNNEL_STATE=running
host_stubbed cmd_connect --name dev-abcd --stop --dry-run
expect_status 0
expect "the state is left to the tools image's --dry-run, and the container stays" \
  "$(counted 'connect-stop --name dev-abcd --dry-run|') $(calls 'docker rm')" '1 '
expect_output "[dry-run] docker rm --force cp-sandbox-dev-abcd-tunnel"
expect_output "[dry-run] Nothing was removed."
expect_no_output "Closed the tunnel"
end_case

start_case "down tells the tools image of the sandbox's Docker objects, and removes them once down --yes has succeeded"
FAKE_OBJECTS=$(printf '%s\n' 'dev-abcd container cp-sandbox-dev-abcd-tunnel running' 'dev-abcd network cp-sandbox-dev-abcd -')
host_stubbed host_down --name dev-abcd --yes
expect_status 0
expect "docker lists them by the sandbox's label, for down, and again once it has succeeded" \
  "$(counted 'docker ps --all --filter label=cp-sandbox=dev-abcd --format') $(counted 'docker network ls --filter label=cp-sandbox=dev-abcd --format')" '2 2'
expect "the tools image's down hears of both" \
  "$(counted "in-tools.sh down --name dev-abcd --yes|sandbox=|objects=dev-abcd container cp-sandbox-dev-abcd-tunnel running;dev-abcd network cp-sandbox-dev-abcd -")" 1
expect "then the container goes, then the network" "$(in_order 'in-tools.sh down' \
  'docker rm --force cp-sandbox-dev-abcd-tunnel' 'docker network rm cp-sandbox-dev-abcd')" yes
expect_output "Removed the container cp-sandbox-dev-abcd-tunnel."
expect_output "Removed the network cp-sandbox-dev-abcd."
end_case

start_case "down removes nothing from Docker without --yes, under --dry-run, or when the tools image's down fails"
FAKE_OBJECTS='dev-abcd container cp-sandbox-dev-abcd-tunnel running'
host_stubbed host_down --name dev-abcd
expect_status 0
expect "without --yes" "$(calls 'docker rm')" ''
FAKE_OBJECTS='dev-abcd container cp-sandbox-dev-abcd-tunnel running'
host_stubbed host_down --name dev-abcd --yes --dry-run
expect_status 0
expect "under --dry-run" "$(calls 'docker rm')" ''
expect "down hears of --dry-run" "$(counted 'in-tools.sh down --name dev-abcd --yes --dry-run|')" 1
FAKE_OBJECTS='dev-abcd container cp-sandbox-dev-abcd-tunnel running'
FAKE_TOOLS_FAILS_ON=down
host_stubbed host_down --name dev-abcd --yes
expect_status 1
expect "when down fails" "$(calls 'docker rm')" ''
end_case

start_case "down --yes names a network it has to leave"
FAKE_OBJECTS='dev-abcd network cp-sandbox-dev-abcd -'
FAKE_NETWORK_RM_FAILS=1
host_stubbed host_down --name dev-abcd --yes
expect_status 0
expect_output "Left the network cp-sandbox-dev-abcd: a container still uses it, one not labelled cp-sandbox=dev-abcd or one that could not be removed."
end_case

start_case "down --yes fails, and says so, when it cannot remove a container"
FAKE_OBJECTS=$(printf '%s\n' 'dev-abcd container cp-sandbox-dev-abcd-tunnel running' 'dev-abcd network cp-sandbox-dev-abcd -')
FAKE_RM_FAILS=1
FAKE_NETWORK_RM_FAILS=1
host_stubbed host_down --name dev-abcd --yes
expect_status 1
expect_no_output "Removed the container"
expect_output "Left the network cp-sandbox-dev-abcd"
expect_output "could not remove the container(s) cp-sandbox-dev-abcd-tunnel: remove them with docker rm --force"
end_case

start_case "services --dry-run prints the build, the master's services, the containers and the API's configuration, and starts nothing"
fake_services_state dev-abcd
sandbox services --dry-run
expect_status 0
expect_output "[dry-run] The tunnel of sandbox dev-abcd is not running; without --dry-run, services stops here."
expect_output "ec2 import-key-pair --key-name cp-sandbox-dev-abcd --public-key-material fileb://$STATE/dev-abcd/ssh-key.pem.pub"
expect_output "bash /opt/cp-sandbox/install-services.sh 10.0.0.5 /opt/cp-sandbox/tool.Dockerfile https://cloud-pipeline-oss-builds.s3.amazonaws.com/builds/develop/"
expect_output "[dry-run] docker run --detach --name cp-sandbox-dev-abcd-db --label cp-sandbox=dev-abcd --label cp-sandbox-role=db --network cp-sandbox-dev-abcd --network-alias db --volume cp-sandbox-dev-abcd-db:/var/lib/postgresql/data"
expect_output "[dry-run] docker run --detach --init --name cp-sandbox-dev-abcd-api --label cp-sandbox=dev-abcd --label cp-sandbox-role=api"
expect_output "--network cp-sandbox-dev-abcd --network-alias api --cap-add NET_ADMIN --device /dev/net/tun"
expect_output "--env-file $CONFIG --env-file $STATE/dev-abcd/api.env"
expect_output "java -Xmx2g -Djava.security.egd=file:/dev/./urandom -jar $STATE/dev-abcd/api.jar --spring.config.location=file:$STATE/dev-abcd/api.properties"
expect_output "psql -U pipeline -d pipeline, with: SELECT setval('pipeline.s_pipeline_run', 100000000, false)"
expect_output "[dry-run] curl --request POST https://api:9999/pipeline/restapi/cloud/region"
expect_output "[dry-run] curl --request POST https://api:9999/pipeline/restapi/dockerRegistry/register"
expect_output '"userName":"pipe_admin","password":"<registry-password.secret>"'
expect_output "[dry-run] java -jar $JWT_GENERATOR_JAR --private $STATE/dev-abcd/jwt-private-key.pem --expires 3600 ... >$STATE/dev-abcd/registry-token.secret"
expect_output "cat >/etc/cp-sandbox/registry/notify-token.secret.part"
expect_output "--notify-url https://10.0.0.5:9999/pipeline/restapi/dockerRegistry/notify --api-cert /opt/cp-sandbox/api-tls.crt"
expect_output "ec2-user@i-0local 'sudo cat /etc/cp-sandbox/registry/password.secret'"
expect_output "[dry-run] Nothing was configured."
expect_no_output "$SECRET"
[ -z "$(docker ps --all --quiet --filter label=cp-sandbox=dev-abcd)" ] || fail "a container was started"
if docker volume inspect cp-sandbox-dev-abcd-db >/dev/null 2>&1; then fail "the database volume was created"; fi
[ ! -e "$STATE/dev-abcd/api.jar" ] || fail "the jar was copied"
end_case

start_case "services --pipe-common-from-checkout --dry-run packs the checkout's pipe-common, copies it over SSH, and has the master serve it"
fake_services_state dev-abcd
sandbox services --dry-run --pipe-common-from-checkout
expect_status 0
expect_output "[dry-run] pack workflows/pipe-common of $REPO_ROOT into pipe-common.tar.gz"
expect_output "ec2-user@i-0local"
expect_output "cat >/opt/cp-sandbox/pipe-common.tar.gz.part && mv -f /opt/cp-sandbox/pipe-common.tar.gz.part /opt/cp-sandbox/pipe-common.tar.gz"
expect_output "/opt/cp-sandbox/tool.Dockerfile https://cloud-pipeline-oss-builds.s3.amazonaws.com/builds/develop/cloud-pipeline.0.0.0.0.dryrun.tgz --notify-url https://10.0.0.5:9999/pipeline/restapi/dockerRegistry/notify --api-cert /opt/cp-sandbox/api-tls.crt --pipe-common /opt/cp-sandbox/pipe-common.tar.gz"
expect_output "and pipe-common from this checkout."
expect_no_output "$SECRET"
end_case

start_case "services hands --pipe-common-from-checkout to the tools image's services-master"
fake_services_state dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd --pipe-common-from-checkout
real_repo
expect_status 0
expect "services-master hears of it, and services-prepare does not" \
  "$(counted "in-tools.sh services-master --name dev-abcd --pipe-common-from-checkout|") $(counted 'services-prepare --name dev-abcd --docker-subnets  --pipe-common')" '1 0'
end_case

start_case "services builds what it needs, prepares the sandbox, then starts the database and the API on the network, and configures it from there"
fake_services_state dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_SUBNETS='172.18.0.0/16 '
host_stubbed cmd_services --name dev-abcd
real_repo
expect_status 0
expect "the steps, in order" "$(in_order "in-tools.sh services-prepare --name dev-abcd --docker-subnets 172.18.0.0/16 |sandbox=|" \
  "docker run --rm --entrypoint java" "in-tools.sh services-master --name dev-abcd|sandbox=|" \
  "docker volume create --label cp-sandbox=dev-abcd cp-sandbox-dev-abcd-db" \
  "docker run --detach --name cp-sandbox-dev-abcd-db" "docker exec cp-sandbox-dev-abcd-db pg_isready -q -h 127.0.0.1 -U pipeline -d pipeline" \
  "docker run --detach --init --name cp-sandbox-dev-abcd-api" "in-tools.sh services-wait --name dev-abcd|sandbox=dev-abcd|" \
  "docker exec -i cp-sandbox-dev-abcd-db psql" "in-tools.sh services-configure --name dev-abcd|sandbox=dev-abcd|")" yes
# shellcheck disable=SC2012 # the mode, as ls shows it on macOS and Linux alike
expect "jwt-generator mints the admin's token with the sandbox's key, for its owner only" \
  "$(counted "--private $STATE/dev-abcd/jwt-private-key.pem --expires 2592000 --claim user_id=1 --claim user_name=pipe_admin --claim role=ROLE_ADMIN --claim group=ADMIN") $(ls -l "$STATE/dev-abcd/admin-token.secret" | cut -c1-10)" \
  '1 -rw-------'
expect "the jars were there, so nothing is built" "$(counted 'gradlew')" 0
expect "the API runs a copy of the jar, from the local state" "$(cat "$STATE/dev-abcd/api.jar")" "fake $API_JAR"
expect "the API container is labelled with what it started from, on the network as api, and routes the VPC through a tun device" \
  "$(calls 'docker run --detach --init' | grep -cE -- "--label cp-sandbox=dev-abcd --label cp-sandbox-role=api --label cp-sandbox-config=[0-9a-f]{12} --network cp-sandbox-dev-abcd --network-alias api --cap-add NET_ADMIN --device /dev/net/tun " || true)" 1
expect "it has the checkout and the sandbox's state, read-only, and the config and api.env as its environment" \
  "$(calls 'docker run --detach --init' | grep -cF -- "--volume $FAKE_REPO:$FAKE_REPO:ro --volume $STATE/dev-abcd:$STATE/dev-abcd:ro" || true) $(calls 'docker run --detach --init' | grep -cF -- "--env-file $CONFIG --env-file $STATE/dev-abcd/api.env cp-sandbox-api:" || true)" \
  '1 1'
expect "the sequences' SQL goes to the database" "$(calls 'sql: ')" "sql: SELECT setval('pipeline.s_pipeline_run', 123400000, false);"
expect "the database is the PostgreSQL the platform's installer deploys" \
  "$(calls 'docker run --detach --name cp-sandbox-dev-abcd-db' | grep -c " postgres:$(sed -n 's/^PSG_VERSION=//p' "$REAL_REPO_ROOT/$DB_VERSION_FILE")\$" || true)" 1
end_case

start_case "services mints the registry's own token until an hour after the sandbox expires, and again once its expiry has moved past it"
fake_services_state dev-abcd
fake_repo
sed -e 's/2020-01-01T00:00:00Z/2030-01-01T00:00:00Z/' "$STATE/dev-abcd/state.json" >"$STATE/dev-abcd/state.json.new"
mv "$STATE/dev-abcd/state.json.new" "$STATE/dev-abcd/state.json"
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd
expect_status 0
EXPIRES_2030=1893456000
TOKEN_SECONDS=$(calls 'docker run --rm --entrypoint java' | grep -F -- '--expires' | grep -vF -- '--expires 2592000' |
  sed -E 's/.*--expires ([0-9]+) .*/\1/')
expect "the admin's token and the registry's are minted, the registry's for the admin too" \
  "$(counted '--entrypoint java') $(counted '--claim user_id=1 --claim user_name=pipe_admin --claim role=ROLE_ADMIN --claim group=ADMIN')" '2 2'
expect "the registry's token is valid until an hour after the sandbox expires" \
  "$(($(date +%s) + TOKEN_SECONDS - EXPIRES_2030 - 3600 < 60 && $(date +%s) + TOKEN_SECONDS - EXPIRES_2030 - 3600 > -60))" 1
# shellcheck disable=SC2012 # the mode, as ls shows it on macOS and Linux alike
expect "it is its owner's only, and when it expires is kept beside it" \
  "$(ls -l "$STATE/dev-abcd/registry-token.secret" | cut -c1-10) $(cat "$STATE/dev-abcd/registry-token.until")" \
  "-rw------- $((EXPIRES_2030 + 3600))"
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd
expect "a second run mints the admin's token only" "$(counted '--entrypoint java')" 1
sed -e 's/2030-01-01T00:00:00Z/2030-01-02T00:00:00Z/' "$STATE/dev-abcd/state.json" >"$STATE/dev-abcd/state.json.new"
mv "$STATE/dev-abcd/state.json.new" "$STATE/dev-abcd/state.json"
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd
expect "after extend, the registry's token is minted again, for the new expiry" \
  "$(counted '--entrypoint java') $(cat "$STATE/dev-abcd/registry-token.until")" "2 $((EXPIRES_2030 + 86400 + 3600))"
fake_services_state dev-abcd
rm "$STATE/dev-abcd/registry-token.until"
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd
expect "a sandbox past its expiry gets a token for an hour" "$(calls 'docker run --rm --entrypoint java' | grep -c -- '--expires 3600 ' || true)" 1
real_repo
end_case

start_case "services --log shows the API's log, with the tokens, keys and credentials in it blanked out"
FAKE_API_STATE=running
FAKE_LOG=$(printf '%s\n' 'Scaling cluster up. Command: nodeup.py --run_id 1 --kubeadm_token abcdef.0123456789abcdef --region_id eu-west-1.' \
  'EnvVars: {API_TOKEN=eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJQSVBFX0FETUlOIn0.c2lnbmF0dXJlLXBhcnQ, AWS_SESSION_TOKEN=IQoJb3JpZ2lu, API=https://10.0.0.5:9999/pipeline/restapi/}' \
  "Created POD: env=[EnvVar(name=CP_ACCOUNT_ID_1, value=AKIAFAKEFAKEFAKEFAKE, valueFrom=null), EnvVar(name=CP_ACCOUNT_KEY_1, value=$SECRET/x+y, valueFrom=null), EnvVar(name=SSH_PASS, value=pass-of-the-run, valueFrom=null), EnvVar(name=RUN_ID, value=7, valueFrom=null)]" \
  'Authorization: Bearer eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJQSVBFX0FETUlOIn0.c2lnbmF0dXJlLXBhcnQ from ASIAFAKEFAKEFAKEFAKE' \
  "key MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7$(printf 'A%.0s' $(seq 1 80))== end" \
  "session $(printf 'Zm9v%.0s' $(seq 1 30)) end" \
  '{"name": "CP_ACCOUNT_KEY_1", "value": "json/value+of+the+key"}, {"name": "RUN_ID", "value": "7"}' \
  "loose $(printf 'fAk/%.0s' $(seq 1 10)), and a commit c8004491d892254e8a73f17ba5056fde0b6bbf60." \
  'Started Application in 203 seconds')
host_stubbed cmd_services --name dev-abcd --log --tail 50
expect_status 0
expect "it reads the API container's log, as many lines as asked" "$(counted 'docker logs --tail 50 cp-sandbox-dev-abcd-api')" 1
expect_output "--kubeadm_token <join token> --region_id eu-west-1."
expect_output "EnvVars: {API_TOKEN=<blanked>, AWS_SESSION_TOKEN=<blanked>, API=https://10.0.0.5:9999/pipeline/restapi/}"
expect_output "env=[EnvVar(name=CP_ACCOUNT_ID_1, value=<blanked>, valueFrom=null), EnvVar(name=CP_ACCOUNT_KEY_1, value=<blanked>, valueFrom=null), EnvVar(name=SSH_PASS, value=<blanked>, valueFrom=null), EnvVar(name=RUN_ID, value=7, valueFrom=null)]"
expect_output "Authorization: Bearer <token> from <aws key id>"
expect_output "key <key> end"
expect_output "session <base64> end"
expect_output '{"name": "CP_ACCOUNT_KEY_1", "value": "<blanked>"}, {"name": "RUN_ID", "value": "7"}'
expect_output "loose <blanked>, and a commit <blanked>."
expect_output "Started Application in 203 seconds"
expect_no_output "abcdef.0123456789abcdef"
expect_no_output "eyJzdWIi"
expect_no_output "MIIEvQIBADAN"
expect_no_output "$SECRET"
expect_no_output "FAKEFAKE"
expect_no_output "pass-of-the-run"
expect_no_output "IQoJ"
expect_no_output "Zm9vZm9v"
expect_no_output "json/value"
expect_no_output "sEcReT"
host_stubbed cmd_services --name dev-abcd --log
expect_status 1
expect_output "sandbox dev-abcd runs no api container here"
host_stubbed cmd_services --name dev-abcd --log --tail 0
expect_status 1
end_case

start_case "api calls the REST API from the sandbox's network, and blanks out the secrets in the answer"
FAKE_API_STATE=running
FAKE_API_ANSWER="{\"status\": \"RUNNING\", \"log\": \"EnvVar(name=CP_ACCOUNT_KEY_1, value=$SECRET, valueFrom=null) AKIAFAKEFAKEFAKEFAKE\"}"
FAKE_API_ERROR='error: GET run/7: HTTP 500, Bearer eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJQSVBFX0FETUlOIn0.c2lnbmF0dXJl'
host_stubbed cmd_api --name dev-abcd --jq . GET run/7
expect_status 0
expect "the call runs in the tools image, on the sandbox's network, with the arguments given" \
  "$(counted "tools bash $SCRIPTS_DIR/in-tools.sh services-api --name dev-abcd --jq . GET run/7|sandbox=dev-abcd|")" 1
expect_output '"status": "RUNNING"'
expect_output "EnvVar(name=CP_ACCOUNT_KEY_1, value=<blanked>, valueFrom=null) <aws key id>"
expect_output "error: GET run/7: HTTP 500, Bearer <token>"
expect_no_output "$SECRET"
expect_no_output "FAKEFAKE"
expect_no_output "eyJzdWIi"
FAKE_API_STATE=running
host_stubbed cmd_api --name dev-abcd POST run '{"cmdTemplate": "sleep 30"}'
expect "a JSON body goes as one argument" "$(counted 'api-arg [{"cmdTemplate": "sleep 30"}]')" 1
host_stubbed cmd_api --name dev-abcd GET whoami
expect_status 1
expect_output "sandbox dev-abcd runs no API here: run sandbox.sh services"
expect "and makes no call without the API" "$(calls tools)" ''
end_case

start_case "api --dry-run prints the call, with no token"
fake_services_state dev-abcd
sandbox api --dry-run GET run/7
expect_status 0
expect_output "[dry-run] curl --request GET https://api:9999/pipeline/restapi/run/7"
expect_no_output "Authorization"
expect_no_output "$SECRET"
end_case

start_case "ui runs the checkout's GUI on the sandbox's network, against its API, on 127.0.0.1 only, and waits for it to compile"
fake_state dev-abcd
printf 'header.payload.signature\n' >"$STATE/dev-abcd/admin-token.secret"
fake_repo
fake_client node_modules
NODE_IMAGE=node:$(tr -d '[:space:]' <"$REAL_REPO_ROOT/$UI_NVMRC")
FAKE_API_STATE=running FAKE_SERVICE_LOG=$(printf '%s\n' 'Starting the development server...' 'Compiled successfully!')
host_stubbed cmd_ui --name dev-abcd
expect_status 0
expect "the container is labelled, with what it started from, on the network, its two ports published on 127.0.0.1 only" \
  "$(calls 'docker run --detach --init' | grep -cE -- "^docker run --detach --init --name cp-sandbox-dev-abcd-ui --label cp-sandbox=dev-abcd --label cp-sandbox-role=ui --label cp-sandbox-config=[0-9a-f]{12} --network cp-sandbox-dev-abcd --publish 127.0.0.1:3100:3100 --publish 127.0.0.1:9199:9199 " || true)" 1
expect "it runs npm start as the calling user, in client/, mounted from the checkout, in the Node image of client/.nvmrc, through the entrypoint" \
  "$(calls 'docker run --detach --init' | grep -cF -- "--user $(id -u):$(id -g) --env HOME=/tmp --workdir $FAKE_REPO/client --volume $FAKE_REPO/client:$FAKE_REPO/client " || true) $(calls 'docker run --detach --init' | grep -cF -- "--volume $SKILL_DIR/docker/ui-entrypoint.sh:/usr/local/bin/ui-entrypoint.sh:ro" || true) $(calls 'docker run --detach --init' | grep -cE -- "--entrypoint bash $NODE_IMAGE /usr/local/bin/ui-entrypoint.sh npm start\$" || true)" \
  '1 1 1'
expect "the dev proxy forwards to the API on the network, trusting its certificate, and listens on every address of the container, as the dev server does, with polling for the mounted checkout" \
  "$(calls 'docker run --detach --init' | grep -cF -- '--env SERVER=https://api:9999/pipeline/ --env PROXY_INSECURE=1 --env PROXY_TARGET=https://api:9999 --env PROXY_SERVER=http://localhost:9199/pipeline/ --env PROXY_HOST=0.0.0.0 --env PROXY_PORT=9199 ' || true) $(calls 'docker run --detach --init' | grep -cF -- ' --env HOST=0.0.0.0 --env PORT=3100 --env HTTPS=false --env CHOKIDAR_USEPOLLING=true --env BROWSER=none ' || true)" \
  '1 1'
expect "it answers the GUI's own origin alone, and every setting of the proxy is given, so that the checkout's client/.env files cannot send the token elsewhere" \
  "$(calls 'docker run --detach --init' | grep -cF -- ' --env PROXY_CORS_ORIGIN=http://localhost:3100 --env PROXY_DISABLED= --env SKIP_DEV_PROXY= ' || true)" 1
expect "the admin's token is mounted alone, read-only, for the entrypoint to read: neither the token nor the rest of the state is handed over" \
  "$(calls 'docker run --detach --init' | grep -cF -- "--volume $STATE/dev-abcd/admin-token.secret:/run/cp-sandbox/admin-token.secret:ro --volume" || true) $(calls 'docker run --detach --init' | grep -cF -e "--volume $STATE/dev-abcd:" -e header.payload.signature -e PROXY_BEARER_TOKEN || true)" \
  '1 0'
expect "node_modules is there, so npm ci does not run; the log tells of the compile" \
  "$(counted 'npm ci') $(counted 'docker logs cp-sandbox-dev-abcd-ui')" '0 1'
expect_output "  gui    http://localhost:3100/, on 127.0.0.1 only: every request goes to the API as its admin"
expect_output "  stop   sandbox.sh ui --stop"
end_case

start_case "ui installs client/node_modules with npm ci first, when it is missing"
fake_state dev-abcd
printf 'header.payload.signature\n' >"$STATE/dev-abcd/admin-token.secret"
fake_repo
fake_client
FAKE_API_STATE=running FAKE_SERVICE_LOG='Compiled with warnings.'
host_stubbed cmd_ui --name dev-abcd
expect_status 0
expect "npm ci runs as the calling user in the Node image, with npm's cache in the state root, before the dev server starts" \
  "$(in_order "docker run --rm --user $(id -u):$(id -g) --volume $FAKE_REPO/client:$FAKE_REPO/client --volume $STATE/.npm-cache:$STATE/.npm-cache --env HOME=/tmp --env npm_config_cache=$STATE/.npm-cache --workdir $FAKE_REPO/client $NODE_IMAGE npm ci" \
    'docker run --detach --init --name cp-sandbox-dev-abcd-ui') $([ -d "$STATE/.npm-cache" ] && echo made)" 'yes made'
real_repo
end_case

start_case "ui leaves a GUI that runs with the same settings, and starts one that does not again"
fake_state dev-abcd
printf 'header.payload.signature\n' >"$STATE/dev-abcd/admin-token.secret"
fake_repo
fake_client node_modules
FAKE_API_STATE=running FAKE_SERVICE_LOG='Compiled successfully!'
host_stubbed cmd_ui --name dev-abcd
CONFIG_LABEL=$(calls 'docker run --detach --init' | sed -E 's/.*--label cp-sandbox-config=([0-9a-f]{12}) .*/\1/')
FAKE_API_STATE=running FAKE_STATES='ui=running' FAKE_CONFIGS="ui=$CONFIG_LABEL" FAKE_SERVICE_LOG='Compiled successfully!'
host_stubbed cmd_ui --name dev-abcd
expect_status 0
expect "nothing is started or removed" "$(counted 'docker run')$(counted 'docker rm')" '00'
expect_output "The GUI's container cp-sandbox-dev-abcd-ui is running already, on the same checkout and settings."
FAKE_API_STATE=running FAKE_STATES='ui=running' FAKE_CONFIGS='ui=000000000000' FAKE_SERVICE_LOG='Compiled successfully!'
host_stubbed cmd_ui --name dev-abcd
expect "one with other settings, as from another checkout, is started again" \
  "$(in_order 'docker rm --force cp-sandbox-dev-abcd-ui' 'docker run --detach --init --name cp-sandbox-dev-abcd-ui')" yes
real_repo
end_case

start_case "ui needs the API that services runs, and its admin token, and starts nothing without them"
fake_state dev-abcd
fake_repo
fake_client node_modules
host_stubbed cmd_ui --name dev-abcd
expect_status 1
expect_output "sandbox dev-abcd runs no API here: run sandbox.sh services first"
FAKE_API_STATE=running
host_stubbed cmd_ui --name dev-abcd
expect_status 1
expect_output "sandbox dev-abcd has no admin-token.secret: run sandbox.sh services"
expect "no container is started" "$(calls 'docker run')" ''
real_repo
end_case

start_case "ui stops, with the end of the log, when the GUI does not compile, and leaves the dev server running"
fake_state dev-abcd
printf 'header.payload.signature\n' >"$STATE/dev-abcd/admin-token.secret"
fake_repo
fake_client node_modules
FAKE_API_STATE=running FAKE_SERVICE_LOG=$(printf '%s\n' 'Compiled successfully!' 'Compiling...' 'Failed to compile.')
FAKE_LOG="Module not found: Error: Can't resolve './missing'"
host_stubbed cmd_ui --name dev-abcd
expect_status 1
expect "the end of its log is shown, and its container stays" \
  "$(counted 'docker logs --tail 40 cp-sandbox-dev-abcd-ui') $(counted 'docker rm')" '1 0'
expect_output "  | Module not found: Error: Can't resolve './missing'"
expect_output "the GUI did not compile. Its dev server runs on in cp-sandbox-dev-abcd-ui, and compiles again when a file changes"
FAKE_API_STATE=running FAKE_SERVICE_LOG=$(printf '%s\n' 'Failed to compile.' 'Compiling...' 'Compiled successfully!')
host_stubbed cmd_ui --name dev-abcd
expect "a compile that failed, then one that passed, is compiled" "$STATUS" 0
FAKE_API_STATE=running FAKE_STATES='ui=exited' FAKE_SERVICE_LOG='Starting the development server...'
host_stubbed cmd_ui --name dev-abcd
expect_status 1
expect_output "the GUI's dev server stopped, or did not compile in 20 minutes; its container cp-sandbox-dev-abcd-ui stays"
real_repo
end_case

start_case "ui --stop removes the GUI's container"
FAKE_STATES='ui=running'
host_stubbed cmd_ui --name dev-abcd --stop
expect_status 0
expect "only that container goes" "$(calls 'docker rm' | paste -sd '|' -)" 'docker rm --force cp-sandbox-dev-abcd-ui'
expect_output "Stopped the GUI of sandbox dev-abcd: removed the container cp-sandbox-dev-abcd-ui."
host_stubbed cmd_ui --name dev-abcd --stop
expect_output "Sandbox dev-abcd runs no GUI here."
end_case

start_case "ui --dry-run prints the dev server's container, and starts nothing"
fake_state dev-abcd
sandbox ui --dry-run
expect_status 0
expect_output "[dry-run] Sandbox dev-abcd runs no API here; without --dry-run, ui stops here."
expect_output "[dry-run] docker run --detach --init --name cp-sandbox-dev-abcd-ui --label cp-sandbox=dev-abcd --label cp-sandbox-role=ui"
expect_output "[dry-run] Nothing was started."
[ -z "$(docker ps --all --quiet --filter label=cp-sandbox=dev-abcd)" ] || fail "a container was started"
end_case

start_case "services leaves an API that runs on the same jar and settings, and starts one that does not again"
fake_services_state dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd
CONFIG_LABEL=$(calls 'docker run --detach --init' | sed -E 's/.*--label cp-sandbox-config=([0-9a-f]{12}) .*/\1/')
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_VOLUME=1 FAKE_DB_STATE=running FAKE_API_STATE=running FAKE_API_CONFIG=$CONFIG_LABEL
host_stubbed cmd_services --name dev-abcd
expect_status 0
expect "nothing is started or removed, and the configuration runs again" \
  "$(counted 'docker run --detach')$(counted 'docker rm')$(counted 'docker volume create') $(counted 'services-configure')" '000 1'
expect_output "The API container cp-sandbox-dev-abcd-api is running already, on the same jar and settings."
expect_output "The database container cp-sandbox-dev-abcd-db is running already."
printf 'changed\n' >>"$STATE/dev-abcd/api.properties"
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_VOLUME=1 FAKE_DB_STATE=running FAKE_API_STATE=running FAKE_API_CONFIG=$CONFIG_LABEL
host_stubbed cmd_services --name dev-abcd
real_repo
expect "settings that changed start the API again" \
  "$(in_order 'docker rm --force cp-sandbox-dev-abcd-api' 'docker run --detach --init --name cp-sandbox-dev-abcd-api')" yes
end_case

start_case "services builds the jars when one is missing, and the API's image from its own context"
fake_services_state dev-abcd
fake_repo
rm -f "${FAKE_REPO:?}/${JWT_GENERATOR_JAR:?}"
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_NO_API_IMAGE=1
host_stubbed cmd_services --name dev-abcd
real_repo
expect "Gradle builds both jars from the checkout, as the calling user, with its home in the state root" \
  "$(counted "docker run --rm --user $(id -u):$(id -g) --volume $FAKE_REPO:$FAKE_REPO --volume $STATE/.gradle-home:$STATE/.gradle-home --env HOME=$STATE/.gradle-home --env GRADLE_USER_HOME=$STATE/.gradle-home --env GRADLE_OPTS=-Duser.home=$STATE/.gradle-home --workdir $FAKE_REPO eclipse-temurin:8-jdk ./gradlew --no-daemon --quiet :api:bootRepackage :jwt-generator:shadowJar") $([ -d "$STATE/.gradle-home" ] && echo made)" \
  '1 made'
expect "the image's context holds its Dockerfile, the entrypoints, the node scripts' requirements and pipe-common" \
  "$(calls 'context: ')" 'context: api-entrypoint.sh api.Dockerfile pipe-common requirements.txt service-entrypoint.sh tools-entrypoint.sh'
expect "and its tag is the hash of what it is built from" \
  "$(calls 'docker build -f' | grep -cE -- '-t cp-sandbox-api:[0-9a-f]{12} ' || true)" 1
expect "the image installs OpenSSH's client, which the API logs in to a run's node with" \
  "$(sed -n '/apt-get install/,/rm -rf/p' "$SKILL_DIR/docker/api.Dockerfile" | grep -cw openssh-client || true)" 1
end_case

start_case "services needs a tunnel that runs, and touches nothing without one"
fake_services_state dev-abcd
host_stubbed cmd_services --name dev-abcd
expect_status 1
expect_output "sandbox dev-abcd has no tunnel running: run sandbox.sh connect first"
expect "no tools call, no change on Docker" "$(calls tools)$(calls 'docker run')$(calls 'docker volume create')" ''
end_case

start_case "services shows the API's log, and stops, when the API does not answer"
fake_services_state dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_TOOLS_FAILS_ON=services-wait
host_stubbed cmd_services --name dev-abcd
real_repo
expect_status 1
expect "the log is shown, and nothing is configured" \
  "$(counted 'docker logs --tail 40 cp-sandbox-dev-abcd-api') $(counted services-configure) $(counted 'docker exec -i')" '1 0 0'
expect_output "the API did not start; its container stays, for its log"
end_case

start_case "services --stop removes the API and database containers, and keeps the volume and the network"
FAKE_API_STATE=running FAKE_DB_STATE=exited
host_stubbed cmd_services --name dev-abcd --stop
expect_status 0
expect "both containers go, nothing else" \
  "$(calls 'docker rm' | paste -sd '|' -) $(counted 'docker volume rm')$(counted 'docker network rm')" \
  'docker rm --force cp-sandbox-dev-abcd-api|docker rm --force cp-sandbox-dev-abcd-db 00'
expect_output "Stopped the services of sandbox dev-abcd: removed cp-sandbox-dev-abcd-api, cp-sandbox-dev-abcd-db. Its volumes stay, for down to remove."
host_stubbed cmd_services --name dev-abcd --stop
expect_output "Sandbox dev-abcd runs no services here."
end_case

start_case "services --stop removes the optional services too, each before what it uses"
FAKE_API_STATE=running FAKE_DB_STATE=running
FAKE_STATES='billing=running search=exited monitoring=running elasticsearch=running'
host_stubbed cmd_services --name dev-abcd --stop
expect_status 0
expect "the services, then the API, then the Elasticsearch and the database" "$(calls 'docker rm' | paste -sd '|' -)" \
  'docker rm --force cp-sandbox-dev-abcd-billing|docker rm --force cp-sandbox-dev-abcd-search|docker rm --force cp-sandbox-dev-abcd-monitoring|docker rm --force cp-sandbox-dev-abcd-api|docker rm --force cp-sandbox-dev-abcd-elasticsearch|docker rm --force cp-sandbox-dev-abcd-db'
expect "and no volume" "$(counted 'docker volume rm')" 0
end_case

start_case "services --dry-run --with all prints the Elasticsearch and the services, and starts nothing"
fake_services_state dev-abcd
sandbox services --dry-run --with all
expect_status 0
expect_output "write $STATE/dev-abcd/billing.properties: billing-report-agent/src/main/resources/application.properties"
expect_output "write $STATE/dev-abcd/search.properties: elasticsearch-agent/src/main/resources/application.properties"
expect_output "write $STATE/dev-abcd/monitoring.properties: monitoring-service/src/main/resources/application.properties"
expect_output "[dry-run] docker run --detach --platform linux/amd64 --name cp-sandbox-dev-abcd-elasticsearch --label cp-sandbox=dev-abcd --label cp-sandbox-role=elasticsearch --network cp-sandbox-dev-abcd --network-alias elasticsearch --volume cp-sandbox-dev-abcd-elasticsearch:/usr/share/elasticsearch/data --env discovery.type=single-node --env xpack.ml.enabled=false"
expect_output " elasticsearch:$(sed -n 's/^FROM elasticsearch://p' "$REPO_ROOT/$ELASTICSEARCH_DOCKERFILE")"
expect_output "[dry-run] curl --request POST https://api:9999/pipeline/restapi/preferences --data '[{\"name\":\"search.elastic.scheme\",\"value\":\"http\",\"visible\":true}"
for service in billing search monitoring; do
  expect_output "[dry-run] docker run --detach --init --name cp-sandbox-dev-abcd-$service --label cp-sandbox=dev-abcd --label cp-sandbox-role=$service --label cp-sandbox-config=dry-run --network cp-sandbox-dev-abcd --network-alias $service --volume $STATE/dev-abcd:$STATE/dev-abcd:ro --env SANDBOX_DIR=$STATE/dev-abcd"
  expect_output "--entrypoint /usr/local/bin/service-entrypoint.sh cp-sandbox-api:"
  expect_output "java -Xmx512m -jar $STATE/dev-abcd/$service.jar --spring.config.location=file:$STATE/dev-abcd/$service.properties"
done
expect_no_output "$SECRET"
[ -z "$(docker ps --all --quiet --filter label=cp-sandbox=dev-abcd)" ] || fail "a container was started"
if docker volume inspect cp-sandbox-dev-abcd-elasticsearch >/dev/null 2>&1; then fail "the Elasticsearch volume was created"; fi
for service in billing search monitoring; do
  [ ! -e "$STATE/dev-abcd/$service.properties" ] || fail "the $service settings were written"
done
end_case

start_case "services --with starts the Elasticsearch with the database, and the services once the API is configured"
fake_services_state dev-abcd
fake_service_settings dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd --with monitoring,billing,search
real_repo
expect_status 0
expect "the steps, in order" "$(in_order "in-tools.sh services-prepare --name dev-abcd --docker-subnets  --with billing,search,monitoring|" \
  "docker run --detach --name cp-sandbox-dev-abcd-db" "docker volume create --label cp-sandbox=dev-abcd cp-sandbox-dev-abcd-elasticsearch" \
  "docker run --detach --platform linux/amd64 --name cp-sandbox-dev-abcd-elasticsearch" \
  "docker run --detach --init --name cp-sandbox-dev-abcd-api" "in-tools.sh services-configure --name dev-abcd --with billing,search,monitoring|sandbox=dev-abcd|" \
  "docker exec cp-sandbox-dev-abcd-elasticsearch curl --silent --fail" \
  "docker run --detach --init --name cp-sandbox-dev-abcd-billing" "docker logs cp-sandbox-dev-abcd-billing" \
  "docker run --detach --init --name cp-sandbox-dev-abcd-search" "docker logs cp-sandbox-dev-abcd-search" \
  "docker run --detach --init --name cp-sandbox-dev-abcd-monitoring" "docker logs cp-sandbox-dev-abcd-monitoring")" yes
expect "the services run copies of their jars, from the local state" \
  "$(cat "$STATE/dev-abcd/billing.jar" "$STATE/dev-abcd/search.jar" "$STATE/dev-abcd/monitoring.jar" | paste -sd '|' -)" \
  'fake billing-report-agent/build/libs/billing-report-agent.jar|fake elasticsearch-agent/build/libs/elasticsearch-agent.jar|fake monitoring-service/build/libs/monitoring-service.jar'
expect "each is labelled with what it started from" \
  "$(calls 'docker run --detach --init --name cp-sandbox-dev-abcd-' | grep -cE -- '--label cp-sandbox-role=(billing|search|monitoring) --label cp-sandbox-config=[0-9a-f]{12} ' || true)" 3
expect "and gets no config, no ~/.aws and no checkout: no AWS credentials" \
  "$(calls 'docker run --detach --init --name cp-sandbox-dev-abcd-' | grep -E -- '-(billing|search|monitoring) ' | grep -cF -e '--env-file' -e '/.aws' -e "$FAKE_REPO" || true)" 0
expect "only the monitoring service gets the kubeconfig" \
  "$(calls 'docker run --detach --init --name cp-sandbox-dev-abcd-' | grep -F -- "--env KUBECONFIG=$STATE/dev-abcd/kubeconfig.secret" | sed -E 's/.*--name ([^ ]+) .*/\1/')" \
  cp-sandbox-dev-abcd-monitoring
expect "the Elasticsearch publishes nothing on the workstation" "$(calls 'cp-sandbox-dev-abcd-elasticsearch' | grep -c -e ' -p ' -e '--publish' || true)" 0
expect_output "The billing service runs, in cp-sandbox-dev-abcd-billing."
expect_output "The monitoring service runs, in cp-sandbox-dev-abcd-monitoring."
end_case

start_case "services --with leaves the services that run on the same jar and settings, and starts one that changed again"
fake_services_state dev-abcd
fake_service_settings dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd --with search
CONFIG_LABEL=$(calls 'docker run --detach --init --name cp-sandbox-dev-abcd-search' | sed -E 's/.*--label cp-sandbox-config=([0-9a-f]{12}) .*/\1/')
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_VOLUME=1 FAKE_STATES='search=running elasticsearch=running'
FAKE_CONFIGS="search=$CONFIG_LABEL"
host_stubbed cmd_services --name dev-abcd --with search
expect_status 0
expect "the Elasticsearch and the search service stay" \
  "$(counted 'docker run --detach --platform')$(counted 'docker run --detach --init --name cp-sandbox-dev-abcd-search')$(counted 'docker rm')" '000'
expect_output "The search container cp-sandbox-dev-abcd-search is running already, on the same jar and settings."
expect_output "The Elasticsearch container cp-sandbox-dev-abcd-elasticsearch is running already."
printf 'changed\n' >>"$STATE/dev-abcd/search.properties"
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_VOLUME=1 FAKE_STATES='search=running elasticsearch=running'
FAKE_CONFIGS="search=$CONFIG_LABEL"
host_stubbed cmd_services --name dev-abcd --with search
expect "settings that changed start it again" \
  "$(in_order 'docker rm --force cp-sandbox-dev-abcd-search' 'docker run --detach --init --name cp-sandbox-dev-abcd-search')" yes
CONFIG_LABEL=$(calls 'docker run --detach --init --name cp-sandbox-dev-abcd-search' | sed -E 's/.*--label cp-sandbox-config=([0-9a-f]{12}) .*/\1/')
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_VOLUME=1 FAKE_STATES='search=running elasticsearch=running'
FAKE_CONFIGS="search=$CONFIG_LABEL"
FAKE_SERVICE_LOG='[ERROR] 2026-10-14 11:52:09.205 [main] SpringApplication - Application run failed'
host_stubbed cmd_services --name dev-abcd --with search
real_repo
expect "one that runs on the same jar and settings, but failed to start, starts again" \
  "$(in_order 'docker logs cp-sandbox-dev-abcd-search' 'docker rm --force cp-sandbox-dev-abcd-search' 'docker run --detach --init --name cp-sandbox-dev-abcd-search')" yes
expect_output "Starting the search container cp-sandbox-dev-abcd-search again: its log does not say it has started."
end_case

start_case "services --with builds the services' jars when one is missing"
fake_services_state dev-abcd
fake_service_settings dev-abcd
fake_repo
rm -f "$FAKE_REPO/$(service_jar search)"
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd --with search,billing
real_repo
expect "Gradle builds the API's jars and the bootJar of each service asked for" \
  "$(calls 'eclipse-temurin:8-jdk ./gradlew' | sed 's/.*eclipse-temurin:8-jdk //')" \
  './gradlew --no-daemon --quiet :api:bootRepackage :jwt-generator:shadowJar :billing-report-agent:bootJar :elasticsearch-agent:bootJar'
end_case

start_case "services --with stops, with the log, when a service stops before it has started"
fake_services_state dev-abcd
fake_service_settings dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_STATES='billing=exited'
FAKE_SERVICE_LOG="Caused by: java.net.UnknownHostException: elasticsearch, with a token eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJQSVBFX0FETUlOIn0.c2lnbmF0dXJl"
FAKE_LOG='the end of the billing log'
host_stubbed cmd_services --name dev-abcd --with billing,monitoring
real_repo
expect_status 1
expect "the log is shown, and the next service is not started" \
  "$(counted 'docker logs --tail 40 cp-sandbox-dev-abcd-billing') $(counted 'docker run --detach --init --name cp-sandbox-dev-abcd-monitoring')" '1 0'
expect_output "the billing service did not start; its container stays, for its log: sandbox.sh services --log billing"
expect_output "  | the end of the billing log"
end_case

start_case "services --with stops at once when a service's log says it failed to start, though it runs on, and names the causes"
fake_services_state dev-abcd
fake_service_settings dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_STATES='search=running'
FAKE_SERVICE_LOG=$(printf '%s\n' '[ERROR] 2026-10-14 11:52:09.205 [main] SpringApplication - Application run failed' \
  'Caused by: com.epam.pipeline.exception.PipelineResponseIOException: java.net.UnknownHostException: api' \
  '	at okhttp3.RealCall.execute(RealCall.java:77)' 'Caused by: java.net.UnknownHostException: api' \
  'Caused by: java.net.UnknownHostException: api' 'Caused by: a token eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJQSVBFX0FETUlOIn0.c2lnbmF0dXJl')
SERVICE_WAIT_SECONDS=600
host_stubbed cmd_services --name dev-abcd --with search
SERVICE_WAIT_SECONDS=300
real_repo
expect_status 1
expect "its log is read once, and the end of it shown" \
  "$(counted 'docker logs cp-sandbox-dev-abcd-search') $(counted 'docker logs --tail 40 cp-sandbox-dev-abcd-search')" '1 1'
expect_output "  | Caused by: java.net.UnknownHostException: api"
expect_output "  | Caused by: a token <token>"
expect "each cause once" "$(printf '%s\n' "$OUTPUT" | grep -c '| Caused by: java.net.UnknownHostException: api$' || true)" 1
expect_no_output "eyJzdWIi"
expect_output "the search service did not start; its container stays, for its log: sandbox.sh services --log search"
end_case

start_case "services --with stops, with its log, when the Elasticsearch does not start"
fake_services_state dev-abcd
fake_service_settings dev-abcd
fake_repo
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1 FAKE_ES_FAILS=1 FAKE_STATES='elasticsearch=exited'
host_stubbed cmd_services --name dev-abcd --with search
real_repo
expect_status 1
expect "its log is shown, and no service is started" \
  "$(counted 'docker logs --tail 40 cp-sandbox-dev-abcd-elasticsearch') $(counted 'docker run --detach --init --name cp-sandbox-dev-abcd-search')" '1 0'
expect_output "Elasticsearch did not start in cp-sandbox-dev-abcd-elasticsearch"
end_case

start_case "services --with refuses what is not a service, and does nothing"
fake_services_state dev-abcd
FAKE_TUNNEL_STATE=running FAKE_NETWORK=1
host_stubbed cmd_services --name dev-abcd --with search,mail
expect_status 1
expect_output "--with: no service mail; the services are billing search monitoring, or all"
expect "no tools call, no change on Docker" "$(calls tools)$(calls 'docker run')" ''
host_stubbed cmd_services --name dev-abcd --with ,
expect_status 1
expect_output "--with names no service"
end_case

start_case "services --log <container> shows that container's log, blanked out the same way"
FAKE_STATES='search=running'
FAKE_LOG="GET run/7 Authorization: Bearer eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJQSVBFX0FETUlOIn0.c2lnbmF0dXJl done"
host_stubbed cmd_services --name dev-abcd --log search --tail 30
expect_status 0
expect "it reads the search container's log" "$(counted 'docker logs --tail 30 cp-sandbox-dev-abcd-search')" 1
expect_output "Authorization: Bearer <token> done"
expect_no_output "eyJzdWIi"
host_stubbed cmd_services --name dev-abcd --log billing
expect_status 1
expect_output "sandbox dev-abcd runs no billing container here"
host_stubbed cmd_services --name dev-abcd --log mail
expect_status 1
expect_output "services: unknown option mail"
end_case

start_case "down --yes removes the API's container before the instances go, so that it starts no node"
FAKE_OBJECTS=$(printf '%s\n' 'dev-abcd container cp-sandbox-dev-abcd-api running' 'dev-abcd container cp-sandbox-dev-abcd-db running')
FAKE_API_STATE=running
host_stubbed host_down --name dev-abcd --yes
expect_status 0
expect "the API goes first, then down, then the rest" "$(in_order 'docker rm --force cp-sandbox-dev-abcd-api' \
  'in-tools.sh down --name dev-abcd --yes' 'docker rm --force cp-sandbox-dev-abcd-db')" yes
expect_output "Removed the container cp-sandbox-dev-abcd-api first, so that it starts no node."
FAKE_OBJECTS='dev-abcd container cp-sandbox-dev-abcd-api running'
FAKE_API_STATE=running
host_stubbed host_down --name dev-abcd
expect "without --yes, the API stays" "$(calls 'docker rm')" ''
end_case

start_case "down --yes removes the sandbox's volume too, after its containers"
FAKE_OBJECTS=$(printf '%s\n' 'dev-abcd container cp-sandbox-dev-abcd-db running' 'dev-abcd network cp-sandbox-dev-abcd -' 'dev-abcd volume cp-sandbox-dev-abcd-db -')
host_stubbed host_down --name dev-abcd --yes
expect_status 0
expect "docker lists the volumes by the sandbox's label, for down and once it has succeeded" "$(counted 'docker volume ls --filter label=cp-sandbox=dev-abcd --format')" 2
expect "the container goes first, then the network and the volume" "$(in_order 'docker rm --force cp-sandbox-dev-abcd-db' \
  'docker network rm cp-sandbox-dev-abcd' 'docker volume rm cp-sandbox-dev-abcd-db')" yes
expect_output "Removed the volume cp-sandbox-dev-abcd-db."
end_case

start_case "the host lists, reads and removes the Docker objects labelled for a sandbox, on real Docker"
mkdir -p "$STATE"
tools_image
REAL_NETWORK=$(sandbox_network "$NETWORK_SANDBOX")
REAL_CONTAINER=$(tunnel_container "$NETWORK_SANDBOX")
REAL_VOLUME=$(service_volume "$NETWORK_SANDBOX" db)
docker network create --label "cp-sandbox=$NETWORK_SANDBOX" "$REAL_NETWORK" >/dev/null
docker volume create --label "cp-sandbox=$NETWORK_SANDBOX" "$REAL_VOLUME" >/dev/null
docker create --label "cp-sandbox=$NETWORK_SANDBOX" --label cp-sandbox-role=tunnel \
  --name "$REAL_CONTAINER" --network "$REAL_NETWORK" --volume "$REAL_VOLUME:/data" "$TOOLS_IMAGE" true >/dev/null
OUTPUT=
expect "docker_objects lists the sandbox's container, then its network and its volume" \
  "$(docker_objects "$NETWORK_SANDBOX" | paste -s -d '|' -)" \
  "$NETWORK_SANDBOX container $REAL_CONTAINER created|$NETWORK_SANDBOX network $REAL_NETWORK -|$NETWORK_SANDBOX volume $REAL_VOLUME -"
expect "docker_objects lists nothing of another sandbox" "$(docker_objects "$NETWORK_SANDBOX-x")" ''
expect "tunnel_state reads the tunnel container's state" "$(tunnel_state "$NETWORK_SANDBOX")" created
sandbox status --dry-run
expect_status 0
expect_output "$REAL_CONTAINER"
expect_output "$REAL_NETWORK"
OUTPUT=$(remove_docker_objects "$(docker_objects "$NETWORK_SANDBOX")" 2>&1) || fail "remove_docker_objects failed"
expect_output "Removed the container $REAL_CONTAINER."
expect_output "Removed the network $REAL_NETWORK."
expect_output "Removed the volume $REAL_VOLUME."
expect "nothing is left" "$(docker_objects "$NETWORK_SANDBOX")" ''
end_case

start_case "the host picks a sandbox as the tools image does"
fake_state dev-abcd
host_stubbed host_select_sandbox ''
expect_status 0
expect "the only local sandbox" "$OUTPUT" dev-abcd
host_stubbed host_select_sandbox dev-other
expect "the one named" "$STATUS $OUTPUT" '0 dev-other'
fake_state dev-efgh
host_stubbed host_select_sandbox ''
expect_status 1
expect_output "several local sandboxes (dev-abcd dev-efgh); pass --name <name>"
host_stubbed host_select_sandbox Bad_Name
expect_status 1
expect_output "invalid sandbox name 'Bad_Name'"
rm -rf "${STATE:?}"/dev-*
host_stubbed host_select_sandbox ''
expect_status 1
expect_output "no local sandbox in $STATE; pass --name <name>"
end_case

start_case "tools.sh --sandbox runs the command on the sandbox's network, with its kubeconfig"
mkdir -p "$STATE"
docker network create "$(sandbox_network "$NETWORK_SANDBOX")" >/dev/null
# shellcheck disable=SC2016 # expanded by the shell in the tools image
tools --sandbox "$NETWORK_SANDBOX" bash -c 'echo "kubeconfig:$KUBECONFIG"; cat /etc/resolv.conf'
expect_status 0
expect_output "kubeconfig:$STATE/$NETWORK_SANDBOX/kubeconfig.secret"
# Docker's own DNS server, which only a network of the user's own has.
expect_output "nameserver 127.0.0.11"
docker network rm "$(sandbox_network "$NETWORK_SANDBOX")" >/dev/null
tools --sandbox "$NETWORK_SANDBOX" true
expect_status 1
expect_output "sandbox $NETWORK_SANDBOX has no Docker network here: run sandbox.sh connect first"
end_case

start_case "an unknown command or option is refused"
sandbox launch
expect_status 1
sandbox down --force --dry-run
expect_status 1
sandbox connect --force --dry-run
expect_status 1
end_case

for test in in-tools-test.sh check-test.sh ami-test.sh connect-test.sh services-test.sh; do
  start_case "$test: the libraries and the commands, in the tools image"
  mkdir -p "$STATE"
  if OUTPUT=$(CP_SANDBOX_ENV_FILE=$CONFIG CP_SANDBOX_STATE_DIR=$STATE \
    bash "$SCRIPTS_DIR/tools.sh" bash "$TEST_DIR/$test" 2>&1); then
    printf '%s\n' "$OUTPUT" | sed 's/^/  /'
  else
    fail "$test failed"
  fi
  end_case
done

start_case "host-test.sh: the host scripts, as root in a container of their own"
tools_image
# The network plugin manifest that up sends to the master, as commands/up.sh names it.
NETWORK_MANIFEST=$REPO_ROOT/$(sed -n 's/^NETWORK_MANIFEST=//p' "$SCRIPTS_DIR/commands/up.sh")
OUTPUT=
# Docker would mount a missing file as an empty directory, made in the checkout.
if [ ! -f "$NETWORK_MANIFEST" ]; then
  fail "no network plugin manifest at $NETWORK_MANIFEST, the path NETWORK_MANIFEST gives in commands/up.sh"
elif OUTPUT=$(docker run --rm --user 0:0 --entrypoint bash --volume "$SKILL_DIR:$SKILL_DIR:ro" \
  --volume "$NETWORK_MANIFEST:$NETWORK_MANIFEST:ro" --env "DEPLOY_NETWORK_MANIFEST=$NETWORK_MANIFEST" \
  "$TOOLS_IMAGE" "$TEST_DIR/host-test.sh" 2>&1); then
  printf '%s\n' "$OUTPUT" | sed 's/^/  /'
else
  fail "host-test.sh failed"
fi
end_case

start_case "api-entrypoint-test.sh: the entrypoints of the API's image, as root in a container of its own"
tools_image
OUTPUT=
if OUTPUT=$(docker run --rm --user 0:0 --entrypoint bash --volume "$SKILL_DIR:$SKILL_DIR:ro" \
  "$TOOLS_IMAGE" "$TEST_DIR/api-entrypoint-test.sh" 2>&1); then
  printf '%s\n' "$OUTPUT" | sed 's/^/  /'
else
  fail "api-entrypoint-test.sh failed"
fi
end_case

start_case "services-host-test.sh: the master's side of services, as root in a container of its own"
OUTPUT=
mkdir -p "$REGISTRY_SETTINGS"
if OUTPUT=$(docker run --rm --user 0:0 --volume "$SKILL_DIR:$SKILL_DIR:ro" \
  --volume "$REPO_ROOT/$DEPLOY_REGISTRY_CONFIG:$REPO_ROOT/$DEPLOY_REGISTRY_CONFIG:ro" \
  --env "DEPLOY_REGISTRY_CONFIG=$REPO_ROOT/$DEPLOY_REGISTRY_CONFIG" \
  --volume "$REGISTRY_SETTINGS:/export" --env EXPORT_DIR=/export "$SERVICES_HOST_TEST_IMAGE" \
  bash "$TEST_DIR/services-host-test.sh" 2>&1); then
  printf '%s\n' "$OUTPUT" | sed 's/^/  /'
else
  fail "services-host-test.sh failed"
fi
end_case

start_case "the registry's settings, as install-services.sh leaves them, are ones the registry's image starts with: a login, deletes and notifications"
OUTPUT=
REGISTRY_IMAGE=$(sed -n 's/^REGISTRY_IMAGE=//p' "$SCRIPTS_DIR/host/install-services.sh")
tools_image
if [ ! -s "$REGISTRY_SETTINGS/config.yml" ]; then
  fail "services-host-test.sh left no registry settings in $REGISTRY_SETTINGS"
else
  docker network create "$REGISTRY_TEST_NETWORK" >/dev/null
  docker image inspect "$REGISTRY_IMAGE" >/dev/null 2>&1 || docker pull --quiet "$REGISTRY_IMAGE" >/dev/null
  docker run --detach --name "$REGISTRY_TEST_CONTAINER" --network "$REGISTRY_TEST_NETWORK" \
    --network-alias registry --volume "$REGISTRY_SETTINGS:/certs:ro" --env SSL_CERT_FILE=/certs/api.crt \
    "$REGISTRY_IMAGE" /certs/config.yml >/dev/null
  # registry_status [<curl args...>] <path>: the HTTP status the registry answers, from the network.
  registry_status() {
    docker run --rm --network "$REGISTRY_TEST_NETWORK" --entrypoint curl "$TOOLS_IMAGE" --silent \
      --insecure --output /dev/null --write-out '%{http_code}' "$@" 2>/dev/null || true
  }
  # A made-up password, which services-host-test.sh made.
  USER_ARGS=(--user "pipe_admin:$(cat "$REGISTRY_SETTINGS/password.secret")")
  for _ in $(seq 1 30); do
    [ "$(registry_status "${USER_ARGS[@]}" https://registry:5000/v2/)" != 200 ] || break
    sleep 1
  done
  expect "the registry answers its user" "$(registry_status "${USER_ARGS[@]}" https://registry:5000/v2/)" 200
  expect "and no one without the login" "$(registry_status https://registry:5000/v2/)" 401
  expect "a delete is taken, not refused as unsupported (405): the manifest is just not there" \
    "$(registry_status "${USER_ARGS[@]}" --request DELETE "https://registry:5000/v2/library/none/manifests/sha256:$(printf '0%.0s' $(seq 1 64))")" 404
  OUTPUT=$(docker logs "$REGISTRY_TEST_CONTAINER" 2>&1 || true)
  expect "it notifies the API at the URL given" \
    "$(printf '%s\n' "$OUTPUT" | grep -c 'configuring endpoint cp-sandbox-api (https://10.0.0.8:9999/pipeline/restapi/dockerRegistry/notify), timeout=10s' || true)" 1
  docker rm --force "$REGISTRY_TEST_CONTAINER" >/dev/null
  docker network rm "$REGISTRY_TEST_NETWORK" >/dev/null
fi
end_case

start_case "lambda/test: the reaper, with EC2 stubbed, and its template, with pytest"
lambda_check pytest python -m pytest -q -p no:cacheprovider test
end_case

start_case "lambda/template.yaml: cfn-lint"
lambda_check cfn-lint cfn-lint template.yaml
end_case

if [ "$FAILURES" != 0 ]; then
  printf '\n%s failure(s)\n' "$FAILURES"
  exit 1
fi
printf '\nAll passed.\n'
