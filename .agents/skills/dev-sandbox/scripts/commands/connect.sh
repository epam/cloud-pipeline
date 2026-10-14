# shellcheck shell=bash
# connect: the tunnel between the developer's Docker and the sandbox's master. SSH through a
# Session Manager session, with the sandbox's key, carries three forwards:
#   -L  the tunnel container's port KUBE_API_PORT, on the sandbox's Docker network, to the master's
#       Kubernetes API. Containers there reach it as https://<kube_tunnel_host>:KUBE_API_PORT, the
#       tunnel container's alias, which the API server's certificate and the kubeconfig carry.
#   -R  TUNNEL_API_PORT on the master's private address, where the platform's nodes reach the API,
#       to TUNNEL_API_TARGET on the Docker network: the API that runs on the workstation.
#   -D  a SOCKS proxy on the tunnel container's port TUNNEL_SOCKS_PORT, whose connections the
#       master makes: the containers on the network reach the VPC through it, as the local API
#       does for the platform's nodes, the registry on the master and its own address there.
#
# sandbox.sh runs the Docker side on the host. These are its steps in the tools image:
# connect-prepare checks the sandbox before the tunnel container starts, tunnel is that
# container's process, connect-check waits for the forwards, and connect-stop records that the
# tunnel is gone.

TUNNEL_API_PORT=9999
TUNNEL_API_TARGET=api:$TUNNEL_API_PORT
TUNNEL_SOCKS_PORT=1080
# A session that ends sooner than this counts as a failure to open the tunnel; after so many in a
# row, the tunnel gives up. A longer one that ends, as when Session Manager closes it, is reopened.
TUNNEL_QUICK_SECONDS=60
TUNNEL_QUICK_FAILURES=5
TUNNEL_RETRY_SECONDS=10
CONNECT_TIMEOUT_SECONDS=180
CONNECT_POLL_SECONDS=5
DRY_RUN_NODES='NAME   STATUS   ROLES    AGE   VERSION'
# What state.json says of the tunnel. connect-check writes it once both forwards work; it goes when
# the tunnel closes, gives up, or fails a check.
TUNNEL_STATE_KEYS=(tunnel_container tunnel_network tunnel_kube_server tunnel_api_address
  tunnel_api_target tunnel_socks)

# connect_name --name <name>: the sandbox named, which the host has already picked.
connect_name() {
  local name=''
  while (($#)); do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      *) die "unknown option $1" ;;
    esac
  done
  [[ -n $name ]] || die "--name <name> is required"
  validate_name "$name"
  printf '%s\n' "$name"
}

# connect_master <name>: the sandbox's master, from the local state, or an error saying what is
# missing. Sets MASTER_ID and MASTER_IP.
connect_master() {
  local name=$1 file
  [[ -f $(state_file "$name") ]] || die "no local state for sandbox $name: run up first"
  use_sandbox_region "$name"
  MASTER_ID=$(state_get "$name" instance_id)
  MASTER_IP=$(state_get "$name" private_ip)
  [[ -n $MASTER_ID && -n $MASTER_IP ]] ||
    die "the local state of sandbox $name names no master and its address: run up first"
  for file in "$(ssh_key_file "$name")" "$(kubeconfig_file "$name")"; do
    [[ -f $file ]] || die "sandbox $name has no $(basename "$file") in its local state: run up, \
which installs the master and fetches it"
  done
}

# connect-prepare: the master in the local state runs, tagged as this owner's master of the
# sandbox, and the local state has what the tunnel needs. Under --dry-run, also prints the ssh
# commands of the tunnel: the one that frees the API port on the master, and its session.
cmd_connect_prepare() {
  local name master
  name=$(connect_name "$@")
  connect_master "$name"

  master=$(sandbox_instances "$name" "$(dry_run_instances "$name" master "$MASTER_ID")" | mine |
    jq -c --arg id "$MASTER_ID" '[.[] | select(.id == $id)] | .[0] // empty')
  [[ -n $master ]] || die "master $MASTER_ID of sandbox $name is gone from $SANDBOX_REGION: run up \
to launch another"
  [[ $(jq -r .role <<<"$master") == master ]] ||
    die "$MASTER_ID is not tagged as the master of sandbox $name"
  [[ $(jq -r .state <<<"$master") == running ]] ||
    die "master $MASTER_ID of sandbox $name is $(jq -r .state <<<"$master"), not running"
  log "Sandbox $name: master $MASTER_ID ($MASTER_IP) is running."
  if dry_run; then
    release_api_port "$name" "$MASTER_ID" "$MASTER_IP"
    tunnel_ssh "$name" "$MASTER_ID" "$MASTER_IP"
  fi
}

# release_api_port_command <private-ip>: the shell command that ends every sshd session on the
# master that listens on <private-ip>:TUNNEL_API_PORT, where the tunnel binds its remote forward,
# and prints their process IDs. ss names the process sshd-session since OpenSSH 9.8, and sshd
# before it. A session already gone by the time kill runs is named all the same.
release_api_port_command() {
  printf '%s\n' "pids=\$(sudo ss -Hltnp 'sport = :$TUNNEL_API_PORT' | \
grep -F ' $1:$TUNNEL_API_PORT ' | grep -oE '\"sshd(-session)?\",pid=[0-9]+' | cut -d= -f2 | \
sort -u); [ -z \"\$pids\" ] || { sudo kill \$pids; echo \$pids; }"
}

# release_api_port <name> <instance-id> <private-ip>: frees the API port on the master's private
# address for the tunnel's next session, and writes the process IDs of the sessions it ended. A
# session that ends without closing its connection, as when the tunnel container is removed, stays
# on the master with its remote forward until Session Manager closes the connection, many minutes
# later, and until then a new session cannot bind the port. Only a tunnel's session listens there.
release_api_port() {
  instance_ssh '' "$1" "$2" "$(release_api_port_command "$3")"
}

# tunnel_ssh <name> <instance-id> <private-ip>: the tunnel's SSH session, until it ends. Under
# --dry-run, prints the command instead.
tunnel_ssh() {
  ssh_options "$1" "$2"
  local -a ssh_command=(ssh "${SSH_OPTIONS[@]}" -N -T -o ExitOnForwardFailure=yes
    -L "0.0.0.0:$KUBE_API_PORT:127.0.0.1:$KUBE_API_PORT"
    -R "$3:$TUNNEL_API_PORT:$TUNNEL_API_TARGET"
    -D "0.0.0.0:$TUNNEL_SOCKS_PORT"
    "$SSH_USER@$2")
  if dry_run; then
    dry_run_note "$(print_command "${ssh_command[@]}")"
    return 0
  fi
  "${ssh_command[@]}"
}

# tunnel: the tunnel container's process. Keeps the tunnel open, opening it again when a session
# ends, and gives up after TUNNEL_QUICK_FAILURES sessions in a row that each ended within
# TUNNEL_QUICK_SECONDS. Everything it says goes to the container's log.
cmd_tunnel() {
  local name started status released failures=0
  name=$(connect_name "$@")
  connect_master "$name"
  if dry_run; then
    release_api_port "$name" "$MASTER_ID" "$MASTER_IP"
    tunnel_ssh "$name" "$MASTER_ID" "$MASTER_IP"
    return 0
  fi

  while :; do
    # When SSH cannot reach the master to free the port, the session after it fails too, and counts
    # the failure.
    released=$(release_api_port "$name" "$MASTER_ID" "$MASTER_IP") || released=''
    if [[ -n $released ]]; then
      log "$(utc_now) Ended the SSH session(s) on the master that an earlier tunnel left listening on \
$MASTER_IP:$TUNNEL_API_PORT: process(es) $released."
    fi
    log "$(utc_now) Opening the tunnel to master $MASTER_ID of sandbox $name: \
$(kube_tunnel_host "$name"):$KUBE_API_PORT here to its Kubernetes API, its \
$MASTER_IP:$TUNNEL_API_PORT to $TUNNEL_API_TARGET here, and a SOCKS proxy into its VPC at \
$(kube_tunnel_host "$name"):$TUNNEL_SOCKS_PORT here."
    started=$SECONDS
    status=0
    tunnel_ssh "$name" "$MASTER_ID" "$MASTER_IP" || status=$?
    if ((SECONDS - started < TUNNEL_QUICK_SECONDS)); then
      failures=$((failures + 1))
    else
      failures=0
    fi
    if ((failures >= TUNNEL_QUICK_FAILURES)); then
      state_unset "$name" "${TUNNEL_STATE_KEYS[@]}"
      die "$(utc_now) the tunnel closed $failures times in a row within $TUNNEL_QUICK_SECONDS seconds \
of opening, last with ssh status $status: giving up. sandbox.sh connect opens it again."
    fi
    log "$(utc_now) The tunnel closed, ssh status $status. Opening it again in \
$TUNNEL_RETRY_SECONDS seconds."
    sleep "$TUNNEL_RETRY_SECONDS"
  done
}

# kube_call <dry-run output> <kubectl args...>: kubectl, or under --dry-run the command printed and
# <dry-run output> written.
kube_call() {
  local output=$1
  shift
  if dry_run; then
    dry_run_note "$(print_command kubectl "$@")"
    [[ -z $output ]] || printf '%s\n' "$output"
    return 0
  fi
  kubectl "$@"
}

# tunnel_nodes <name> <error-file>: the cluster's nodes, through the tunnel; errors go to the file.
tunnel_nodes() {
  if dry_run; then
    kube_call "$DRY_RUN_NODES" --kubeconfig "$(kubeconfig_file "$1")" get nodes -o wide
    return 0
  fi
  kube_call '' --kubeconfig "$(kubeconfig_file "$1")" --request-timeout=10s get nodes -o wide \
    2>"$2"
}

# api_forward_bound <name> <instance-id> <private-ip>: the master listens on the private address,
# on TUNNEL_API_PORT: the tunnel's remote forward is bound where the platform's nodes reach it.
api_forward_bound() {
  local listening
  listening=$(instance_ssh "LISTEN 0 128 $3:$TUNNEL_API_PORT 0.0.0.0:*" "$1" "$2" \
    "ss -Hltn 'sport = :$TUNNEL_API_PORT'") || return 1
  awk -v want="$3:$TUNNEL_API_PORT" '$4 == want { found = 1 } END { exit !found }' <<<"$listening"
}

# tunnel_socks <name>: where the containers on the sandbox's network reach the SOCKS proxy.
tunnel_socks() {
  printf '%s:%s\n' "$(kube_tunnel_host "$1")" "$TUNNEL_SOCKS_PORT"
}

# socks_call <dry-run output> <curl args...>: curl through the tunnel's SOCKS proxy, or under
# --dry-run the command printed and <dry-run output> written.
socks_call() {
  local output=$1
  shift
  if dry_run; then
    dry_run_note "$(print_command curl "$@")"
    [[ -z $output ]] || printf '%s\n' "$output"
    return 0
  fi
  curl "$@"
}

# socks_reaches_master <name> <private-ip>: the master's Kubernetes API answers at its private
# address through the SOCKS proxy, as the VPC does for the containers on the network. Any HTTP
# status will do: an answer at all is what is checked.
socks_reaches_master() {
  local code
  code=$(socks_call 200 --silent --insecure --output /dev/null --write-out '%{http_code}' \
    --max-time 10 --socks5 "$(tunnel_socks "$1")" "https://$2:$KUBE_API_PORT/healthz") || true
  [[ $code =~ ^[1-5][0-9][0-9]$ ]]
}

# connect-check: runs on the sandbox's Docker network once the tunnel container has started. Waits
# until the Kubernetes API answers through the tunnel, the master listens on the API port, and the
# SOCKS proxy reaches the master's private address, then records the tunnel in the local state.
# What an earlier connect recorded goes first, so that a check that fails leaves none of it.
cmd_connect_check() {
  local name nodes deadline error_file
  name=$(connect_name "$@")
  connect_master "$name"
  state_unset "$name" "${TUNNEL_STATE_KEYS[@]}"
  deadline=$((SECONDS + CONNECT_TIMEOUT_SECONDS))
  error_file=$(mktemp)

  log "Waiting for the Kubernetes API at $(kube_tunnel_server "$name"), through the tunnel, up to \
$((CONNECT_TIMEOUT_SECONDS / 60)) minutes."
  until nodes=$(tunnel_nodes "$name" "$error_file"); do
    if ((SECONDS >= deadline)); then
      tail -n 5 "$error_file" >&2
      rm -f "$error_file"
      die "the Kubernetes API did not answer at $(kube_tunnel_server "$name") through the tunnel"
    fi
    sleep "$CONNECT_POLL_SECONDS"
  done
  rm -f "$error_file"

  log "Checking that master $MASTER_ID listens on $MASTER_IP:$TUNNEL_API_PORT, over SSH."
  until api_forward_bound "$name" "$MASTER_ID" "$MASTER_IP"; do
    ((SECONDS < deadline)) || die "master $MASTER_ID does not listen on $MASTER_IP:$TUNNEL_API_PORT: \
the tunnel's remote forward is not bound there. install-master.sh lets sshd bind it (GatewayPorts \
clientspecified); run up again to repeat it."
    sleep "$CONNECT_POLL_SECONDS"
  done

  log "Checking that the SOCKS proxy at $(tunnel_socks "$name") reaches $MASTER_IP:$KUBE_API_PORT."
  until socks_reaches_master "$name" "$MASTER_IP"; do
    ((SECONDS < deadline)) || die "the SOCKS proxy at $(tunnel_socks "$name") does not reach the \
master at $MASTER_IP:$KUBE_API_PORT"
    sleep "$CONNECT_POLL_SECONDS"
  done

  state_set "$name" tunnel_container "$(tunnel_container "$name")" \
    tunnel_network "$(sandbox_network "$name")" tunnel_kube_server "$(kube_tunnel_server "$name")" \
    tunnel_api_address "$MASTER_IP:$TUNNEL_API_PORT" tunnel_api_target "$TUNNEL_API_TARGET" \
    tunnel_socks "$(tunnel_socks "$name")"

  if dry_run; then
    printf '[dry-run] Nothing was started or recorded. Without --dry-run:\n'
  fi
  nodes="  ${nodes//$'\n'/$'\n'  }"
  cat <<EOF
Sandbox $name is connected, through the container $(tunnel_container "$name") on the Docker
network $(sandbox_network "$name"). From containers on that network:
  kubernetes  $(kube_tunnel_server "$name"), with $(kubeconfig_file "$name")
  kubectl     scripts/tools.sh --sandbox $name kubectl get nodes
  the VPC     socks5://$(tunnel_socks "$name"), connections made by the master
On the master, and from the platform's nodes:
  api         $MASTER_IP:$TUNNEL_API_PORT reaches $TUNNEL_API_TARGET on the network
The tunnel's log: docker logs $(tunnel_container "$name"). To close it: sandbox.sh connect --stop
Nodes:
$nodes
EOF
}

cmd_connect_stop() {
  local name
  name=$(connect_name "$@")
  state_unset "$name" "${TUNNEL_STATE_KEYS[@]}"
}
