#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Checks of the libraries and of up, extend and down, with AWS, Run Command and SSH replaced by the
# stubs in stubs.sh. Runs in the tools image, started by sandbox-test.sh with a throw-away state
# directory; it makes no AWS call.
set -euo pipefail
# As in-tools.sh, which the commands run under.
shopt -s inherit_errexit

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=stubs.sh
. "$TEST_DIR/stubs.sh"

test_instances_named_keeps_only_the_sandbox() {
  local output
  output=$(described \
    "$(instance i-own dev-abcd master running 2026-01-01T00:00:00Z)" \
    "$(instance i-other-owner dev-abcd master running 2026-01-01T00:00:00Z someone@example.com)" \
    "$(instance i-other-sandbox dev-abcdx master running 2026-01-01T00:00:00Z)" \
    '{"InstanceId": "i-untagged", "State": {"Name": "running"}}' \
    '{"InstanceId": "i-name-only", "State": {"Name": "running"},
      "Tags": [{"Key": "Name", "Value": "cp-sandbox-dev-abcd-master"}]}')
  check "instances_named keeps only instances tagged cp-sandbox=<name>" \
    "$(instances_named dev-abcd <<<"$output" | jq -c 'map(.id)')" '["i-own","i-other-owner"]'
  check "instances_named reads owner, role, state, IP, AMI and expiry" \
    "$(instances_named dev-abcd <<<"$output" | jq -c '.[0] | [.owner, .role, .state, .ip, .ami, .expires]')" \
    '["dev@example.com","master","running","10.0.0.9","ami-running","2026-01-01T00:00:00Z"]'
  check "instances_named reads empty output as no instance" \
    "$(instances_named dev-abcd </dev/null)" '[]'
  check "mine keeps the instances tagged with this owner" \
    "$(instances_named dev-abcd <<<"$output" | mine | jq -c 'map(.id)')" '["i-own"]'
  check "not_mine keeps the rest" \
    "$(instances_named dev-abcd <<<"$output" | not_mine | jq -c 'map(.id)')" '["i-other-owner"]'
}

test_helpers() {
  local name
  check "sandbox_tags carries the four sandbox tags and a Name" \
    "$(sandbox_tags dev-abcd worker 2026-01-01T00:00:00Z | jq -c 'from_entries')" \
    '{"Name":"cp-sandbox-dev-abcd-worker","cp-sandbox":"dev-abcd","cp-sandbox-owner":"dev@example.com","cp-sandbox-role":"worker","cp-sandbox-expires":"2026-01-01T00:00:00Z"}'
  check "an instance profile name is passed as Name" \
    "$(instance_profile_spec cp-sandbox)" '{"Name":"cp-sandbox"}'
  check "an instance profile ARN is passed as Arn" \
    "$(instance_profile_spec arn:aws:iam::123456789012:instance-profile/cp-sandbox)" \
    '{"Arn":"arn:aws:iam::123456789012:instance-profile/cp-sandbox"}'
  check "utc_after_hours counts from the time given" \
    "$(utc_after_hours 30 2026-01-01T00:00:00Z)" 2026-01-02T06:00:00Z

  name=$(CP_SANDBOX_OWNER=Jane_Doe@example.com new_sandbox_name)
  check "a new name is the owner's login and four random characters" \
    "$([[ $name =~ ^jane-doe-[a-z0-9]{4}$ ]] && echo yes || echo "no: $name")" yes
  name=$(CP_SANDBOX_OWNER=__@x new_sandbox_name)
  check "a login with nothing a name allows still gives a valid name" \
    "$([[ $name =~ $NAME_PATTERN ]] && echo yes || echo "no: $name")" yes
}

test_state() {
  local key before
  state_set dev-abcd instance_id i-1 region eu-west-1
  state_set dev-abcd private_ip 10.0.0.1 instance_id i-2
  check "state_set merges into state.json" \
    "$(jq -c . "$(state_file dev-abcd)")" '{"instance_id":"i-2","region":"eu-west-1","private_ip":"10.0.0.1"}'
  check "state_get reads one value" "$(state_get dev-abcd region)" eu-west-1
  check "state_get of a missing key is empty" "$(state_get dev-abcd nothing)" ''
  check "local_sandboxes lists the directories holding a state.json" "$(local_sandboxes)" dev-abcd
  check "the secrets are named for the deny-read rules: .pem and .secret" \
    "$(basename "$(ssh_key_file dev-abcd)") $(basename "$(kubeconfig_file dev-abcd)") $(basename "$(join_token_file dev-abcd)")" \
    'ssh-key.pem kubeconfig.secret kube-join-token.secret'

  ensure_ssh_key dev-abcd
  key=$(ssh_key_file dev-abcd)
  check "ensure_ssh_key writes a private key only its owner can read" "$(stat -c %a "$key")" 600
  check "the public key is the one the master's user data carries" \
    "$(master_user_data dev-abcd | sed -n 3p)" "  - $(cat "$key.pub")"
  before=$(cksum <"$key")
  ensure_ssh_key dev-abcd
  check "ensure_ssh_key keeps an existing key" "$(cksum <"$key")" "$before"

  state_set dev-abcd tunnel_container c-1 tunnel_network n-1
  state_unset dev-abcd tunnel_container absent
  check "state_unset removes the keys named, and only them" \
    "$(jq -c '[.tunnel_container, .tunnel_network, .region]' "$(state_file dev-abcd)")" \
    '[null,"n-1","eu-west-1"]'
  state_unset dev-none tunnel_container
  check "state_unset of a sandbox with no state writes none" \
    "$([[ -e $(state_dir dev-none) ]] && echo written || echo none)" none

  DRY_RUN=1
  state_set dev-abcd region us-east-1 2>/dev/null
  state_unset dev-abcd tunnel_network 2>/dev/null
  state_remove dev-abcd 2>/dev/null
  DRY_RUN=0
  check "state_set, state_unset and state_remove change nothing under --dry-run" \
    "$(state_get dev-abcd region) $(state_get dev-abcd tunnel_network)" 'eu-west-1 n-1'

  SANDBOX_DOCKER_OBJECTS=$(printf '%s\n' 'dev-abcd container cp-sandbox-dev-abcd-tunnel running' \
    '' 'dev-other container cp-sandbox-dev-other-tunnel exited' 'dev-abcd network cp-sandbox-dev-abcd')
  check "local_docker_objects keeps the named sandbox's objects, with - for a state not given" \
    "$(local_docker_objects dev-abcd | paste -sd '|')" \
    'dev-abcd container cp-sandbox-dev-abcd-tunnel running|dev-abcd network cp-sandbox-dev-abcd -'
  check "local_docker_objects without a name keeps every sandbox's" "$(local_docker_objects | wc -l)" 3
  SANDBOX_DOCKER_OBJECTS=
  check "local_docker_objects lists nothing when it was told of nothing" "$(local_docker_objects)" ''

  state_remove dev-abcd
  check "state_remove deletes the sandbox's directory" \
    "$([[ -e $(state_dir dev-abcd) ]] && echo present || echo gone)" gone
}

# shellcheck disable=SC2016 # the placeholders are the point
test_render_template() {
  local template=$STUB_DIR/template rendered
  printf 'a: ${CP_ONE}\nb: ${CP_TWO} and ${CP_ONE}\nc: $(seq 1 2) $KEEP & \\1\n' >"$template"
  rendered=$(render_template "$template" CP_ONE=10.244.0.0/16 'CP_TWO=x&y')
  check "render_template replaces every placeholder, and only them, literally" "$rendered" \
    "$(printf 'a: 10.244.0.0/16\nb: x&y and 10.244.0.0/16\nc: $(seq 1 2) $KEEP & \\1')"
  run_command render_template "$template" CP_ONE=1
  check "render_template fails on a placeholder left without a value" "$RUN_STATUS" 1
  check "render_template names the placeholder left" "$(logged '${CP_TWO} has no value')" 1
}

test_kubeadm_config_is_the_platform_one_plus_cert_sans() {
  local rendered original=$SANDBOX_REPO_ROOT/deploy/contents/k8s/kube-system/kubeadm-init-config.yaml
  rendered=$(render_kubeadm_config dev-abcd 10.0.0.7)
  check "the kubeadm config's certificate is for 127.0.0.1, localhost, the private IP and the tunnel's name" \
    "$(sed -n '/^  certSANs:/,/^[a-z]/p' <<<"$rendered" | sed -n 's/^    - //p' | paste -sd ' ')" \
    '127.0.0.1 localhost 10.0.0.7 cp-sandbox-dev-abcd-master'
  check "the kubeadm config takes deploy/'s install values" \
    "$(grep -E 'podSubnet|node-cidr-mask-size|^port' <<<"$rendered" | sed 's/^ *//' | paste -sd ' ')" \
    'podSubnet: 10.244.0.0/16 node-cidr-mask-size: "26" port: 10250'
  check "the sandbox's kubeadm config is deploy/'s apart from its comments and certSANs" \
    "$(sed -e '/^#/d' -e '/^apiServer:/,/^etcd:/{/^etcd:/!d;}' "$SCRIPTS_DIR/$KUBEADM_CONFIG_TEMPLATE")" \
    "$(cat "$original")"
}

test_network_manifest_renders() {
  local rendered
  rendered=$(render_template "$SANDBOX_REPO_ROOT/$NETWORK_MANIFEST" "CP_KUBE_FLANNEL_CIDR=$KUBE_POD_CIDR")
  check "the network manifest gets the pod network" \
    "$(count '"Network": "10.244.0.0/16"' - <<<"$rendered")" 1
}

test_ssm_run() {
  queue invocation '!InvocationDoesNotExist' '{"Status": "InProgress"}' \
    '{"Status": "Success", "StandardOutputContent": "done\n", "ResponseCode": 0}'
  run_command real_ssm_run i-1 300 "do it" 'echo done'
  check "ssm_run waits past a missing invocation and InProgress, then writes the output" \
    "$RUN_STATUS $(cat "$RUN_LOG")" '0 done'
  check "ssm_run sends the command to the instance, as root, with its timeout" \
    "$(called 'ssm send-command' | sed 's/ --query.*//')" \
    'ssm send-command --instance-ids i-1 --document-name AWS-RunShellScript --comment do it --timeout-seconds 600 --parameters {"commands":["echo done"],"executionTimeout":["300"]}'
  check "ssm_run polls the invocation it sent" "$(called 'ssm get-command-invocation' | sort -u)" \
    'ssm get-command-invocation --command-id cmd-1 --instance-id i-1 --output json'

  queue invocation '{"Status": "Failed", "StandardOutputContent": "half\n", "StandardErrorContent": "broke\n", "ResponseCode": 3}'
  run_command real_ssm_run i-1 300 "do it" false
  check "ssm_run fails when the command fails" "$RUN_STATUS" 1
  check "ssm_run shows the output, the error output and the status of a failed command" \
    "$(logged half) $(logged broke) $(logged 'do it on i-1: Failed, exit status 3')" '1 1 1'

  STUB_ERROR["ssm get-command-invocation"]=AccessDeniedException
  run_command real_ssm_run i-1 300 "do it" true
  check "ssm_run fails, rather than waiting, when it cannot read the invocation" \
    "$RUN_STATUS $(logged AccessDeniedException)" '1 1'
  reset_stubs
}

test_ssm_send_files() {
  local source=$STUB_DIR/source remote=$STUB_DIR/remote parts
  mkdir -p "$source/a" "$source/b"
  head -c 30000 /dev/urandom >"$source/a/big.bin"
  printf 'echo hi\n' >"$source/b/script.sh"
  # The commands, run here against a directory of the test's own.
  REMOTE_DIR=$remote SSM_CHUNK_CHARS=10000 SSM_RUN_LOCALLY=1
  run_command ssm_send_files i-1 "$source/a/big.bin" "$source/b/script.sh"
  REMOTE_DIR=/opt/cp-sandbox SSM_CHUNK_CHARS=16000 SSM_RUN_LOCALLY=0
  parts=$(count '# copy big.bin script.sh to' "$SSM_COMMANDS")
  check "ssm_send_files succeeds" "$RUN_STATUS" 0
  check "ssm_send_files splits a large payload across commands, each saying which part it is" \
    "$((parts > 1)) $(count "part $parts of $parts" "$SSM_COMMANDS")" '1 1'
  check "the files arrive whole, side by side" \
    "$(cmp -s "$source/a/big.bin" "$remote/big.bin" && cmp -s "$source/b/script.sh" "$remote/script.sh" && echo same)" same
  check "the files' directory is root's only" "$(stat -c %a "$remote")" 700
  check "the payload is cleaned up" "$([[ -e $remote/.payload ]] && echo left || echo gone)" gone
}

test_ssm_run_script() {
  local remote=$STUB_DIR/run-script logs=$STUB_DIR/run-script-logs
  run_command ssm_run_script i-1 600 "run it" install-master.sh /opt/cp-sandbox/a.yaml 'with space'
  check "ssm_run_script runs the copied script with its arguments, appending to a log of its own" \
    "$(count "; bash /opt/cp-sandbox/install-master.sh /opt/cp-sandbox/a.yaml 'with space' >>/var/log/cp-sandbox/install-master.log 2>&1;" "$SSM_COMMANDS")" 1

  # The commands, run here against directories of the test's own. The first run stops half-way
  # through a line, as one that Run Command's timeout ends may.
  mkdir -p "$remote"
  # shellcheck disable=SC2016 # for the scripts to expand
  printf 'printf "run %%s" "$1"\n[[ $1 == first ]] || echo\nexit "${2:-0}"\n' >"$remote/script.sh"
  # shellcheck disable=SC2016
  printf 'printf "line %%s\\n" 1 2 3 4 5\n' >"$remote/many.sh"
  REMOTE_DIR=$remote REMOTE_LOG_DIR=$logs SSM_RUN_LOCALLY=1
  run_command ssm_run_script i-1 600 "run it" script.sh first
  run_command ssm_run_script i-1 600 "run it" script.sh second 3
  check "ssm_run_script writes what this run logged, not the runs before it, and fails as the script does" \
    "$RUN_STATUS $(logged '== ') $(logged 'run second') $(logged 'run first')" '3 1 1 0'
  check "the log keeps every run, in a directory only its owner can read" \
    "$(grep -c '^run ' "$logs/script.log") $(stat -c %a "$logs")" '2 700'
  REMOTE_TAIL_LINES=3
  run_command ssm_run_script i-1 600 "run it" many.sh
  check "ssm_run_script writes only the last lines of a long run" "$(paste -sd ' ' "$RUN_LOG")" \
    'line 3 line 4 line 5'
  REMOTE_DIR=/opt/cp-sandbox REMOTE_LOG_DIR=/var/log/cp-sandbox REMOTE_TAIL_LINES=40 SSM_RUN_LOCALLY=0
}

test_up_launches_a_master_from_the_host_image() {
  local state
  reset_stubs
  run_command cmd_up --name dev-up
  check "up launches when the sandbox has no master" "$RUN_STATUS" 0
  check "up runs the preflight check before it looks for the image or launches anything" \
    "$(grep -E '^(sts get-caller-identity|ec2 describe-images --owners|ec2 run-instances)' "$CALLS" | cut -d' ' -f1-2 | paste -sd ' ')" \
    'sts get-caller-identity ec2 describe-images ec2 run-instances'
  check "up looks up this version's host image" \
    "$(called 'ec2 describe-images --owners self')" \
    "ec2 describe-images --owners self --filters Name=tag:cp-sandbox-ami,Values=$(host_version) Name=state,Values=pending,available --output json"
  check "up launches one master, from the host image" \
    "$(count '--image-id ami-host' "$CALLS")" 1
  check "the master is tagged as the sandbox's master" \
    "$(called 'ec2 run-instances' | count '{"Key":"cp-sandbox-role","Value":"master"}' -)" 1
  check "up builds no host image when there is one" "$(count prepare-host.sh "$SSM_COMMANDS")" 0
  check "up copies install-master.sh and its two files to the master" \
    "$(count '# copy install-master.sh kubeadm-init-config.yaml canal.yaml to /opt/cp-sandbox' "$SSM_COMMANDS")" 1
  check "up runs install-master.sh with the rendered files" \
    "$(count "bash /opt/cp-sandbox/install-master.sh /opt/cp-sandbox/kubeadm-init-config.yaml /opt/cp-sandbox/canal.yaml >>" "$SSM_COMMANDS")" 1
  check "up reads the credentials over SSH" "$(paste -sd ' ' "$SSH_COMMANDS")" \
    'sudo cat /etc/kubernetes/admin.conf sudo cat /etc/kubernetes/cp-sandbox/join-token sudo cat /etc/kubernetes/cp-sandbox/ca-cert-hash'

  state=$(state_file dev-up)
  check "up records the master, its IP, its expiry and the CA cert hash" \
    "$(jq -c '[.instance_id, .ami, .private_ip, (.expires | test("^\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ$")), .kube_ca_cert_hash, .kube_api_port, .kube_tunnel_host]' "$state")" \
    "[\"i-launched\",\"ami-host\",\"10.0.0.7\",true,\"$FAKE_HASH\",\"6443\",\"cp-sandbox-dev-up-master\"]"
  check "state.json holds neither the join token nor the kubeconfig" \
    "$(count "$FAKE_TOKEN" "$state") $(count 'kind: Config' "$state")" '0 0'
  check "the kubeconfig and the join token are files only their owner can read" \
    "$(stat -c %a "$(kubeconfig_file dev-up)") $(cat "$(join_token_file dev-up)") $(stat -c %a "$(join_token_file dev-up)")" \
    "600 $FAKE_TOKEN 600"
  check "the kubeconfig reaches the API server by the tunnel's name, with the master's credentials" \
    "$(kubectl config view --kubeconfig "$(kubeconfig_file dev-up)" --raw \
      -o 'jsonpath={.clusters[*].cluster.server} {.users[0].user.client-key-data}')" \
    'https://cp-sandbox-dev-up-master:6443 ZmFrZS1rZXk='
  check "up prints no credential" "$(logged "$FAKE_TOKEN")" 0
  state_remove dev-up
}

test_up_builds_the_host_image_first_when_there_is_none() {
  reset_stubs
  IMAGES='{"Images": []}'
  queue run-instances i-builder i-master
  run_command cmd_up --name dev-up
  check "up succeeds after building the host image" "$RUN_STATUS" 0
  check "up builds the image from a builder, then launches the master from the image" \
    "$(called 'ec2 run-instances' | sed -E 's/.*--image-id ([^ ]+).*"cp-sandbox-role","Value":"([^"]+)".*/\2:\1/' | paste -sd ' ')" \
    'ami-builder:ami-stock master:ami-built'
  check "up terminates the builder" "$(called 'ec2 terminate-instances' | sed 's/ --query.*//')" \
    'ec2 terminate-instances --instance-ids i-builder'
  check "up records the master from the new image" "$(state_get dev-up ami)" ami-built
  state_remove dev-up
}

test_up_picks_up_the_running_master() {
  reset_stubs
  DESCRIBED=$(described "$(instance i-running dev-up master running 2030-01-01T00:00:00Z)")
  run_command cmd_up --name dev-up
  check "up succeeds with the master already running" "$RUN_STATUS" 0
  check "up launches nothing when the master is running" "$(called 'ec2 run-instances')" ''
  check "up needs no host image, and no preflight check, for a master that runs" \
    "$(called 'ec2 describe-images --owners')$(called 'sts get-caller-identity')" ''
  check "up installs the master again, which is safe to repeat" \
    "$(count '; bash /opt/cp-sandbox/install-master.sh' "$SSM_COMMANDS")" 1
  check "up records the running master, and keeps its expiry" \
    "$(jq -c '[.instance_id, .ami, .expires]' "$(state_file dev-up)")" \
    '["i-running","ami-running","2030-01-01T00:00:00Z"]'
  state_remove dev-up
}

test_up_launches_nothing_it_cannot_justify() {
  reset_stubs
  STUB_ERROR["ec2 describe-subnets"]=InvalidSubnetID.NotFound
  run_command cmd_up --name dev-up
  check "up fails when the preflight check fails" "$RUN_STATUS" 1
  check "up shows the failed item" "$(logged 'FAIL  CP_SANDBOX_SUBNET_ID subnet-0fake does not exist')" 1
  check "up launches nothing, and looks for no image, when the check fails" \
    "$(called 'ec2 run-instances')$(called 'ec2 describe-images')" ''
  check "a refused up leaves no local state" \
    "$([[ -e $(state_dir dev-up) ]] && echo present || echo none)" none

  reset_stubs
  DESCRIBED=$(described "$(instance i-stopped dev-up master stopped 2030-01-01T00:00:00Z)")
  run_command cmd_up --name dev-up
  check "up fails when the master is stopped" "$RUN_STATUS" 1
  check "up launches nothing next to a stopped master" "$(called 'ec2 run-instances')" ''

  reset_stubs
  DESCRIBE_FAILS=1
  run_command cmd_up --name dev-up
  check "up fails, with the AWS CLI's status, when it cannot list the sandbox's instances" \
    "$RUN_STATUS" 255
  check "up launches nothing when it cannot list the sandbox's instances" \
    "$(called 'ec2 run-instances')" ''

  reset_stubs
  DESCRIBED=$(described "$(instance i-theirs dev-up master running 2030-01-01T00:00:00Z someone@example.com)")
  run_command cmd_up --name dev-up
  check "up fails when another owner's instance has the name" "$RUN_STATUS" 1
  check "up launches nothing under another owner's name" "$(called 'ec2 run-instances')" ''
  check "up leaves no local state for a name it refused" \
    "$([[ -e $(state_dir dev-up) ]] && echo present || echo none)" none
  reset_stubs
}

test_up_says_what_to_do_when_ssm_never_reports_the_master() {
  reset_stubs
  STUB_OUTPUT["ssm describe-instance-information"]=ConnectionLost
  SSM_ONLINE_TIMEOUT_SECONDS=0
  run_command cmd_up --name dev-up
  SSM_ONLINE_TIMEOUT_SECONDS=900
  check "up fails when SSM never reports the master online, and says the master still runs" \
    "$RUN_STATUS $(logged 'The master is still running: run up again to keep waiting, or down to remove it.')" '1 1'
  check "up installs nothing on a master SSM cannot reach" "$(cat "$SSM_COMMANDS")" ''
  state_remove dev-up
  reset_stubs
}

test_up_fails_when_the_install_does() {
  reset_stubs
  SSM_FAILS_ON='install the sandbox master'
  run_command cmd_up --name dev-up
  check "up fails when install-master.sh fails" "$RUN_STATUS" 1
  check "up fetches no credentials from a master whose install failed" "$(cat "$SSH_COMMANDS")" ''
  state_remove dev-up
  reset_stubs
}

test_fetch_secret() {
  local file
  mkdir -p "$(state_dir dev-fetch)"
  file=$(kubeconfig_file dev-fetch)
  fetch_secret dev-fetch i-1 /etc/kubernetes/admin.conf "$file"
  check "fetch_secret writes what SSH read, readable by its owner only" \
    "$(stat -c %a "$file") $(head -n 1 "$file")" '600 apiVersion: v1'
  run_command fetch_secret dev-fetch i-1 /etc/kubernetes/nothing "$STUB_DIR/empty.secret"
  check "fetch_secret fails on an empty answer, and leaves no file" \
    "$RUN_STATUS $([[ -e $STUB_DIR/empty.secret ]] && echo left || echo none)" '1 none'
  check "fetch_secret leaves no partial file" \
    "$(find "$(state_dir dev-fetch)" "$STUB_DIR" -name '.fetch.*' | wc -l)" 0
  state_remove dev-fetch
}

test_extend_moves_every_expiry() {
  local new ahead
  reset_stubs
  DESCRIBED=$(described \
    "$(instance i-master dev-ext master running 2030-01-01T00:00:00Z)" \
    "$(instance i-worker dev-ext worker running 2030-01-01T02:00:00Z)" \
    "$(instance i-theirs dev-ext master running 2031-01-01T00:00:00Z someone@example.com)" \
    "$(instance i-foreign dev-other master running 2030-01-01T00:00:00Z)")
  run_command cmd_extend --name dev-ext --hours 5
  check "extend succeeds" "$RUN_STATUS" 0
  check "extend tags this owner's instances of the sandbox only, 5 hours after their latest expiry" \
    "$(called 'ec2 create-tags')" \
    'ec2 create-tags --resources i-master i-worker --tags Key=cp-sandbox-expires,Value=2030-01-01T07:00:00Z'

  DESCRIBED=$(described "$(instance i-master dev-ext master running 2020-01-01T00:00:00Z)")
  run_command cmd_extend --name dev-ext --hours 5
  new=$(called 'ec2 create-tags' | sed 's/.*Value=//')
  ahead=$(($(date -u -d "$new" +%s) - $(date -u +%s)))
  check "extend counts from now once the expiry has passed" \
    "$( ((ahead > 5 * 3600 - 60 && ahead <= 5 * 3600)) && echo yes || echo "no: $new")" yes
}

test_down_terminates_only_the_sandbox() {
  reset_stubs
  DESCRIBED=$(described \
    "$(instance i-master dev-down master running 2030-01-01T00:00:00Z)" \
    "$(instance i-worker dev-down worker running 2030-01-01T00:00:00Z)" \
    "$(instance i-theirs dev-down master running 2030-01-01T00:00:00Z someone@example.com)" \
    "$(instance i-foreign dev-downx master running 2030-01-01T00:00:00Z)")
  state_set dev-down instance_id i-master

  run_command cmd_down --name dev-down
  check "down without --yes terminates nothing" "$(called 'ec2 terminate-instances')" ''
  check "down without --yes keeps the local state" \
    "$([[ -f $(state_file dev-down) ]] && echo kept || echo gone)" kept
  check "down names another owner's instance as left alone" \
    "$(logged 'leave     i-theirs, tagged cp-sandbox-owner=someone@example.com')" 1

  run_command cmd_down --name dev-down --yes
  check "down --yes succeeds" "$RUN_STATUS" 0
  check "down --yes terminates this owner's instances of the sandbox only" \
    "$(called 'ec2 terminate-instances' | sed 's/ --query.*//')" \
    'ec2 terminate-instances --instance-ids i-master i-worker'
  check "down --yes waits for them" \
    "$(called 'ec2 wait')" 'ec2 wait instance-terminated --instance-ids i-master i-worker'
  check "down --yes removes the local state" \
    "$([[ -e $(state_dir dev-down) ]] && echo kept || echo gone)" gone
}

test_instances_named_keeps_only_the_sandbox
test_helpers
test_state
test_render_template
test_kubeadm_config_is_the_platform_one_plus_cert_sans
test_network_manifest_renders
test_ssm_run
test_ssm_send_files
test_ssm_run_script
test_up_launches_a_master_from_the_host_image
test_up_builds_the_host_image_first_when_there_is_none
test_up_picks_up_the_running_master
test_up_launches_nothing_it_cannot_justify
test_up_says_what_to_do_when_ssm_never_reports_the_master
test_up_fails_when_the_install_does
test_fetch_secret
test_extend_moves_every_expiry
test_down_terminates_only_the_sandbox
finish
