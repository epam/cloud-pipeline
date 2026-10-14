# shellcheck shell=bash
# services: the platform's API on the developer's workstation, against the sandbox. sandbox.sh
# runs the Docker side on the host (lib/services.sh); these are its steps in the tools image:
#   services-prepare    what the API needs of AWS, of the master and of the local state: the VPC's
#                       address ranges, an EC2 key pair for the nodes, the registry with its tool
#                       and the distribution files on the master (host/install-services.sh), the
#                       API's TLS and JWT keys, and its settings, api.properties and api.env
#   services-sequences  the SQL that moves the run and pool node IDs to the sandbox's own block
#   services-wait       waits for the API to answer, on the sandbox's Docker network
#   services-configure  the cloud region, the preferences, the registry and the tool, through the
#                       API's REST API, and what the API reads back of them
# With --with, services-prepare also writes the settings of the optional services, and
# services-configure points the API's search at their Elasticsearch.
#
# Nothing in api.properties, api.env, the services' settings or state.json is secret: the API's
# container reads the keys and the join token from their own files (docker/api-entrypoint.sh), and
# the services read the admin's token from its file (docker/service-entrypoint.sh).

SERVICES_API_HOST=api
SERVICES_API_PORT=$TUNNEL_API_PORT
# As install-services.sh sets them on the master.
REGISTRY_PORT=5000
REGISTRY_CERT_REMOTE=/etc/cp-sandbox/registry/registry.crt
TOOL_REPOSITORY=library/centos
TOOL_TAG=7
TOOL_GROUP=library
DISTRIBUTION_PORT=8090
# Where the platform's CI publishes its builds; the newest one under the prefix is taken, unless
# CP_SANDBOX_DISTRIBUTION_URL names another.
DISTRIBUTION_BUCKET_URL=https://cloud-pipeline-oss-builds.s3.amazonaws.com
DISTRIBUTION_PREFIX=builds/develop/
DRY_RUN_DISTRIBUTION_URL=$DISTRIBUTION_BUCKET_URL/${DISTRIBUTION_PREFIX}cloud-pipeline.0.0.0.0.dryrun.tgz
SERVICES_INSTALL_TIMEOUT_SECONDS=2700
API_WAIT_SECONDS=900
API_POLL_SECONDS=10
DEV_PROFILE=api/profiles/dev/application.properties
NODE_SCRIPTS_DIR=scripts/autoscaling/aws
NODE_INIT_SCRIPT=scripts/autoscaling/init_multicloud_v1.15.4_al2023.sh
PIPE_COMMON_DIR=workflows/pipe-common
DB_URL=jdbc:postgresql://db:5432/pipeline
TLS_DAYS=3650
JWT_KEY_BITS=2048
ADMIN_USER=pipe_admin
CLUSTER_MAX_SIZE=2
# A node the platform started goes once it has been idle this long. The platform's own policy,
# MINUTES_TILL_HOUR, would keep it up to the end of its first hour.
CLUSTER_KEEP_ALIVE_POLICY=IDLE_MINUTES
CLUSTER_KEEP_ALIVE_MINUTES=5
# Run IDs name the nodes' instances (tag:Name), and the platform's node scripts find an instance by
# that name alone, across the region: each sandbox takes a block of IDs of its own, at random, well
# above where a real deployment's IDs are.
RUN_ID_FLOOR=100000000
RUN_ID_BLOCK=100000
RUN_ID_BLOCKS=20000
RUN_ID_SEQUENCES=(pipeline.s_pipeline_run pipeline.s_free_nodes)
# The Linux nodes' init script does not use it, but the API passes it to the node scripts on a
# command line that an empty value breaks.
KUBE_NODE_TOKEN_PLACEHOLDER=not-used-by-linux-nodes
# Of what the API's REST API answers, the keys whose string values sandbox.sh api blanks out, and
# the names of the {name, value} pairs whose values it blanks out: sshPassword, SSH_PASS,
# API_TOKEN, CP_ACCOUNT_KEY_1, CP_ACCOUNT_ID_1 and the like, matched whatever their case.
SECRET_KEY_PATTERN='pass|secret|token|credential|private|(^|_)(key|account_id)(_|$)'
# The platform's installer, whose api_register_search sets the API's search preferences.
INSTALLER_UTILS=deploy/contents/install/app/configure-utils.sh
INSTALLER_SEARCH_FUNCTION=api_register_search
# Where the monitoring service keeps the time it last checked the platform usage credits: its
# working directory in its container. Its own settings name /opt/monitoring, which is not there.
MONITORING_CREDITS_FILE=lastPlatformUsageCreditsMonitorTime.txt
# An optional service's settings, in its module.
SERVICE_SETTINGS=src/main/resources/application.properties
# The billing service's settings in the platform's image, whose sync.billing.schedule runs it once
# a day. The module's own schedule, every few minutes, never bills the last day of a run: a sync
# bills the days that have ended, of the runs active since the sync before it.
BILLING_DEPLOY_SETTINGS=deploy/docker/cp-billing-srv/config/application.properties

# services_options --name <name> [--docker-subnets <subnets>] [--with <services>]: sets
# SERVICES_NAME, SERVICES_DOCKER_SUBNETS and SERVICES_WITH, the optional services as words.
services_options() {
  SERVICES_NAME=''
  SERVICES_DOCKER_SUBNETS=''
  SERVICES_WITH=''
  while (($#)); do
    case $1 in
      --name) SERVICES_NAME=$(option_value "$1" "${2-}"); shift 2 ;;
      --docker-subnets) SERVICES_DOCKER_SUBNETS=${2-}; shift 2 ;;
      --with) SERVICES_WITH=$(optional_services "$(option_value "$1" "${2-}")"); shift 2 ;;
      *) die "unknown option $1" ;;
    esac
  done
  [[ -n $SERVICES_NAME ]] || die "--name <name> is required"
  validate_name "$SERVICES_NAME"
}

# with_service <service>...: one of the services is among the optional ones asked for.
with_service() {
  local service
  for service in "$@"; do
    [[ " $SERVICES_WITH " != *" $service "* ]] || return 0
  done
  return 1
}

# The sandbox's files the services read, beyond what up and connect leave.
api_tls_key_file() { printf '%s/api-tls-key.pem\n' "$(state_dir "$1")"; }
api_tls_cert_file() { printf '%s/api-tls.crt\n' "$(state_dir "$1")"; }
jwt_private_key_file() { printf '%s/jwt-private-key.pem\n' "$(state_dir "$1")"; }
jwt_public_key_file() { printf '%s/jwt-public-key.pem\n' "$(state_dir "$1")"; }
admin_token_file() { printf '%s/admin-token.secret\n' "$(state_dir "$1")"; }
registry_cert_file() { printf '%s/registry-ca.crt\n' "$(state_dir "$1")"; }
api_properties_file() { printf '%s/api.properties\n' "$(state_dir "$1")"; }
api_env_file() { printf '%s/api.env\n' "$(state_dir "$1")"; }
# service_properties_file <name> <service>: the settings of an optional service.
service_properties_file() { printf '%s/%s.properties\n' "$(state_dir "$1")" "$2"; }

# services_master <name>: the sandbox's master and what up and connect recorded of it, or an error
# saying what is missing. Sets MASTER_ID and MASTER_IP, as connect_master does.
services_master() {
  local name=$1 file
  connect_master "$name"
  file=$(join_token_file "$name")
  [[ -f $file ]] || die "sandbox $name has no $(basename "$file") in its local state: run up"
  [[ -n $(state_get "$name" kube_ca_cert_hash) && -n $(state_get "$name" ami) ]] ||
    die "the local state of sandbox $name has no CA cert hash or host image: run up"
  # connect records the SOCKS proxy once it has checked it; a tunnel opened before connect had
  # one runs without it.
  if [[ -z $(state_get "$name" tunnel_socks) ]]; then
    dry_run || die "the tunnel of sandbox $name has no SOCKS proxy, which the API reaches the VPC \
through: run sandbox.sh connect --stop, then sandbox.sh connect"
    dry_run_note "the tunnel of sandbox $name has no SOCKS proxy recorded; without --dry-run, services stops here"
  fi
}

# recorded <name> <key>: a value services-prepare recorded in the sandbox's state.json. Under
# --dry-run, which records nothing, <key> stands for one that is not there.
recorded() {
  local value
  value=$(state_get "$1" "$2")
  if [[ -z $value ]] && dry_run; then
    value="<$2>"
  fi
  printf '%s\n' "$value"
}

# services_api_url <address>: the API's REST root at an address, as the platform's settings take it.
services_api_url() {
  printf 'https://%s:%s/pipeline/restapi/\n' "$1" "$SERVICES_API_PORT"
}

# vpc_cidrs: the configured VPC's IPv4 address ranges, separated by spaces.
vpc_cidrs() {
  # shellcheck disable=SC2016 # the backquotes are JMESPath's
  aws_call 10.0.0.0/16 ec2 describe-vpcs --vpc-ids "$CP_SANDBOX_VPC_ID" \
    --query 'Vpcs[0].CidrBlockAssociationSet[?CidrBlockState.State==`associated`].CidrBlock' \
    --output text | tr -s '\t\n' '  ' | sed 's/ $//'
}

subnet_zone() {
  aws_call "${SANDBOX_REGION}a" ec2 describe-subnets --subnet-ids "$CP_SANDBOX_SUBNET_ID" \
    --query 'Subnets[0].AvailabilityZone' --output text
}

# ipv4_number <a.b.c.d>
ipv4_number() {
  local a b c d
  IFS=. read -r a b c d <<<"$1"
  printf '%s\n' $(((a << 24) + (b << 16) + (c << 8) + d))
}

# cidrs_overlap <cidr> <cidr>: the two IPv4 ranges share an address.
cidrs_overlap() {
  local first=${1%/*} first_bits=${1#*/} second=${2%/*} second_bits=${2#*/} start1 end1 start2 end2
  start1=$(ipv4_number "$first")
  start2=$(ipv4_number "$second")
  start1=$((start1 & ~((1 << (32 - first_bits)) - 1) & 0xffffffff))
  start2=$((start2 & ~((1 << (32 - second_bits)) - 1) & 0xffffffff))
  end1=$((start1 + (1 << (32 - first_bits)) - 1))
  end2=$((start2 + (1 << (32 - second_bits)) - 1))
  ((start1 <= end2 && start2 <= end1))
}

# check_docker_subnets <vpc-cidrs> <docker-subnets>: the API's container routes the VPC through the
# tunnel, so the sandbox's Docker network must not take any of its addresses.
check_docker_subnets() {
  local vpc subnet
  for vpc in $1; do
    for subnet in $2; do
      [[ $subnet == *.*.*.*/* ]] || continue
      if cidrs_overlap "$vpc" "$subnet"; then
        die "the sandbox's Docker network has the addresses $subnet, which overlap the VPC's $vpc: \
the API's container could not reach the VPC. Remove the network (sandbox.sh connect --stop, then \
docker network rm $(sandbox_network "$SERVICES_NAME")), and create it with another subnet: \
docker network create --label $TAG_SANDBOX=$SERVICES_NAME --subnet <a range outside $vpc> \
$(sandbox_network "$SERVICES_NAME"); then run connect again"
      fi
    done
  done
}

# ensure_key_pair <name>: the EC2 key pair the platform launches the sandbox's nodes with, which
# holds the sandbox's SSH key: imported once, tagged as the sandbox's, and removed by down.
ensure_key_pair() {
  local name=$1 key pairs tags
  key=cp-sandbox-$name
  pairs=$(aws_call '[]' ec2 describe-key-pairs --filters "Name=key-name,Values=$key" \
    --query 'KeyPairs[].Tags' --output json)
  case $(jq length <<<"$pairs") in
    0)
      tags=$(sandbox_tags "$name" worker "$(state_get "$name" expires)")
      aws_call key-0dryrun ec2 import-key-pair --key-name "$key" \
        --public-key-material "fileb://$(ssh_key_file "$name").pub" \
        --tag-specifications "$(jq -cn --argjson tags "$tags" '[{ResourceType: "key-pair", Tags: $tags}]')" \
        --query KeyPairId --output text >/dev/null
      log "Imported the sandbox's SSH key as the EC2 key pair $key, for the platform's nodes."
      ;;
    *)
      jq -e --arg tag_sandbox "$TAG_SANDBOX" --arg tag_owner "$TAG_OWNER" --arg name "$name" \
        --arg owner "$CP_SANDBOX_OWNER" \
        '.[0] // [] | from_entries | .[$tag_sandbox] == $name and .[$tag_owner] == $owner' \
        <<<"$pairs" >/dev/null ||
        die "the EC2 key pair $key is there, but not tagged $TAG_SANDBOX=$name and \
$TAG_OWNER=$CP_SANDBOX_OWNER: pick another sandbox name"
      ;;
  esac
  state_set "$name" key_pair "$key"
}

# distribution_source: the published build of the platform whose pipe-common, pipe and the rest
# the sandbox's runs download, as the archive's URL.
distribution_source() {
  local listing
  if [[ -n ${CP_SANDBOX_DISTRIBUTION_URL:-} ]]; then
    printf '%s\n' "$CP_SANDBOX_DISTRIBUTION_URL"
    return 0
  fi
  if dry_run; then
    dry_run_note "$(print_command curl --silent --fail "$DISTRIBUTION_BUCKET_URL/?list-type=2&prefix=$DISTRIBUTION_PREFIX")"
    printf '%s\n' "$DRY_RUN_DISTRIBUTION_URL"
    return 0
  fi
  listing=$(curl --silent --show-error --fail \
    "$DISTRIBUTION_BUCKET_URL/?list-type=2&prefix=$DISTRIBUTION_PREFIX") ||
    die "could not list the platform's builds at $DISTRIBUTION_BUCKET_URL/$DISTRIBUTION_PREFIX; set \
CP_SANDBOX_DISTRIBUTION_URL to the URL of a cloud-pipeline.<version>.tgz"
  newest_distribution <<<"$listing"
}

# newest_distribution: of an S3 listing, the URL of the newest cloud-pipeline.<version>.tgz.
newest_distribution() {
  local newest
  newest=$({ grep -oE '<Key>[^<]*/cloud-pipeline\.[0-9][^<]*\.tgz</Key><LastModified>[^<]+' || true; } |
    sed -E 's#<Key>([^<]*)</Key><LastModified>(.*)#\2 \1#' | sort | tail -n 1 | cut -d' ' -f2)
  [[ -n $newest ]] || die "no cloud-pipeline.<version>.tgz under $DISTRIBUTION_BUCKET_URL/$DISTRIBUTION_PREFIX"
  printf '%s/%s\n' "$DISTRIBUTION_BUCKET_URL" "$newest"
}

# install_master_services <name> <instance-id> <private-ip> <distribution-source>
install_master_services() {
  local name=$1 id=$2 ip=$3 source=$4
  ssm_send_files "$id" "$SCRIPTS_DIR/host/install-services.sh" "$SCRIPTS_DIR/host/tool.Dockerfile"
  log "Installing the registry, its tool and the distribution files on $id, up to \
$((SERVICES_INSTALL_TIMEOUT_SECONDS / 60)) minutes."
  ssm_run_script "$id" "$SERVICES_INSTALL_TIMEOUT_SECONDS" "install the sandbox's services on the master" \
    install-services.sh "$ip" "$REMOTE_DIR/tool.Dockerfile" "$source" >&2
}

# fetch_registry_certificate <name> <instance-id>: the registry's certificate, which is no secret,
# for the platform to trust the registry by.
fetch_registry_certificate() {
  local name=$1 id=$2 file cert
  file=$(registry_cert_file "$name")
  cert=$(instance_ssh "-----BEGIN CERTIFICATE-----
ZHJ5LXJ1bg==
-----END CERTIFICATE-----" "$name" "$id" "sudo cat $REGISTRY_CERT_REMOTE")
  [[ $cert == '-----BEGIN CERTIFICATE-----'* ]] || die "$REGISTRY_CERT_REMOTE on $id holds no certificate"
  if dry_run; then
    dry_run_note "write the registry's certificate to $file"
    return 0
  fi
  printf '%s\n' "$cert" >"$file"
}

# ensure_api_tls <name> <private-ip>: the API's TLS key and certificate, for the names it is
# reached by: api on the Docker network, and the master's private address, where the platform's
# nodes reach it through the tunnel. Made again when the address is not on the certificate.
ensure_api_tls() {
  local name=$1 ip=$2 key cert
  key=$(api_tls_key_file "$name")
  cert=$(api_tls_cert_file "$name")
  if [[ -f $key && -f $cert ]] && openssl x509 -in "$cert" -noout -checkip "$ip" | grep -q 'does match'; then
    return 0
  fi
  if dry_run; then
    dry_run_note "make the API's TLS key and certificate, for $SERVICES_API_HOST and $ip: $key, $cert"
    return 0
  fi
  openssl req -x509 -newkey rsa:2048 -nodes -days "$TLS_DAYS" -subj "/CN=$SERVICES_API_HOST" \
    -addext "subjectAltName=DNS:$SERVICES_API_HOST,DNS:localhost,IP:127.0.0.1,IP:$ip" \
    -keyout "$key" -out "$cert" 2>/dev/null
  log "Made the API's TLS certificate, for $SERVICES_API_HOST and $ip."
}

# ensure_jwt_keys <name>: the key pair the API signs and checks its tokens with.
ensure_jwt_keys() {
  local private public
  private=$(jwt_private_key_file "$1")
  public=$(jwt_public_key_file "$1")
  [[ ! -f $private || ! -f $public ]] || return 0
  if dry_run; then
    dry_run_note "make the API's JWT key pair: $private, $public"
    return 0
  fi
  openssl genpkey -algorithm RSA -pkeyopt "rsa_keygen_bits:$JWT_KEY_BITS" -out "$private" 2>/dev/null
  openssl pkey -in "$private" -pubout -out "$public"
  log "Made the API's JWT key pair."
}

# ensure_run_id_base <name>: the sandbox's block of run IDs, picked once.
ensure_run_id_base() {
  local base
  base=$(state_get "$1" run_id_base)
  if [[ -z $base ]]; then
    base=$((RUN_ID_FLOOR + ((RANDOM << 15 | RANDOM) % RUN_ID_BLOCKS) * RUN_ID_BLOCK))
    state_set "$1" run_id_base "$base"
  fi
  printf '%s\n' "$base"
}

# render_properties <profile> <key=value>...: the profile, with each key given set to its value
# where the profile has it, and added at the end where it has not.
render_properties() {
  local profile=$1
  shift
  awk 'NR == FNR { key = substr($0, 1, index($0, "=") - 1); value[key] = $0; order[++count] = key; next }
    match($0, /^[A-Za-z0-9_.-]+=/) {
      key = substr($0, 1, RLENGTH - 1)
      if (key in value) { print value[key]; seen[key] = 1; next }
    }
    { print }
    END { for (i = 1; i <= count; i++) if (!(order[i] in seen)) print value[order[i]] }' \
    <(printf '%s\n' "$@") "$profile"
}

# write_api_settings <name> <private-ip>: api.properties, the dev profile with what the sandbox
# changes, and api.env, the environment the API's container starts with.
write_api_settings() {
  local name=$1 ip=$2 root=$SANDBOX_REPO_ROOT properties env url cluster
  properties=$(api_properties_file "$name")
  env=$(api_env_file "$name")
  url=https://$ip:$SERVICES_API_PORT/pipeline
  # Only EKS nodes are told the cluster's name, but nodeup.py fails without one, and the API leaves
  # it off nodeup.py's command line while kube.cluster.name is empty, as the dev profile has it.
  cluster=$(sandbox_network "$name")
  if dry_run; then
    dry_run_note "write $properties: $DEV_PROFILE with the database, the node scripts, the master and the API's address set"
    dry_run_note "write $env"
    return 0
  fi
  {
    printf '# Written by sandbox.sh services for sandbox %s: %s, with the settings below it\n' \
      "$name" "$DEV_PROFILE"
    printf '# changes. Written again on every run; edit the profile, or the script, instead.\n'
    render_properties "$root/$DEV_PROFILE" \
      "database.url=$DB_URL" \
      "monitoring.elasticsearch.url=localhost" \
      "cluster.nodeup.script=$root/$NODE_SCRIPTS_DIR/nodeup.py" \
      "cluster.nodedown.script=$root/$NODE_SCRIPTS_DIR/nodedown.py" \
      "cluster.reassign.script=$root/$NODE_SCRIPTS_DIR/node_reassign.py" \
      "cluster.node.terminate.script=$root/$NODE_SCRIPTS_DIR/terminate_node.py" \
      "api.host=$url/restapi/" \
      "launch.script.url.linux=$url/launch.sh" \
      "init.script.url.linux=$url/init.sh" \
      "launch.script.url.windows=$url/launch.py" \
      "kube.master.ip=$ip:$KUBE_API_PORT" \
      "kube.kubeadm.token=\${CP_KUBE_KUBEADM_TOKEN}" \
      "kube.kubeadm.cert.hash=$(state_get "$name" kube_ca_cert_hash)" \
      "kube.node.token=$KUBE_NODE_TOKEN_PLACEHOLDER" \
      "kube.cluster.name=$cluster"
  } >"$properties"
  cat >"$env" <<EOF
SANDBOX_DIR=$(state_dir "$name")
SANDBOX_VPC_CIDRS=$(state_get "$name" vpc_cidrs)
SANDBOX_SOCKS_PROXY=$(tunnel_socks "$name")
SANDBOX_MASTER_IP=$ip
PYTHONPATH=$root/$PIPE_COMMON_DIR
EOF
}

# service_settings <service>: what the sandbox changes in an optional service's settings, a
# key=value a line: the API on the sandbox's network, with the admin's token, which the service's
# container exports; the Elasticsearch there; and the database, for the search service, which
# reads the API's. Billing runs on the schedule of the platform's image. The search service's NFS
# events synchronizer is off, as the platform's search deployment has it
# (deploy/contents/k8s/cp-search/cp-search-srv-dpl.yaml): on, it needs a bucket of events, and the
# module's settings name none, so the service fails to start.
service_settings() {
  local api elasticsearch schedule
  api=$(services_api_url "$SERVICES_API_HOST")
  # shellcheck disable=SC2016 # a placeholder of the service's settings, not this shell's
  printf '%s\n' "cloud.pipeline.host=$api" 'cloud.pipeline.token=${CP_API_JWT_ADMIN}'
  elasticsearch=("elasticsearch.client.url=$ELASTICSEARCH_HOST" "elasticsearch.client.port=$ELASTICSEARCH_PORT"
    elasticsearch.client.scheme=http)
  case $1 in
    # Run billing, which the module's settings leave off; storage billing stays off, as there.
    billing)
      schedule=$(billing_schedule)
      printf '%s\n' "${elasticsearch[@]}" sync.run.disable=false "sync.billing.schedule=$schedule"
      ;;
    search) printf '%s\n' "${elasticsearch[@]}" "database.url=$DB_URL" sync.nfs-file.observer.sync.disable=true ;;
    monitoring)
      printf '%s\n' "es.host=$ELASTICSEARCH_HOST" "es.port=$ELASTICSEARCH_PORT" \
        "platform.usage.credits.monitor.execution.timestamp.file=$MONITORING_CREDITS_FILE"
      ;;
  esac
}

# billing_schedule: sync.billing.schedule in the billing service's image, the default of a
# ${VARIABLE:default} there; the last of the key's lines, as Java's properties take.
billing_schedule() {
  local file=$SANDBOX_REPO_ROOT/$BILLING_DEPLOY_SETTINGS schedule
  [[ -f $file ]] || die "$BILLING_DEPLOY_SETTINGS is not in $SANDBOX_REPO_ROOT"
  schedule=$(sed -n 's/^sync\.billing\.schedule=//p' "$file" | tail -n 1)
  if [[ $schedule =~ ^\$\{[A-Za-z0-9_]+:(.*)\}$ ]]; then
    schedule=${BASH_REMATCH[1]}
  fi
  [[ -n $schedule && $schedule != *\$\{* ]] ||
    die "$BILLING_DEPLOY_SETTINGS in $SANDBOX_REPO_ROOT gives sync.billing.schedule no value"
  printf '%s\n' "$schedule"
}

# write_service_settings <name> <service>: <service>.properties, the settings of the service's
# module with what the sandbox changes.
write_service_settings() {
  local name=$1 service=$2 file module lines settings
  file=$(service_properties_file "$name" "$service")
  module=$(service_module "$service")
  if dry_run; then
    case $service in
      billing) lines="the API and its Elasticsearch set, and the schedule of $BILLING_DEPLOY_SETTINGS" ;;
      search) lines="the API, its Elasticsearch and its database set, and the NFS events synchronizer off" ;;
      *) lines="the API and its Elasticsearch set" ;;
    esac
    dry_run_note "write $file: $module/$SERVICE_SETTINGS with $lines"
    return 0
  fi
  [[ -f $SANDBOX_REPO_ROOT/$module/$SERVICE_SETTINGS ]] || die "$module/$SERVICE_SETTINGS is not in $SANDBOX_REPO_ROOT"
  # Not read from a process substitution, whose failure would go unnoticed.
  lines=$(service_settings "$service")
  mapfile -t settings <<<"$lines"
  {
    printf '# Written by sandbox.sh services for sandbox %s: %s, with the settings below it\n' \
      "$name" "$module/$SERVICE_SETTINGS"
    printf '# changes. Written again on every run; edit the module, or the script, instead.\n'
    render_properties "$SANDBOX_REPO_ROOT/$module/$SERVICE_SETTINGS" "${settings[@]}"
  } >"$file"
}

cmd_services_prepare() {
  local name cidrs zone source base service
  services_options "$@"
  name=$SERVICES_NAME
  services_master "$name"

  cidrs=$(vpc_cidrs)
  [[ -n $cidrs ]] || die "$CP_SANDBOX_VPC_ID has no IPv4 address range"
  check_docker_subnets "$cidrs" "$SERVICES_DOCKER_SUBNETS"
  zone=$(subnet_zone)
  ensure_key_pair "$name"
  source=$(distribution_source)
  log "Sandbox $name: the runs download pipe-common and pipe from $source."
  install_master_services "$name" "$MASTER_ID" "$MASTER_IP" "$source"
  fetch_registry_certificate "$name" "$MASTER_ID"
  ensure_api_tls "$name" "$MASTER_IP"
  ensure_jwt_keys "$name"
  base=$(ensure_run_id_base "$name")
  state_set "$name" vpc_cidrs "$cidrs" subnet_zone "$zone" distribution_source "$source" \
    distribution_url "http://$MASTER_IP:$DISTRIBUTION_PORT/" registry_path "$MASTER_IP:$REGISTRY_PORT" \
    api_url "$(services_api_url "$MASTER_IP")" run_id_base "$base"
  write_api_settings "$name" "$MASTER_IP"
  log "Sandbox $name: wrote the API's settings, $(api_properties_file "$name")."
  for service in $SERVICES_WITH; do
    write_service_settings "$name" "$service"
    log "Sandbox $name: wrote the settings of the $service service, $(service_properties_file "$name" "$service")."
  done
}

# services-sequences: the SQL that moves the sequences of run and pool node IDs into the sandbox's
# block, and leaves one already there as it is.
cmd_services_sequences() {
  local name base sequence
  services_options "$@"
  name=$SERVICES_NAME
  base=$(state_get "$name" run_id_base)
  if [[ -z $base ]] && dry_run; then
    base=$RUN_ID_FLOOR
  fi
  [[ $base =~ ^[0-9]+$ ]] || die "the local state of sandbox $name has no run ID block: run services"
  for sequence in "${RUN_ID_SEQUENCES[@]}"; do
    printf "SELECT setval('%s', %s, false) WHERE (SELECT last_value FROM %s) < %s;\n" \
      "$sequence" "$base" "$sequence" "$base"
  done
}

# api_headers <name>: a file holding the API's authorization header, with the admin token, which
# goes away with the container.
api_headers() {
  local file token
  file=$HOME/.cp-sandbox-api-headers
  if [[ ! -f $file ]]; then
    [[ -f $(admin_token_file "$1") ]] || die "sandbox $1 has no $(basename "$(admin_token_file "$1")"): run services"
    token=$(tr -d '[:space:]' <"$(admin_token_file "$1")")
    (umask 077 && printf 'Authorization: Bearer %s\n' "$token" >"$file")
  fi
  printf '%s\n' "$file"
}

# api_call <dry-run output> <method> <path> [<json body>]: a call to the API's REST API, on the
# sandbox's Docker network, as the admin; writes the payload of the result. Fails, with the API's
# message, unless the API answers 200 with the status OK. Under --dry-run, prints the call, with
# no token, and writes <dry-run output>.
api_call() {
  local output=$1 method=$2 path=$3 body=${4-} url response code
  local -a data=()
  url=$(services_api_url "$SERVICES_API_HOST")$path
  [[ -z $body ]] || data=(--data "$body")
  if dry_run; then
    dry_run_note "$(print_command curl --request "$method" "$url" ${data[@]+"${data[@]}"})"
    printf '%s\n' "$output"
    return 0
  fi
  response=$(curl --silent --show-error --max-time 60 --cacert "$(api_tls_cert_file "$SERVICES_NAME")" \
    --header "@$(api_headers "$SERVICES_NAME")" --header 'Content-Type: application/json' \
    --request "$method" ${data[@]+"${data[@]}"} --write-out '\n%{http_code}' "$url") ||
    die "$method $url: the API did not answer"
  code=${response##*$'\n'}
  response=${response%$'\n'*}
  if [[ $code != 200 ]] || ! jq -e '.status == "OK"' <<<"$response" >/dev/null 2>&1; then
    die "$method $url: HTTP $code, $(jq -r '.message // empty' <<<"$response" 2>/dev/null || true)$(
      jq -e . <<<"$response" >/dev/null 2>&1 || printf '%s' "${response:0:300}")"
  fi
  jq -c '.payload // null' <<<"$response"
}

# secrets_blanked: the JSON it reads, with the string value of every key that names a secret
# blanked out, a run's sshPassword and the API_TOKEN among its envVars say, and the value of every
# {name, value} pair whose name does, as an environment variable or a preference.
secrets_blanked() {
  jq --arg pattern "$SECRET_KEY_PATTERN" 'walk(if type == "object" then
    with_entries(if (.key | test($pattern; "i")) and (.value | type == "string")
      then .value = "<blanked>" else . end)
    | if (.name | type == "string") and (.value | type == "string") and (.name | test($pattern; "i"))
      then .value = "<blanked>" else . end
    else . end)'
}

# services-api --name <name> [--jq <filter>] <method> <path> [<json body>]: one call to the API's
# REST API as the admin, at <path> under the REST root, for a person or an agent to drive the API
# by without the token. Writes the payload, its secrets blanked out, through the jq filter. The
# filter runs with an empty environment: jq's $ENV and env would read the config's credentials.
cmd_services_api() {
  local name='' filter=. method path body
  local -a words=()
  while (($#)); do
    case $1 in
      --name) name=$(option_value "$1" "${2-}"); shift 2 ;;
      --jq) filter=$(option_value "$1" "${2-}"); shift 2 ;;
      *) words+=("$1"); shift ;;
    esac
  done
  [[ -n $name ]] || die "--name <name> is required"
  validate_name "$name"
  ((${#words[@]} == 2 || ${#words[@]} == 3)) || die "usage: sandbox.sh api <method> <path> [<json body>]"
  method=${words[0]} path=${words[1]#/} body=${words[2]-}
  [[ $method =~ ^(GET|POST|PUT|DELETE)$ ]] || die "the method is GET, POST, PUT or DELETE, not $method"
  if [[ -n $body ]]; then
    jq . >/dev/null 2>&1 <<<"$body" || die "the body is not JSON"
  fi
  SERVICES_NAME=$name
  api_call null "$method" "$path" "$body" | secrets_blanked | env -i "PATH=$PATH" jq -r "$filter"
}

# services-wait: until the API answers its admin, up to API_WAIT_SECONDS.
cmd_services_wait() {
  local deadline=$((SECONDS + API_WAIT_SECONDS)) user
  services_options "$@"
  log "Waiting for the API at $(services_api_url "$SERVICES_API_HOST"), up to $((API_WAIT_SECONDS / 60)) minutes."
  until user=$( (api_call "{\"userName\": \"$ADMIN_USER\"}" GET whoami) 2>/dev/null); do
    ((SECONDS < deadline)) || die "the API did not answer at $(services_api_url "$SERVICES_API_HOST") \
in $((API_WAIT_SECONDS / 60)) minutes"
    sleep "$API_POLL_SECONDS"
  done
  log "The API answers, as $(jq -r '.userName // "?"' <<<"$user")."
}

# networks_config <name>: the cluster.networks.config preference: the nodes start from the host
# image in the configured subnet and security groups, with the worker instance profile, the AL2023
# init script and the sandbox's tags.
networks_config() {
  local name=$1 tags
  tags=$(sandbox_tags "$name" worker "$(state_get "$name" expires)" | jq -c 'map(select(.Key != "Name")) | from_entries')
  jq -cn --arg region "$SANDBOX_REGION" --arg zone "$(recorded "$name" subnet_zone)" \
    --arg subnet "$CP_SANDBOX_SUBNET_ID" --arg groups "$CP_SANDBOX_SECURITY_GROUP_IDS" \
    --arg ami "$(state_get "$name" ami)" --arg init "$SANDBOX_REPO_ROOT/$NODE_INIT_SCRIPT" \
    --argjson profile "$(instance_profile_spec "$CP_SANDBOX_WORKER_INSTANCE_PROFILE")" \
    --argjson tags "$tags" '{
      regions: [{
        name: $region, default: true,
        networks: {($zone): $subnet},
        security_group_ids: ($groups | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(length > 0))),
        amis: [{platform: "linux", instance_mask: "*", ami: $ami, init_script: $init,
                additional_spec: {IamInstanceProfile: $profile}}],
        tags: $tags
      }],
      tags: $tags
    }'
}

# preferences <name>: the preferences the sandbox sets, as the API's POST /preferences takes them,
# in two lists: cluster.instance.type is checked against cluster.allowed.instance.types, so that
# one goes first.
preferences() {
  local name=$1 networks
  # An assignment of its own, which errexit sees fail; as an argument, it would not.
  networks=$(networks_config "$name")
  jq -cn --arg type "$CP_SANDBOX_WORKER_INSTANCE_TYPE" '[
    {name: "cluster.allowed.instance.types", value: $type, visible: true},
    {name: "cluster.allowed.instance.types.docker", value: $type, visible: true}]'
  jq -cn --arg type "$CP_SANDBOX_WORKER_INSTANCE_TYPE" --arg networks "$networks" \
    --arg max "$CLUSTER_MAX_SIZE" --arg policy "$CLUSTER_KEEP_ALIVE_POLICY" \
    --arg minutes "$CLUSTER_KEEP_ALIVE_MINUTES" --arg api "$(recorded "$name" api_url)" \
    --arg distribution "$(recorded "$name" distribution_url)" '[
    {name: "cluster.networks.config", value: $networks, visible: true},
    {name: "cluster.instance.type", value: $type, visible: true},
    {name: "cluster.max.size", value: $max, visible: true},
    {name: "cluster.spot", value: "false", visible: true},
    {name: "cluster.keep.alive.policy", value: $policy, visible: true},
    {name: "cluster.keep.alive.minutes", value: $minutes, visible: true},
    {name: "base.api.host", value: $api, visible: true},
    {name: "base.pipe.distributions.url", value: $distribution, visible: true}]'
}

# installer_search_lines: the search preferences the platform's installer sets, as
# INSTALLER_SEARCH_FUNCTION in INSTALLER_UTILS has them, one a line, its fields separated by tabs:
# "pref", the name, the value as written, and whether it is visible; and "doc", the variable, and a
# line of the here-document the function reads into that variable.
installer_search_lines() {
  awk -v function_name="$INSTALLER_SEARCH_FUNCTION" '
    $0 ~ "^function " function_name "[[:space:]]*[{]" { inside = 1; next }
    !inside { next }
    doc && /^[[:space:]]*EOF[[:space:]]*$/ { doc = 0; next }
    doc { sub(/^\t+/, ""); print "doc\t" variable "\t" $0; next }
    /^}/ { exit }
    /<<-?EOF[[:space:]]*$/ { count = split($0, words, /[[:space:]]+/); variable = words[count - 1]; doc = 1; next }
    match($0, /api_set_preference "search[.]elastic[.][^"]*" "[^"]*" "(true|false)"/) {
      split(substr($0, RSTART, RLENGTH), words, "\"")
      print "pref\t" words[2] "\t" words[4] "\t" words[6]
    }' "$1"
}

# search_preferences: the API's search preferences, as the platform's installer sets them, in the
# form the API's POST /preferences takes: the installer's values, and the sandbox's Elasticsearch
# where the installer takes the scheme, the host or the port from its config. Of the rest the
# installer takes from its config, each is left as the API has it, and named.
search_preferences() {
  local file=$SANDBOX_REPO_ROOT/$INSTALLER_UTILS lines preferences left
  [[ -f $file ]] || die "$INSTALLER_UTILS is not in $SANDBOX_REPO_ROOT"
  lines=$(installer_search_lines "$file")
  [[ $lines == *$'pref\t'* ]] || die "$INSTALLER_SEARCH_FUNCTION in $INSTALLER_UTILS sets no search preference"
  preferences=$(jq -cRs --arg host "$ELASTICSEARCH_HOST" --arg port "$ELASTICSEARCH_PORT" '
    split("\n") | map(select(length > 0) | split("\t")) as $rows
    | ($rows | map(select(.[0] == "doc")) | group_by(.[1])
       | map({key: ("$" + .[0][1]), value: (map(.[2]) | join("\n") | fromjson | tojson)})
       | from_entries) as $documents
    | {"search.elastic.scheme": "http", "search.elastic.host": $host, "search.elastic.port": $port} as $own
    | $rows | map(select(.[0] == "pref") | {name: .[1], value: ($documents[.[2]] // .[2]), visible: (.[3] == "true")})
    | map(if (.value | contains("$")) and $own[.name] then .value = $own[.name] else . end)
    | reduce .[] as $preference ({}; .[$preference.name] = $preference) | [.[]]' <<<"$lines") ||
    die "could not read the search preferences of $INSTALLER_SEARCH_FUNCTION in $INSTALLER_UTILS"
  left=$(jq -r 'map(select(.value | contains("$")) | .name) | join(", ")' <<<"$preferences")
  [[ -z $left ]] || log "Left as the API has them, since the installer sets them from its config: $left."
  jq -c 'map(select(.value | contains("$") | not))' <<<"$preferences"
}

# ensure_region: the configured region, as the platform's default one, with the sandbox's key pair
# for its nodes. Writes its ID.
ensure_region() {
  local name=$1 regions id
  regions=$(api_call '[]' GET cloud/region)
  id=$(jq -r --arg region "$SANDBOX_REGION" '[.[]? | select(.regionId == $region)][0].id // empty' <<<"$regions")
  if [[ -z $id ]]; then
    id=$(api_call '{"id": 1}' POST cloud/region "$(jq -cn --arg region "$SANDBOX_REGION" \
      --arg key "$(recorded "$name" key_pair)" '{provider: "AWS", regionId: $region, name: $region,
        default: true, sshKeyName: $key, mountStorageRule: "NONE", mountFileStorageRule: "NONE",
        mountCredentialsRule: "NONE"}')" | jq -r '.id')
    log "Created the cloud region $SANDBOX_REGION, ID $id."
  fi
  printf '%s\n' "$id"
}

# registry_tree: the registries, their tool groups and tools, as the API lists them. Under
# --dry-run, as a fresh API lists them: none.
registry_tree() {
  api_call '{"registries": []}' GET dockerRegistry/loadTree
}

# ensure_registry <name>: the master's registry, its tool group and its tool, each registered once.
ensure_registry() {
  local name=$1 path tree registry group tool cert
  path=$(recorded "$name" registry_path)
  tree=$(registry_tree)
  registry=$(jq -r --arg path "$path" '[.registries[]? | select(.path == $path)][0].id // empty' <<<"$tree")
  if [[ -z $registry ]]; then
    cert=$(registry_cert_file "$name")
    if [[ -f $cert ]]; then
      cert=$(<"$cert")
    elif dry_run; then
      cert="<$(basename "$cert")>"
    else
      die "sandbox $name has no $(basename "$cert"): run services"
    fi
    registry=$(api_call '{"id": 1}' POST dockerRegistry/register "$(jq -cn --arg path "$path" \
      --arg cert "$cert" --arg description "The registry on the master of the dev sandbox $name" \
      '{path: $path, description: $description, caCert: $cert, pipelineAuth: false,
        securityScanEnabled: false}')" | jq -r '.id')
    log "Registered the registry $path, ID $registry."
  fi
  group=$(jq -r --arg path "$path" --arg group "$TOOL_GROUP" \
    '[.registries[]? | select(.path == $path) | .groups[]? | select(.name == $group)][0].id // empty' <<<"$tree")
  if [[ -z $group ]]; then
    group=$(api_call '{"id": 1}' POST toolGroup "$(jq -cn --arg group "$TOOL_GROUP" --argjson registry "$registry" \
      '{name: $group, registryId: $registry, description: "The dev sandbox'"'"'s tools"}')" | jq -r '.id')
    log "Created the tool group $TOOL_GROUP, ID $group."
  fi
  tool=$(jq -r --arg path "$path" --arg group "$TOOL_GROUP" --arg image "$TOOL_REPOSITORY" \
    '[.registries[]? | select(.path == $path) | .groups[]? | select(.name == $group) | .tools[]?
      | select(.image == $image)][0].id // empty' <<<"$tree")
  if [[ -z $tool ]]; then
    api_call '{"id": 1}' POST tool/register "$(jq -cn --arg image "$TOOL_REPOSITORY" --argjson group "$group" \
      '{image: $image, toolGroupId: $group, cpu: "1", ram: "1Gi", defaultCommand: "sleep 30",
        description: "CentOS 7, the tool the dev sandbox runs"}')" >/dev/null
    log "Registered the tool $TOOL_REPOSITORY, whose tag $TOOL_TAG the master's registry holds."
  fi
}

# services-configure: the region, the preferences, the registry and the tool, once the API answers;
# then what the API reads back of them. With the search or the billing service, the API's search
# preferences too, which its searches and its billing reports read the Elasticsearch by.
cmd_services_configure() {
  local name region lists list names preference value tree service
  services_options "$@"
  name=$SERVICES_NAME
  use_sandbox_region "$name"
  region=$(ensure_region "$name")
  # Made first, so that a failure stops the command rather than the loop.
  lists=$(preferences "$name")
  if with_service search billing; then
    lists+=$'\n'$(search_preferences)
  fi
  names=$(jq -r '.[].name' <<<"$lists")
  while IFS= read -r list; do
    api_call '[]' POST preferences "$list" >/dev/null
  done <<<"$lists"
  ensure_registry "$name"

  if dry_run; then
    printf '[dry-run] Nothing was configured. Without --dry-run:\n'
  fi
  cat <<EOF
Sandbox $name: the API runs here, in the container $(service_container "$name" api).
  api           $(services_api_url "$SERVICES_API_HOST") on the network $(sandbox_network "$name")
                $(recorded "$name" api_url) from the master and the nodes
  admin token   $(admin_token_file "$name"), for $ADMIN_USER
  settings      $(api_properties_file "$name")
  region        $SANDBOX_REGION, ID $region
  registry      $(recorded "$name" registry_path), with $TOOL_REPOSITORY:$TOOL_TAG
  run IDs       from $(recorded "$name" run_id_base)
  distribution  $(recorded "$name" distribution_url), from $(recorded "$name" distribution_source)
  log           sandbox.sh services --log
EOF
  if [[ -n $SERVICES_WITH ]]; then
    printf '  %-13s http://%s:%s on the network, in the container %s\n' elasticsearch \
      "$ELASTICSEARCH_HOST" "$ELASTICSEARCH_PORT" "$(service_container "$name" elasticsearch)"
  fi
  for service in $SERVICES_WITH; do
    printf '  %-13s the container %s, with %s; log: sandbox.sh services --log %s\n' "$service" \
      "$(service_container "$name" "$service")" "$(service_properties_file "$name" "$service")" "$service"
  done
  printf 'Preferences, as the API reads them back:\n'
  while IFS= read -r preference; do
    value=$(api_call "{\"name\": \"$preference\", \"value\": \"<dry-run>\"}" GET "preferences/$preference" | jq -r '.value // ""')
    if [[ $preference == cluster.networks.config ]]; then
      value=$(jq -c . <<<"$value" 2>/dev/null || printf '%s' "$value")
    fi
    printf '  %s = %s\n' "$preference" "$value"
  done <<<"$names"
  tree=$(registry_tree)
  printf 'Registries, tool groups and tools, as the API lists them:\n'
  jq -r '.registries[]? | "  \(.path) (ID \(.id))", (.groups[]? | "    \(.name): \([.tools[]?.image] | join(", "))")' <<<"$tree"
}
