# shellcheck shell=bash
# up: launches the sandbox's master from the shared host image, or picks up the one already
# running, installs the Kubernetes master on it, and keeps its credentials in the local state.
# Runs in the tools image.

MASTER_ROOT_VOLUME_GB=30
MASTER_INSTALL_TIMEOUT_SECONDS=2700
NAME_SUFFIX_CHARS=abcdefghijklmnopqrstuvwxyz0123456789

KUBE_VERSION=1.15.4
KUBE_API_PORT=6443
# The values deploy/contents/install/app/install-master.sh gives the same placeholders.
KUBE_POD_CIDR=10.244.0.0/16
KUBE_NODE_CIDR_MASK=26
KUBE_KUBELET_PORT=10250
KUBEADM_CONFIG_TEMPLATE=host/kubeadm-init-config.yaml
# The network plugin the platform's own install applies, relative to the repository root.
NETWORK_MANIFEST=deploy/contents/k8s/kube-system/canal.yaml
# Where install-master.sh leaves the credentials on the master.
REMOTE_ADMIN_CONF=/etc/kubernetes/admin.conf
REMOTE_JOIN_TOKEN=/etc/kubernetes/cp-sandbox/join-token
REMOTE_CA_CERT_HASH=/etc/kubernetes/cp-sandbox/ca-cert-hash
DRY_RUN_CA_CERT_HASH=0000000000000000000000000000000000000000000000000000000000000000

cmd_up() {
  local name='' new=0 master id ami expires ip
  while (($#)); do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --new) new=1; shift ;;
      *) die "up: unknown option $1" ;;
    esac
  done
  [[ -z $name || $new == 0 ]] || die "up: --name and --new do not go together"
  name=$(up_sandbox_name "$name" "$new")
  validate_name "$name"

  # Everything that can refuse comes before anything is written, so a refused up leaves no local
  # state. The preflight check comes before anything is launched, the host image's builder
  # included; a master that runs already is checked by its install.
  if [[ -f $(state_file "$name") ]]; then
    use_sandbox_region "$name"
  fi
  master=$(find_master "$name")
  if [[ -z $master ]]; then
    run_check >&2 || die "the preflight check failed, so up launched nothing. Fix the items \
marked FAIL above; sandbox.sh check runs the check alone."
    ensure_host_image "$(host_version)"
  fi
  if [[ ! -f $(state_file "$name") ]]; then
    state_set "$name" name "$name" owner "$CP_SANDBOX_OWNER" region "$SANDBOX_REGION"
  fi
  ensure_ssh_key "$name"

  if [[ -n $master ]]; then
    id=$(jq -r .id <<<"$master")
    ami=$(jq -r .ami <<<"$master")
    expires=$(jq -r .expires <<<"$master")
    log "Sandbox $name: master $id is $(jq -r .state <<<"$master") already."
  else
    ami=$HOST_IMAGE
    expires=$(utc_after_hours "$CP_SANDBOX_TTL_HOURS")
    id=$(launch_instance "$name" master "$ami" "$expires" "$CP_SANDBOX_MASTER_INSTANCE_TYPE" \
      "$MASTER_ROOT_VOLUME_GB" "$(master_user_data "$name")")
    log "Sandbox $name: launched master $id from $ami."
    state_set "$name" instance_id "$id" ami "$ami" created "$(utc_now)" expires "$expires"
  fi

  aws_call '' ec2 wait instance-running --instance-ids "$id"
  ip=$(aws_call 10.0.0.10 ec2 describe-instances --instance-ids "$id" \
    --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text)
  state_set "$name" instance_id "$id" ami "$ami" expires "$expires" private_ip "$ip"

  wait_ssm_online "$id" "The master is still running: run up again to keep waiting, or down to \
remove it."
  master_install "$name" "$id" "$ip"

  if dry_run; then
    printf '[dry-run] Nothing was launched or recorded. Without --dry-run:\n'
  fi
  cat <<EOF
Sandbox $name is up.
  master      $id ($ip), Kubernetes $KUBE_VERSION, Ready
  region      $SANDBOX_REGION
  expires     $expires
  kubeconfig  $(kubeconfig_file "$name"), for $(kube_tunnel_server "$name")
              through the tunnel that sandbox.sh connect opens
  state       $(state_dir "$name")
EOF
}

# up_sandbox_name <name-or-empty> <new>: the sandbox up acts on.
up_sandbox_name() {
  local names=()
  if [[ -n $1 ]]; then
    printf '%s\n' "$1"
    return 0
  fi
  mapfile -t names < <(local_sandboxes)
  if [[ $2 == 1 || ${#names[@]} == 0 ]]; then
    new_sandbox_name
  elif ((${#names[@]} == 1)); then
    printf '%s\n' "${names[0]}"
  else
    die "several local sandboxes (${names[*]}); pass --name <name> to pick one, or --new"
  fi
}

# new_sandbox_name: <owner>-<4 random characters>, the owner cut to what a name allows.
new_sandbox_name() {
  local owner suffix=''
  owner=$(printf '%s' "${CP_SANDBOX_OWNER%%@*}" | tr '[:upper:]' '[:lower:]' |
    tr -c 'a-z0-9' '-' | tr -s '-' | sed -e 's/^-*//' -e 's/-*$//' | cut -c1-30 | sed 's/-*$//')
  for _ in 1 2 3 4; do
    suffix+=${NAME_SUFFIX_CHARS:RANDOM%${#NAME_SUFFIX_CHARS}:1}
  done
  printf '%s\n' "${owner:-sandbox}-$suffix"
}

ensure_ssh_key() {
  local key
  key=$(ssh_key_file "$1")
  [[ ! -f $key ]] || return 0
  if dry_run; then
    dry_run_note "$(print_command ssh-keygen -q -t ed25519 -N '' -C "cp-sandbox-$1" -f "$key")"
    return 0
  fi
  ssh-keygen -q -t ed25519 -N '' -C "cp-sandbox-$1" -f "$key"
}

# find_master <name>: the sandbox's master that is not terminated, as JSON, or nothing. Fails when
# another owner's instances carry the name.
find_master() {
  local named others masters
  named=$(sandbox_instances "$1" '')
  others=$(not_mine <<<"$named")
  if [[ $(jq length <<<"$others") != 0 ]]; then
    print_not_mine "$named" >&2
    die "the name $1 is taken by instances that are not tagged $TAG_OWNER=$CP_SANDBOX_OWNER; \
pass another --name"
  fi
  masters=$(mine <<<"$named" | jq -c --arg role master '[.[] | select(.role == $role)]')
  case $(jq length <<<"$masters") in
    0) return 0 ;;
    1) ;;
    *) die "sandbox $1 has several masters: $(jq -r 'map(.id) | join(", ")' <<<"$masters")" ;;
  esac
  case $(jq -r '.[0].state' <<<"$masters") in
    pending | running) jq -c '.[0]' <<<"$masters" ;;
    *) die "master $(jq -r '.[0].id' <<<"$masters") of sandbox $1 is $(jq -r '.[0].state' <<<"$masters"); \
start it, or remove the sandbox with down" ;;
  esac
}

# master_user_data <name>: cloud-init config giving the default user the sandbox's SSH key.
master_user_data() {
  local public_key
  public_key=$(ssh_key_file "$1").pub
  if [[ -f $public_key ]]; then
    public_key=$(<"$public_key")
  else
    public_key="<the public key ssh-keygen writes to $public_key>"
  fi
  printf '#cloud-config\nssh_authorized_keys:\n  - %s\n' "$public_key"
}

# render_template <file> [<NAME=value>...]: the file with each ${NAME} replaced by its value. Fails
# when a ${CP_...} placeholder is left without one.
render_template() {
  local file=$1 content pair left='\$\{CP_[A-Z0-9_]+\}'
  shift
  content=$(<"$file")
  for pair in "$@"; do
    content=${content//"\${${pair%%=*}}"/"${pair#*=}"}
  done
  if [[ $content =~ $left ]]; then
    die "$file: ${BASH_REMATCH[0]} has no value"
  fi
  printf '%s\n' "$content"
}

# render_kubeadm_config <name> <private-ip>
render_kubeadm_config() {
  render_template "$SCRIPTS_DIR/$KUBEADM_CONFIG_TEMPLATE" \
    "CP_KUBE_FLANNEL_CIDR=$KUBE_POD_CIDR" "CP_KUBE_NODE_CIDR_MASK=$KUBE_NODE_CIDR_MASK" \
    "CP_KUBE_KUBELET_PORT=$KUBE_KUBELET_PORT" "CP_KUBE_MASTER_IP=$2" \
    "CP_KUBE_MASTER_TUNNEL_HOST=$(kube_tunnel_host "$1")"
}

# master_install <name> <instance-id> <private-ip>: installs the Kubernetes master, which is safe to
# run again, and fetches its credentials.
master_install() {
  local name=$1 id=$2 ip=$3 work
  work=$(mktemp -d)
  render_kubeadm_config "$name" "$ip" >"$work/kubeadm-init-config.yaml"
  render_template "$SANDBOX_REPO_ROOT/$NETWORK_MANIFEST" "CP_KUBE_FLANNEL_CIDR=$KUBE_POD_CIDR" \
    >"$work/canal.yaml"
  ssm_send_files "$id" "$SCRIPTS_DIR/host/install-master.sh" "$work/kubeadm-init-config.yaml" \
    "$work/canal.yaml"
  rm -rf "$work"

  log "Installing the Kubernetes master on $id, up to $((MASTER_INSTALL_TIMEOUT_SECONDS / 60)) minutes."
  ssm_run_script "$id" "$MASTER_INSTALL_TIMEOUT_SECONDS" "install the sandbox master" \
    install-master.sh "$REMOTE_DIR/kubeadm-init-config.yaml" "$REMOTE_DIR/canal.yaml" >&2
  fetch_master_credentials "$name" "$id"
}

# The kubeconfig and the join token travel over SSH, straight into files: Run Command would keep
# them in the account's command history, and printing them would put them on the screen.
fetch_master_credentials() {
  local name=$1 id=$2 hash
  fetch_secret "$name" "$id" "$REMOTE_ADMIN_CONF" "$(kubeconfig_file "$name")"
  point_kubeconfig_at_tunnel "$name"
  fetch_secret "$name" "$id" "$REMOTE_JOIN_TOKEN" "$(join_token_file "$name")"
  hash=$(instance_ssh "$DRY_RUN_CA_CERT_HASH" "$name" "$id" "sudo cat $REMOTE_CA_CERT_HASH")
  [[ $hash =~ ^[0-9a-f]{64}$ ]] || die "$REMOTE_CA_CERT_HASH on $id holds no SHA-256 hash"
  state_set "$name" kube_api_port "$KUBE_API_PORT" kube_ca_cert_hash "$hash" \
    kube_tunnel_host "$(kube_tunnel_host "$name")"
}

# kube_tunnel_server <name>: where the developer's containers reach the master's Kubernetes API, on
# the sandbox's Docker network, through the tunnel connect opens.
kube_tunnel_server() {
  printf 'https://%s:%s\n' "$(kube_tunnel_host "$1")" "$KUBE_API_PORT"
}

# point_kubeconfig_at_tunnel <name>: the master's private address cannot be reached from outside
# the VPC, so the local kubeconfig names the tunnel instead; the API server's certificate carries
# both names. kubectl rewrites the file in place, and prints none of it.
point_kubeconfig_at_tunnel() {
  local file cluster server
  file=$(kubeconfig_file "$1")
  server=$(kube_tunnel_server "$1")
  if dry_run; then
    dry_run_note "$(print_command kubectl config set-cluster '<its one cluster>' --kubeconfig "$file" \
      --server "$server")"
    return 0
  fi
  cluster=$(kubectl config view --kubeconfig "$file" -o 'jsonpath={.clusters[*].name}')
  [[ -n $cluster && $cluster != *' '* ]] || die "$file names no single cluster to point at the tunnel"
  kubectl config set-cluster "$cluster" --kubeconfig "$file" --server "$server" >/dev/null
}

# fetch_secret <name> <instance-id> <remote-file> <local-file>: copies a file only root can read on
# the instance into the local state, readable by its owner only, without printing it.
fetch_secret() {
  local name=$1 id=$2 remote=$3 file=$4 part
  if dry_run; then
    instance_ssh '' "$name" "$id" "sudo cat $remote"
    dry_run_note "write what it prints to $file"
    return 0
  fi
  part=$(mktemp "$(dirname "$file")/.fetch.XXXXXX.secret")
  if ! instance_ssh '' "$name" "$id" "sudo cat $remote" >"$part" || [[ ! -s $part ]]; then
    rm -f "$part"
    die "could not read $remote on $id over SSH"
  fi
  mv -f "$part" "$file"
}
