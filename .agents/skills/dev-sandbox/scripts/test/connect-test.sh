#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Checks of connect's steps in the tools image, of the tunnel container's process, and of what
# down and status say about the Docker objects sandbox.sh tells them of, with AWS, SSH and kubectl
# replaced by the stubs in stubs.sh. Runs in the tools image, started by sandbox-test.sh with a
# throw-away state directory; it makes no AWS call.
set -euo pipefail
# As in-tools.sh, which the commands run under.
shopt -s inherit_errexit

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=stubs.sh
. "$TEST_DIR/stubs.sh"

# The tunnel's SSH session, stubbed when TUNNEL_STUBBED is set: records the run, and fails at once,
# or, for a run queued as "long", after a session as long as one that counts as open.
eval "real_tunnel_ssh() $(declare -f tunnel_ssh | sed 1d)"
TUNNEL_STUBBED=0
TUNNEL_RUNS=$STUB_DIR/tunnel-runs
tunnel_ssh() {
  if ((TUNNEL_STUBBED == 0)); then
    real_tunnel_ssh "$@"
    return
  fi
  printf '%s\n' "$*" >>"$TUNNEL_RUNS"
  if [[ $(next_answer tunnel quick) == long ]]; then
    SECONDS=$((SECONDS + TUNNEL_QUICK_SECONDS))
  fi
  return 255
}

# up_state <name>: the local state up leaves for a master that is installed.
up_state() {
  state_set "$1" name "$1" region eu-west-1 instance_id i-master private_ip 10.0.0.7
  : >"$(ssh_key_file "$1")"
  admin_conf >"$(kubeconfig_file "$1")"
}

running_master() {   # running_master <name> [<state>]: describe-instances output with its master
  described "$(instance i-master "$1" master "${2:-running}" 2030-01-01T00:00:00Z)"
}

# tunnel_recorded <name>: what an earlier connect recorded of the tunnel, as connect-check does.
tunnel_recorded() {
  state_set "$1" tunnel_container "$(tunnel_container "$1")" tunnel_network "$(sandbox_network "$1")" \
    tunnel_kube_server https://old:6443 tunnel_api_address 10.0.0.1:9999 tunnel_api_target api:9999 \
    tunnel_socks old:1080
}

tunnel_keys() {   # tunnel_keys <name>: the tunnel's keys in the sandbox's state.json
  jq -r '[keys[] | select(startswith("tunnel_"))] | join(" ")' "$(state_file "$1")"
}

test_connect_prepare_accepts_the_running_master() {
  reset_stubs
  up_state dev-con
  DESCRIBED=$(running_master dev-con)
  run_command cmd_connect_prepare --name dev-con
  check "connect-prepare passes for the running master the local state names" \
    "$RUN_STATUS $(logged 'Sandbox dev-con: master i-master (10.0.0.7) is running.')" '0 1'
  check "connect-prepare looks the master up among the sandbox's instances" \
    "$(called 'ec2 describe-instances' | sed 's/ --output.*//')" \
    'ec2 describe-instances --filters Name=tag:cp-sandbox,Values=dev-con Name=instance-state-name,Values=pending,running,shutting-down,stopping,stopped'
  check "connect-prepare opens no session" "$(cat "$SSH_COMMANDS")" ''
  state_remove dev-con
}

test_connect_prepare_refuses_what_a_tunnel_cannot_use() {
  reset_stubs
  run_command cmd_connect_prepare
  check "connect-prepare needs --name" "$RUN_STATUS $(logged '--name <name> is required')" '1 1'

  run_command cmd_connect_prepare --name dev-none
  check "connect-prepare fails for a sandbox with no local state" \
    "$RUN_STATUS $(logged 'no local state for sandbox dev-none: run up first')" '1 1'

  up_state dev-con
  rm "$(kubeconfig_file dev-con)"
  DESCRIBED=$(running_master dev-con)
  run_command cmd_connect_prepare --name dev-con
  check "connect-prepare fails when up has fetched no kubeconfig" \
    "$RUN_STATUS $(logged 'sandbox dev-con has no kubeconfig.secret in its local state')" '1 1'
  check "and looks for no instance" "$(called 'ec2 describe-instances')" ''

  up_state dev-con
  DESCRIBED=$(running_master dev-con stopped)
  run_command cmd_connect_prepare --name dev-con
  check "connect-prepare fails when the master is stopped" \
    "$RUN_STATUS $(logged 'master i-master of sandbox dev-con is stopped, not running')" '1 1'

  DESCRIBED=$(described "$(instance i-other dev-con master running 2030-01-01T00:00:00Z)" \
    "$(instance i-master dev-con master running 2030-01-01T00:00:00Z someone@example.com)")
  run_command cmd_connect_prepare --name dev-con
  check "connect-prepare fails when the master the state names is gone, or is not this owner's" \
    "$RUN_STATUS $(logged 'master i-master of sandbox dev-con is gone from eu-west-1')" '1 1'

  DESCRIBE_FAILS=1
  run_command cmd_connect_prepare --name dev-con
  check "connect-prepare fails when it cannot list the sandbox's instances" "$RUN_STATUS" 255
  state_remove dev-con
  reset_stubs
}

test_tunnel_command() {
  local command
  reset_stubs
  up_state dev-con
  DRY_RUN=1
  command=$(tunnel_ssh dev-con i-master 10.0.0.7 2>&1)
  DRY_RUN=0
  check "the tunnel forwards the Kubernetes API from every address of its container, binds the API port on the master's private address, and serves SOCKS on every address of its container" \
    "$(count "-N -T -o ExitOnForwardFailure=yes -L 0.0.0.0:6443:127.0.0.1:6443 -R 10.0.0.7:9999:api:9999 -D 0.0.0.0:1080 ec2-user@i-master" - <<<"$command")" 1
  check "the tunnel uses the sandbox's key and known_hosts, through a Session Manager session in its region" \
    "$(count "-i $(ssh_key_file dev-con) -o IdentitiesOnly=yes -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=$(known_hosts_file dev-con) -o HostKeyAlias=i-master" - <<<"$command") $(count "'ProxyCommand=aws ssm start-session --region eu-west-1 --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p'" - <<<"$command")" \
    '1 1'

  DRY_RUN=1
  run_command cmd_tunnel --name dev-con
  DRY_RUN=0
  check "tunnel --dry-run prints the session, once, and opens none" \
    "$RUN_STATUS $(logged '[dry-run] ssh -i') $(logged 'Opening the tunnel')" '0 1 0'
  check "and freeing the API port on the master, which instance_ssh prints under --dry-run" \
    "$(cat "$SSH_COMMANDS")" "$(release_api_port_command 10.0.0.7)"
  state_remove dev-con
}

test_tunnel_reopens_and_gives_up() {
  reset_stubs
  up_state dev-con
  tunnel_recorded dev-con
  : >"$TUNNEL_RUNS"
  TUNNEL_STUBBED=1
  queue tunnel long long
  run_command cmd_tunnel --name dev-con
  TUNNEL_STUBBED=0
  check "a tunnel that gives up takes its record out of the local state, and keeps the rest" \
    "$(tunnel_keys dev-con)|$(state_get dev-con instance_id)" '|i-master'
  check "the tunnel opens again after a session ends, and a session that lasted resets the count of failures: 2 that lasted, then 5 that did not" \
    "$RUN_STATUS $(wc -l <"$TUNNEL_RUNS") $(logged 'The tunnel closed, ssh status 255. Opening it again in 0 seconds.')" \
    '1 7 6'
  check "the tunnel gives up after 5 sessions in a row that ended at once, and says why" \
    "$(logged 'the tunnel closed 5 times in a row within 60 seconds of opening, last with ssh status 255: giving up. sandbox.sh connect opens it again.')" 1
  check "the tunnel says what it opens, each time" \
    "$(logged 'Opening the tunnel to master i-master of sandbox dev-con: cp-sandbox-dev-con-master:6443 here to its Kubernetes API, its 10.0.0.7:9999 to api:9999 here, and a SOCKS proxy into its VPC at cp-sandbox-dev-con-master:1080 here.')" 7
  check "each session is the sandbox's master, at its private address" "$(sort -u "$TUNNEL_RUNS")" \
    'dev-con i-master 10.0.0.7'
  check "before each session, the tunnel frees the API port on the master's private address, over SSH" \
    "$(count "$(release_api_port_command 10.0.0.7)" "$SSH_COMMANDS") $(wc -l <"$SSH_COMMANDS")" '7 7'
  check "and says nothing of it when no session held the port" "$(logged 'Ended the SSH session')" 0
  state_remove dev-con
}

test_tunnel_frees_the_api_port() {
  reset_stubs
  up_state dev-con
  : >"$TUNNEL_RUNS"
  TUNNEL_STUBBED=1
  queue released '11776 11817'
  run_command cmd_tunnel --name dev-con
  check "the tunnel names the sessions it ended on the master, once, before the session that follows" \
    "$(logged 'Ended the SSH session(s) on the master that an earlier tunnel left listening on 10.0.0.7:9999: process(es) 11776 11817.') $(grep -n -m1 'Ended the SSH' "$RUN_LOG" | cut -d: -f1) $(grep -n -m1 'Opening the tunnel' "$RUN_LOG" | cut -d: -f1)" \
    '1 1 2'

  queue released '!' '!' '!' '!' '!'
  run_command cmd_tunnel --name dev-con
  TUNNEL_STUBBED=0
  check "the tunnel opens its sessions even when it cannot reach the master to free the port, and they count the failures" \
    "$RUN_STATUS $(wc -l <"$TUNNEL_RUNS") $(logged 'giving up')" '1 10 1'
  state_remove dev-con
}

# release_api_port_command, run here with sudo, ss and kill stubbed: ss answers as the master's
# does, and kill records what it is asked to end, and fails when told to.
test_release_api_port_command() {
  local bin output
  bin=$(mktemp -d)
  cat >"$bin/sudo" <<'EOF'
#!/bin/sh
exec "$@"
EOF
  cat >"$bin/ss" <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >>"$STUB_BIN/ss-args"
cat "$STUB_BIN/ss-output"
EOF
  cat >"$bin/kill" <<'EOF'
#!/bin/sh
printf '%s\n' "$*" >>"$STUB_BIN/killed"
[ ! -e "$STUB_BIN/kill-fails" ]
EOF
  chmod +x "$bin/sudo" "$bin/ss" "$bin/kill"
  # The tunnel's own forward, under either name OpenSSH gives the process (two listeners on one
  # address cannot be, but the names are what is checked); an sshd on another address, which the
  # tunnel's forward does not conflict with; and an address that starts with the private one.
  cat >"$bin/ss-output" <<'EOF'
LISTEN 0      128         10.0.0.7:9999      0.0.0.0:* users:(("sshd-session",pid=11817,fd=9))
LISTEN 0      128         10.0.0.7:9999      0.0.0.0:* users:(("sshd",pid=2001,fd=7),("sshd",pid=2000,fd=7))
LISTEN 0      128        127.0.0.1:9999      0.0.0.0:* users:(("sshd-session",pid=4000,fd=9))
LISTEN 0      128        10.0.0.77:9999      0.0.0.0:* users:(("sshd-session",pid=5000,fd=9))
LISTEN 0      128             [::]:9999         [::]:* users:(("python3",pid=3000,fd=3))
EOF
  output=$(STUB_BIN=$bin PATH=$bin:$PATH bash -c "$(release_api_port_command 10.0.0.7)")
  check "the release asks ss, as root, for the processes that listen on the API port" \
    "$(cat "$bin/ss-args")" '-Hltnp sport = :9999'
  check "the release ends every sshd session that listens there on the private address, and nothing else" \
    "$(cat "$bin/killed")|$output" '11817 2000 2001|11817 2000 2001'

  rm -f "$bin/killed"
  : >"$bin/kill-fails"
  output=$(STUB_BIN=$bin PATH=$bin:$PATH bash -c "$(release_api_port_command 10.0.0.7)") || output="status $?"
  check "the release names the sessions, and succeeds, when one is gone before kill reaches it" \
    "$(cat "$bin/killed")|$output" '11817 2000 2001|11817 2000 2001'

  : >"$bin/ss-output"
  rm -f "$bin/killed" "$bin/kill-fails"
  output=$(STUB_BIN=$bin PATH=$bin:$PATH bash -c "$(release_api_port_command 10.0.0.7)") || output="status $?"
  check "the release ends nothing, prints nothing and succeeds when nothing listens there" \
    "$([[ -e $bin/killed ]] && echo killed)|$output" '|'
  rm -rf "$bin"
}

test_connect_check_waits_for_the_tunnel_and_records_it() {
  local state
  reset_stubs
  up_state dev-con
  queue kubectl '!Unable to connect to the server: dial tcp: lookup cp-sandbox-dev-con-master: no such host' \
    '!Unable to connect to the server: EOF'
  run_command cmd_connect_check --name dev-con
  check "connect-check succeeds once the Kubernetes API answers through the tunnel" "$RUN_STATUS" 0
  check "connect-check asks for the nodes with the sandbox's kubeconfig until they come" \
    "$(wc -l <"$KUBE_CALLS") $(sort -u "$KUBE_CALLS")" \
    "3 --kubeconfig $(kubeconfig_file dev-con) --request-timeout=10s get nodes -o wide"
  check "connect-check then checks, over SSH, what the master listens on at the API port" \
    "$(cat "$SSH_COMMANDS")" "ss -Hltn 'sport = :9999'"
  state=$(state_file dev-con)
  check "connect-check then reaches the master's private address through the SOCKS proxy" \
    "$(cat "$SOCKS_CALLS")" \
    "--silent --insecure --output /dev/null --write-out %{http_code} --max-time 10 --socks5 cp-sandbox-dev-con-master:1080 https://10.0.0.7:6443/healthz"
  check "connect-check records the tunnel's container, network, its ends and its SOCKS proxy" \
    "$(jq -c '[.tunnel_container, .tunnel_network, .tunnel_kube_server, .tunnel_api_address, .tunnel_api_target, .tunnel_socks]' "$state")" \
    '["cp-sandbox-dev-con-tunnel","cp-sandbox-dev-con","https://cp-sandbox-dev-con-master:6443","10.0.0.7:9999","api:9999","cp-sandbox-dev-con-master:1080"]'
  check "connect-check shows the nodes, and how to reach them" \
    "$(logged '  ip-10-0-0-7   Ready    master') $(logged 'scripts/tools.sh --sandbox dev-con kubectl get nodes')" '1 1'
  check "connect-check shows where the platform's nodes reach the local API, and where the VPC is" \
    "$(logged '10.0.0.7:9999 reaches api:9999 on the network') $(logged 'socks5://cp-sandbox-dev-con-master:1080, connections made by the master')" '1 1'
  state_remove dev-con
}

test_connect_check_fails_on_a_tunnel_that_does_not_work() {
  reset_stubs
  up_state dev-con
  tunnel_recorded dev-con
  CONNECT_TIMEOUT_SECONDS=0
  queue kubectl '!Unable to connect to the server: dial tcp: lookup cp-sandbox-dev-con-master: no such host'
  run_command cmd_connect_check --name dev-con
  check "connect-check fails when the Kubernetes API never answers, with kubectl's error" \
    "$RUN_STATUS $(logged 'no such host') $(logged 'the Kubernetes API did not answer at https://cp-sandbox-dev-con-master:6443 through the tunnel')" \
    '1 1 1'
  check "and leaves nothing of the tunnel an earlier connect recorded" "$(tunnel_keys dev-con)" ''

  tunnel_recorded dev-con
  SS_LISTENING='LISTEN 0      128      127.0.0.1:9999      0.0.0.0:*'
  run_command cmd_connect_check --name dev-con
  check "connect-check fails when the master listens on the API port only on another address" \
    "$RUN_STATUS $(logged 'master i-master does not listen on 10.0.0.7:9999')" '1 1'
  check "and leaves nothing of the tunnel recorded" "$(tunnel_keys dev-con)" ''
  SS_LISTENING=
  run_command cmd_connect_check --name dev-con
  check "connect-check fails when nothing listens on the API port" \
    "$RUN_STATUS $(logged 'master i-master does not listen on 10.0.0.7:9999')" '1 1'
  SS_LISTENING='LISTEN 0      128       10.0.0.7:9999      0.0.0.0:*'
  tunnel_recorded dev-con
  queue socks 000
  run_command cmd_connect_check --name dev-con
  check "connect-check fails when the SOCKS proxy does not reach the master" \
    "$RUN_STATUS $(logged 'the SOCKS proxy at cp-sandbox-dev-con-master:1080 does not reach the master at 10.0.0.7:6443')" '1 1'
  check "and leaves nothing of the tunnel recorded" "$(tunnel_keys dev-con)" ''
  CONNECT_TIMEOUT_SECONDS=180
  state_remove dev-con
  reset_stubs
}

test_connect_stop() {
  reset_stubs
  up_state dev-con
  tunnel_recorded dev-con
  run_command cmd_connect_stop --name dev-con
  check "connect-stop takes the tunnel's record out of the local state, and keeps the rest" \
    "$RUN_STATUS $(tunnel_keys dev-con)|$(state_get dev-con instance_id)" '0 |i-master'
  run_command cmd_connect_stop --name dev-none
  check "connect-stop of a sandbox with no state succeeds, and writes none" \
    "$RUN_STATUS $([[ -e $(state_dir dev-none) ]] && echo written || echo none)" '0 none'
  state_remove dev-con
}

test_down_lists_the_docker_objects() {
  reset_stubs
  DESCRIBED=$(running_master dev-dock)
  state_set dev-dock instance_id i-master
  SANDBOX_DOCKER_OBJECTS=$(printf '%s\n' 'dev-dock container cp-sandbox-dev-dock-tunnel running' \
    'dev-dock network cp-sandbox-dev-dock -' 'dev-other container cp-sandbox-dev-other-tunnel running')
  run_command cmd_down --name dev-dock
  check "down lists the sandbox's container and network on Docker" \
    "$(logged "  remove    container cp-sandbox-dev-dock-tunnel (running), on this workstation's Docker") $(logged "  remove    network cp-sandbox-dev-dock, on this workstation's Docker")" \
    '1 1'
  check "down lists no other sandbox's Docker objects" "$(logged cp-sandbox-dev-other)" 0

  DESCRIBED='{"Reservations": []}'
  state_remove dev-dock
  run_command cmd_down --name dev-dock --yes
  check "down --yes with only Docker objects left goes on, for sandbox.sh to remove them" \
    "$RUN_STATUS $(logged 'nothing:') $(logged 'Sandbox dev-dock is removed.')" '0 0 1'

  SANDBOX_DOCKER_OBJECTS=
  run_command cmd_down --name dev-dock --yes
  check "down says when there is nothing anywhere" \
    "$(logged 'nothing: no instance or key pair tagged cp-sandbox=dev-dock with this owner, no local state, and nothing on Docker')" 1
  reset_stubs
}

test_status_lists_the_docker_objects() {
  reset_stubs
  SANDBOX_DOCKER_OBJECTS=$(printf '%s\n' 'dev-a container cp-sandbox-dev-a-tunnel running' \
    'dev-b network cp-sandbox-dev-b -')
  run_command cmd_status
  check "status lists every sandbox's containers and networks on Docker, with their state" \
    "$(grep -cE '^  dev-a +container +cp-sandbox-dev-a-tunnel +running$' "$RUN_LOG") $(grep -cE '^  dev-b +network +cp-sandbox-dev-b +-$' "$RUN_LOG")" \
    '1 1'
  reset_stubs
}

test_connect_prepare_accepts_the_running_master
test_connect_prepare_refuses_what_a_tunnel_cannot_use
test_tunnel_command
test_tunnel_reopens_and_gives_up
test_tunnel_frees_the_api_port
test_release_api_port_command
test_connect_check_waits_for_the_tunnel_and_records_it
test_connect_check_fails_on_a_tunnel_that_does_not_work
test_connect_stop
test_down_lists_the_docker_objects
test_status_lists_the_docker_objects
finish
