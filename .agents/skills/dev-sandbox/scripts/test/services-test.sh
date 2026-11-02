#!/usr/bin/env bash
# shellcheck source-path=SCRIPTDIR
#
# Checks of the steps of services in the tools image — services-prepare, services-sequences,
# services-wait and services-configure — and of what down says and does of the sandbox's key pair,
# with AWS, Run Command, SSH and the API's REST API replaced by the stubs in stubs.sh and here.
# openssl is real. Runs in the tools image, started by sandbox-test.sh with a throw-away state
# directory; it makes no AWS call.
set -euo pipefail
# As in-tools.sh, which the commands run under.
shopt -s inherit_errexit

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
# shellcheck source=stubs.sh
. "$TEST_DIR/stubs.sh"

# The checkout the tests run from, as tools.sh passes it in, for the files read as they are there.
REAL_REPO_ROOT=${SANDBOX_REPO_ROOT:?is set by tools.sh}
SANDBOX_REPO_ROOT=$STUB_DIR/repo
API_CALLS=$STUB_DIR/api-calls
LISTING_DIR=$STUB_DIR/listing
PROFILE=$SANDBOX_REPO_ROOT/$DEV_PROFILE
API_WAIT_SECONDS=0
API_POLL_SECONDS=0

# The dev profile, as api/profiles/dev/application.properties has the keys services changes, and
# the settings of the optional services' modules, with the keys services changes in any of them.
fake_repo() {
  local service module
  mkdir -p "$(dirname "$PROFILE")"
  cat >"$PROFILE" <<'EOF'
#tomcat configuration
server.port=9999
database.url=jdbc:postgresql://localhost:5432/pipeline
monitoring.elasticsearch.url=
cluster.nodeup.script=
cluster.nodedown.script=
cluster.reassign.script=
cluster.node.terminate.script=
api.host=
launch.script.url.linux=
init.script.url.linux=
launch.script.url.windows=
commit.run.scripts.root.url=
commit.run.script.starter.url=
container.layers.script.url=
container.size.script.url=
limit.run.bandwidth.script.url=
pause.run.script.url=
jwt.key.public=${CP_API_JWT_KEY_PUBLIC}
kube.master.ip=
kube.kubeadm.token=
kube.kubeadm.cert.hash=
kube.node.token=
kube.cluster.name=
EOF
  for service in $OPTIONAL_SERVICES; do
    module=$(service_module "$service")
    mkdir -p "$SANDBOX_REPO_ROOT/$module/$(dirname "$SERVICE_SETTINGS")"
    cat >"$SANDBOX_REPO_ROOT/$module/$SERVICE_SETTINGS" <<EOF
# The settings of $module
cloud.pipeline.host=
cloud.pipeline.token=
elasticsearch.client.url=localhost
elasticsearch.client.port=9200
elasticsearch.client.scheme=http
database.url=
sync.run.disable=true
sync.storage.s3.disable=true
es.host=\${CP_HEAPSTER_ELK_INTERNAL_HOST:cp-heapster-elk.default.svc.cluster.local}
es.port=30094
platform.usage.credits.monitor.execution.timestamp.file=/opt/monitoring/last.txt
sync.billing.schedule=0 */3 * ? * *
EOF
  done
  mkdir -p "$(dirname "$SANDBOX_REPO_ROOT/$BILLING_DEPLOY_SETTINGS")"
  cat >"$SANDBOX_REPO_ROOT/$BILLING_DEPLOY_SETTINGS" <<'EOF'
sync.billing.center.key=${CP_BILLING_CENTER_KEY:billing-group}
sync.billing.schedule=${CP_BILLING_SYNC_SCHEDULE:0 30 1 ? * *}
EOF
}

# up_state <name>: the local state up and connect leave for a master that is installed.
up_state() {
  state_set "$1" name "$1" region eu-west-1 instance_id i-master private_ip 10.0.0.7 \
    ami ami-host expires 2030-01-01T00:00:00Z kube_ca_cert_hash "$FAKE_HASH" \
    tunnel_socks "cp-sandbox-$1-master:1080"
  : >"$(ssh_key_file "$1")"
  printf 'ssh-ed25519 AAAAfake cp-sandbox-%s\n' "$1" >"$(ssh_key_file "$1").pub"
  admin_conf >"$(kubeconfig_file "$1")"
  printf '%s\n' "$FAKE_TOKEN" >"$(join_token_file "$1")"
}

# The API's REST API, stubbed: records "<method> <path> <body>", and answers from the api queue for
# that method and path, or from API_ANSWERS, else with null; an answer written !<message> fails, as
# a call the API refuses does. The real one stays as real_api_call.
eval "real_api_call() $(declare -f api_call | sed 1d)"
declare -A API_ANSWERS=()
api_call() {
  local answer
  printf '%s %s %s\n' "$2" "$3" "${4-}" >>"$API_CALLS"
  answer=$(next_answer "api.${2}.${3//\//_}" "${API_ANSWERS["$2 $3"]-null}")
  if [[ $answer == '!'* ]]; then
    die "$2 $3: ${answer#!}"
  fi
  printf '%s\n' "$answer"
}

api_called() {   # api_called <method path prefix>: the recorded calls starting with it
  grep -F -- "$1" "$API_CALLS" || true
}

# The S3 listing of the platform's builds, as curl would fetch it.
curl() {
  cat "$LISTING_DIR/listing.xml"
}

test_render_properties() {
  local rendered
  fake_repo
  rendered=$(render_properties "$PROFILE" "database.url=jdbc:postgresql://db:5432/pipeline" \
    "api.host=https://10.0.0.7:9999/pipeline/restapi/" "new.key=added")
  check "render_properties sets a key the profile has where the profile has it" \
    "$(grep -n '^database.url=' <<<"$rendered")" '3:database.url=jdbc:postgresql://db:5432/pipeline'
  # shellcheck disable=SC2016 # the placeholder is the profile's, not this shell's
  check "and keeps the profile's other lines, comments and placeholders as they are" \
    "$(grep -c -e '^#tomcat configuration$' -e '^server.port=9999$' -e '^jwt.key.public=${CP_API_JWT_KEY_PUBLIC}$' <<<"$rendered")" 3
  check "and adds a key the profile does not have at the end, once" \
    "$(tail -n 1 <<<"$rendered") $(grep -c '^api.host=' <<<"$rendered")" 'new.key=added 1'
}

test_cidrs_overlap() {
  local pairs=(
    "172.31.0.0/16 172.31.5.0/24 yes" "172.31.0.0/16 172.18.0.0/16 no" "10.0.0.0/8 10.255.255.0/24 yes"
    "172.31.0.0/16 172.30.255.255/32 no" "172.31.0.0/16 172.32.0.0/16 no" "192.168.0.0/20 192.168.15.1/32 yes")
  local pair actual=''
  for pair in "${pairs[@]}"; do
    read -r first second _ <<<"$pair"
    if cidrs_overlap "$first" "$second"; then actual+="yes "; else actual+="no "; fi
  done
  check "cidrs_overlap tells ranges that share an address from those that do not" "$actual" 'yes no yes no no yes '
  SERVICES_NAME=dev-svc
  run_command check_docker_subnets "10.0.0.0/16 10.1.0.0/16" "172.18.0.0/16 fd00::/64"
  check "check_docker_subnets passes a Docker network outside the VPC, and skips IPv6" "$RUN_STATUS" 0
  run_command check_docker_subnets "10.0.0.0/16 10.1.0.0/16" "10.1.128.0/20"
  check "check_docker_subnets refuses a Docker network inside the VPC, and says what to do" \
    "$RUN_STATUS $(logged 'has the addresses 10.1.128.0/20, which overlap the VPC'"'"'s 10.1.0.0/16') $(logged 'docker network create --label cp-sandbox=dev-svc --subnet')" \
    '1 1 1'
}

test_newest_distribution() {
  local bucket=https://cloud-pipeline-oss-builds.s3.amazonaws.com
  mkdir -p "$LISTING_DIR"
  printf '%s' '<ListBucketResult><Contents><Key>builds/develop/cloud-pipeline.0.17.0.22593.a249.tgz</Key><LastModified>2026-10-12T16:37:39.000Z</LastModified><Size>1</Size></Contents><Contents><Key>builds/develop/pipectl.0.17.0.22596.c800</Key><LastModified>2026-10-13T14:43:23.000Z</LastModified></Contents><Contents><Key>builds/develop/cloud-pipeline.0.17.0.22596.c800.tgz</Key><LastModified>2026-10-13T13:51:47.000Z</LastModified></Contents><Contents><Key>builds/develop/cloud-pipeline.0.17.0.9999.f00.tgz</Key><LastModified>2026-01-01T00:00:00.000Z</LastModified></Contents></ListBucketResult>' \
    >"$LISTING_DIR/listing.xml"
  check "the newest build under the prefix is the one published last, of the distributions only" \
    "$(distribution_source)" "$bucket/builds/develop/cloud-pipeline.0.17.0.22596.c800.tgz"
  check "CP_SANDBOX_DISTRIBUTION_URL names another" \
    "$(CP_SANDBOX_DISTRIBUTION_URL=https://example.com/cloud-pipeline.1.tgz distribution_source)" \
    https://example.com/cloud-pipeline.1.tgz
  printf '%s' '<ListBucketResult><Contents><Key>builds/develop/pipectl.0.17.0.1.abc</Key><LastModified>2026-10-13T14:43:23.000Z</LastModified></Contents></ListBucketResult>' \
    >"$LISTING_DIR/listing.xml"
  run_command distribution_source
  check "a listing without a distribution fails, and says where it looked" \
    "$RUN_STATUS $(logged "no cloud-pipeline.<version>.tgz under $bucket/builds/develop/")" '1 1'
}

test_key_pair() {
  local tags
  reset_stubs
  up_state dev-svc
  run_command ensure_key_pair dev-svc
  check "a sandbox without a key pair gets one, its SSH key's public half, tagged as the sandbox's" \
    "$RUN_STATUS $(called 'ec2 import-key-pair' | grep -c -- "--key-name cp-sandbox-dev-svc --public-key-material fileb://$(ssh_key_file dev-svc).pub" || true)" '0 1'
  tags=$(called 'ec2 import-key-pair' | sed -E "s/.*--tag-specifications (\[.*\]) --query.*/\1/")
  check "the key pair carries the sandbox's tags, role worker, and its expiry" \
    "$(jq -c '.[0].ResourceType, (.[0].Tags | from_entries | [.["cp-sandbox"], .["cp-sandbox-owner"], .["cp-sandbox-role"], .["cp-sandbox-expires"]])' <<<"$tags" | paste -sd ' ')" \
    "\"key-pair\" [\"dev-svc\",\"$ME\",\"worker\",\"2030-01-01T00:00:00Z\"]"
  check "and the state names it" "$(state_get dev-svc key_pair)" cp-sandbox-dev-svc

  STUB_OUTPUT["ec2 describe-key-pairs"]=$(jq -cn --arg me "$ME" '[[{Key: "cp-sandbox", Value: "dev-svc"}, {Key: "cp-sandbox-owner", Value: $me}]]')
  run_command ensure_key_pair dev-svc
  check "the sandbox's own key pair is kept" "$RUN_STATUS $(called 'ec2 import-key-pair')" '0 '
  STUB_OUTPUT["ec2 describe-key-pairs"]='[[{"Key": "cp-sandbox", "Value": "dev-svc"}, {"Key": "cp-sandbox-owner", "Value": "someone@example.com"}]]'
  run_command ensure_key_pair dev-svc
  check "a key pair of the name that is not the sandbox's own is refused" \
    "$RUN_STATUS $(logged 'the EC2 key pair cp-sandbox-dev-svc is there, but not tagged') $(called 'ec2 import-key-pair')" '1 1 '
  state_remove dev-svc
  reset_stubs
}

test_services_prepare() {
  local name=dev-svc dir cert_before base_before
  reset_stubs
  fake_repo
  up_state "$name"
  dir=$(state_dir "$name")
  CP_SANDBOX_DISTRIBUTION_URL=https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz \
    run_command cmd_services_prepare --name "$name" --docker-subnets "172.18.0.0/16 "
  check "services-prepare succeeds" "$RUN_STATUS" 0
  check "and leaves the master to services-master" "$(wc -l <"$SSM_COMMANDS") $(wc -l <"$SSH_COMMANDS")" '0 0'
  check "it records the VPC, the zone, the key pair, the build, the registry and the API's address" \
    "$(jq -c '[.vpc_cidrs, .subnet_zone, .key_pair, .distribution_source, .distribution_url, .registry_path, .api_url]' "$(state_file "$name")")" \
    '["10.0.0.0/16 10.1.0.0/16","eu-west-1a","cp-sandbox-dev-svc","https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz","http://10.0.0.7:8090/","10.0.0.7:5000","https://10.0.0.7:9999/pipeline/restapi/"]'
  check "and a block of run IDs, above the floor, on a block's boundary" \
    "$(jq -r '.run_id_base | tonumber | (. >= 100000000 and . < 2100000000 and . % 100000 == 0)' "$(state_file "$name")")" true
  check "the API's certificate is for api and the master's private address, its key for its owner only" \
    "$(openssl x509 -in "$dir/api-tls.crt" -noout -checkip 10.0.0.7 | grep -c 'does match') $(openssl x509 -in "$dir/api-tls.crt" -noout -checkhost api | grep -c 'does match') $(stat -c %a "$dir/api-tls-key.pem")" \
    '1 1 600'
  check "the JWT key pair is RSA, the private half PKCS#8 and for its owner only" \
    "$(head -n 1 "$dir/jwt-private-key.pem") $(head -n 1 "$dir/jwt-public-key.pem") $(stat -c %a "$dir/jwt-private-key.pem")" \
    '-----BEGIN PRIVATE KEY----- -----BEGIN PUBLIC KEY----- 600'
  check "api.properties is the dev profile with the sandbox's database, node scripts, master, cluster name and addresses" \
    "$(grep -E '^(database.url|monitoring.elasticsearch.url|cluster.nodeup.script|cluster.node.terminate.script|api.host|launch.script.url.linux|init.script.url.linux|kube.master.ip|kube.kubeadm.token|kube.kubeadm.cert.hash|kube.node.token|kube.cluster.name)=' "$dir/api.properties" | paste -sd '|')" \
    "database.url=jdbc:postgresql://db:5432/pipeline|monitoring.elasticsearch.url=localhost|cluster.nodeup.script=$SANDBOX_REPO_ROOT/scripts/autoscaling/aws/nodeup.py|cluster.node.terminate.script=$SANDBOX_REPO_ROOT/scripts/autoscaling/aws/terminate_node.py|api.host=https://10.0.0.7:9999/pipeline/restapi/|launch.script.url.linux=https://10.0.0.7:9999/pipeline/launch.sh|init.script.url.linux=https://10.0.0.7:9999/pipeline/init.sh|kube.master.ip=10.0.0.7:6443|kube.kubeadm.token=\${CP_KUBE_KUBEADM_TOKEN}|kube.kubeadm.cert.hash=$FAKE_HASH|kube.node.token=not-used-by-linux-nodes|kube.cluster.name=cp-sandbox-dev-svc"
  check "and the scripts the API runs on a run's node over SSH, as the API serves them at the master's address, each set once" \
    "$(grep -E '^(commit.run.scripts.root.url|commit.run.script.starter.url|container.layers.script.url|container.size.script.url|limit.run.bandwidth.script.url|pause.run.script.url)=' "$dir/api.properties" | paste -sd '|')" \
    "commit.run.scripts.root.url=https://10.0.0.7:9999/pipeline/commit-run-scripts/|commit.run.script.starter.url=https://10.0.0.7:9999/pipeline/commit-run-scripts/commit_run_starter.sh|container.layers.script.url=https://10.0.0.7:9999/pipeline/commit-run-scripts/container_layers_count.sh|container.size.script.url=https://10.0.0.7:9999/pipeline/commit-run-scripts/container_size.sh|limit.run.bandwidth.script.url=https://10.0.0.7:9999/pipeline/commit-run-scripts/limit_bandwidth.sh|pause.run.script.url=https://10.0.0.7:9999/pipeline/commit-run-scripts/pause_run.sh"
  # shellcheck disable=SC2016 # $rootDir is the build file's, not this shell's
  check "each is a script of the checkout's scripts/commit-run-scripts/, which the API's build puts where the URLs name" \
    "$(sed -n -E 's#^[a-z.]+=https://10\.0\.0\.7:9999/pipeline/commit-run-scripts/(.+)$#\1#p' "$dir/api.properties" |
      while read -r script; do [[ -f $REAL_REPO_ROOT/scripts/commit-run-scripts/$script ]] && echo "$script"; done | wc -l | tr -d ' ') $(grep -c 'into "$rootDir/api/src/main/resources/static/commit-run-scripts"' "$REAL_REPO_ROOT/api/build.gradle")" \
    '5 1'
  check "api.properties and api.env hold no secret: the join token and the keys stay in their files" \
    "$(grep -c -e "$FAKE_TOKEN" -e "$(sed -n 2p "$dir/jwt-private-key.pem")" "$dir/api.properties" "$dir/api.env" "$(state_file "$name")" | cut -d: -f2 | paste -sd ' ')" \
    '0 0 0'
  check "api.env gives the container the state, the VPC, the SOCKS proxy, the master, where to keep the SSH key and pipe-common's code" \
    "$(paste -sd '|' "$dir/api.env")" \
    "SANDBOX_DIR=$dir|SANDBOX_VPC_CIDRS=10.0.0.0/16 10.1.0.0/16|SANDBOX_SOCKS_PROXY=cp-sandbox-dev-svc-master:1080|SANDBOX_MASTER_IP=10.0.0.7|SANDBOX_NODE_SSH_KEY=/root/.ssh/cp-sandbox-node-key.pem|PYTHONPATH=$SANDBOX_REPO_ROOT/workflows/pipe-common"

  cert_before=$(openssl x509 -in "$dir/api-tls.crt" -noout -fingerprint)
  base_before=$(state_get "$name" run_id_base)
  CP_SANDBOX_DISTRIBUTION_URL=https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz \
    run_command cmd_services_prepare --name "$name" --docker-subnets ""
  check "a second services-prepare keeps the keys, the certificate and the block of run IDs" \
    "$RUN_STATUS $([[ $(openssl x509 -in "$dir/api-tls.crt" -noout -fingerprint) == "$cert_before" ]] && echo kept) $(logged 'Made the API') $([[ $(state_get "$name" run_id_base) == "$base_before" ]] && echo same)" \
    '0 kept 0 same'

  state_set "$name" private_ip 10.0.0.8
  CP_SANDBOX_DISTRIBUTION_URL=https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz \
    run_command cmd_services_prepare --name "$name" --docker-subnets ""
  check "a master at another address gets a certificate for it" \
    "$RUN_STATUS $(openssl x509 -in "$dir/api-tls.crt" -noout -checkip 10.0.0.8 | grep -c 'does match')" '0 1'

  run_command cmd_services_prepare --name "$name" --docker-subnets "10.0.3.0/24"
  check "services-prepare refuses a Docker network inside the VPC, before it changes anything" \
    "$RUN_STATUS $(logged 'overlap the VPC') $(called 'ec2 import-key-pair')$(wc -l <"$SSM_COMMANDS")" '1 1 0'

  state_unset "$name" tunnel_socks
  run_command cmd_services_prepare --name "$name"
  check "services-prepare needs a tunnel that connect checked the SOCKS proxy of, before it changes anything" \
    "$RUN_STATUS $(logged 'has no SOCKS proxy, which the API reaches the VPC through: run sandbox.sh connect --stop, then sandbox.sh connect') $(wc -l <"$CALLS")" \
    '1 1 0'
  state_set "$name" tunnel_socks cp-sandbox-dev-svc-master:1080

  rm "$(join_token_file "$name")"
  run_command cmd_services_prepare --name "$name"
  check "services-prepare needs the join token up fetched" \
    "$RUN_STATUS $(logged 'has no kube-join-token.secret in its local state: run up')" '1 1'
  state_remove "$name"
  reset_stubs
}

# master_state <name>: the local state services-master starts from: what up, connect and
# services-prepare leave, and the registry's token the host mints.
master_state() {
  up_state "$1"
  state_set "$1" distribution_source https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz
  printf 'eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJub3RpZnkifQ.c2ln\n' >"$(registry_token_file "$1")"
  printf 'the API certificate\n' >"$(api_tls_cert_file "$1")"
}

test_services_master() {
  local name=dev-mst commands password
  reset_stubs
  master_state "$name"
  run_command cmd_services_master --name "$name"
  check "services-master succeeds" "$RUN_STATUS" 0
  check "it copies the registry's token to the master over SSH, for root only, and never through Run Command" \
    "$(grep -c "^sudo sh -c 'umask 077 && mkdir -p /etc/cp-sandbox/registry && cat >/etc/cp-sandbox/registry/notify-token.secret.part && mv -f /etc/cp-sandbox/registry/notify-token.secret.part /etc/cp-sandbox/registry/notify-token.secret'$" "$SSH_COMMANDS") $(cat "$STUB_DIR/ssh-file.notify-token.secret") $(grep -c 'eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJub3RpZnkifQ' "$SSM_COMMANDS" || true)" \
    '1 eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJub3RpZnkifQ.c2ln 0'
  commands=$(cat "$SSM_COMMANDS")
  check "it copies install-services.sh, the tool's Dockerfile and the API's certificate, then runs it with the address, the Dockerfile, the build, and where the registry notifies the API" \
    "$(grep -c '^# copy install-services.sh tool.Dockerfile api-tls.crt to /opt/cp-sandbox' <<<"$commands") $(grep -c 'bash /opt/cp-sandbox/install-services.sh 10.0.0.7 /opt/cp-sandbox/tool.Dockerfile https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz --notify-url https://10.0.0.7:9999/pipeline/restapi/dockerRegistry/notify --api-cert /opt/cp-sandbox/api-tls.crt >>/var/log/cp-sandbox/install-services.log' <<<"$commands")" \
    '1 1'
  check "it fetches the registry's certificate over SSH, as root" \
    "$(grep -c '^sudo cat /etc/cp-sandbox/registry/registry.crt$' "$SSH_COMMANDS") $(head -n 1 "$(registry_cert_file "$name")")" \
    '1 -----BEGIN CERTIFICATE-----'
  password=$(registry_password_file "$name")
  check "and its user's password, into the local state, for its owner only" \
    "$(grep -c '^sudo cat /etc/cp-sandbox/registry/password.secret$' "$SSH_COMMANDS") $(cat "$password") $(stat -c %a "$password")" \
    "1 $FAKE_REGISTRY_PASSWORD 600"
  check "the state says the runs get the build's pipe-common, and no output holds the token or the password" \
    "$(state_get "$name" distribution_pipe_common) $(logged 'eyJhbGciOiJSUzUxMiJ9.eyJzdWIiOiJub3RpZnkifQ') $(logged "$FAKE_REGISTRY_PASSWORD")" 'published 0 0'

  FAKE_REGISTRY_PASSWORD='not a password' run_command cmd_services_master --name "$name"
  check "a master that holds no password for the registry fails, and says so" \
    "$RUN_STATUS $(logged '/etc/cp-sandbox/registry/password.secret on i-master holds no password')" '1 1'
  rm "$(registry_token_file "$name")"
  run_command cmd_services_master --name "$name"
  check "without the registry's token, it fails before anything reaches the master" \
    "$RUN_STATUS $(logged "sandbox $name has no registry-token.secret: run sandbox.sh services") $(wc -l <"$SSM_COMMANDS") $(wc -l <"$SSH_COMMANDS")" \
    '1 1 0 0'
  rm -f "$STUB_DIR"/ssh-file.*
  state_remove "$name"
  reset_stubs
}

# fake_pipe_common: the checkout's workflows/pipe-common, with the caches Python and pytest leave.
fake_pipe_common() {
  local dir=$SANDBOX_REPO_ROOT/$PIPE_COMMON_DIR
  rm -rf "$dir"
  mkdir -p "$dir/pipeline/__pycache__" "$dir/shell" "$dir/.pytest_cache"
  printf 'from setuptools import setup\n' >"$dir/setup.py"
  printf '# the package\n' >"$dir/pipeline/__init__.py"
  printf 'cache\n' | tee "$dir/pipeline/__pycache__/__init__.cpython-312.pyc" "$dir/pipeline/old.pyc" \
    "$dir/.pytest_cache/v" >/dev/null
  printf '#!/bin/bash\n' >"$dir/shell/script.sh"
  chmod 0755 "$dir/shell/script.sh"
}

test_pipe_common_from_checkout() {
  local name=dev-pc archive hash
  reset_stubs
  fake_repo
  fake_pipe_common
  master_state "$name"
  run_command cmd_services_master --name "$name" --pipe-common-from-checkout
  check "services-master --pipe-common-from-checkout succeeds" "$RUN_STATUS" 0
  check "it copies the checkout's pipe-common to the master over SSH, for root only" \
    "$(grep -c "^sudo sh -c 'umask 077 && mkdir -p /opt/cp-sandbox && cat >/opt/cp-sandbox/pipe-common.tar.gz.part && mv -f /opt/cp-sandbox/pipe-common.tar.gz.part /opt/cp-sandbox/pipe-common.tar.gz'$" "$SSH_COMMANDS")" 1
  archive=$STUB_DIR/ssh-file.pipe-common.tar.gz
  check "the archive holds what the directory holds, at its root, without the caches, owned by root" \
    "$(tar -tzf "$archive" | paste -sd ' ') | $(tar -tvzf "$archive" | awk '{print $2}' | sort -u | paste -sd ' ') | $(tar -tvzf "$archive" shell/script.sh | cut -c1-10)" \
    'pipeline/ pipeline/__init__.py setup.py shell/ shell/script.sh | 0/0 | -rwxr-xr-x'
  hash=$(sha256sum "$archive" | cut -d' ' -f1)
  check "install-services.sh serves it in place of the build's" \
    "$(grep -c 'bash /opt/cp-sandbox/install-services.sh 10.0.0.7 /opt/cp-sandbox/tool.Dockerfile https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz --notify-url https://10.0.0.7:9999/pipeline/restapi/dockerRegistry/notify --api-cert /opt/cp-sandbox/api-tls.crt --pipe-common /opt/cp-sandbox/pipe-common.tar.gz >>' "$SSM_COMMANDS")" 1
  check "the state records the archive's hash, and says where the runs get pipe-common" \
    "$(state_get "$name" distribution_pipe_common) $(logged 'and pipe-common from this checkout.')" "checkout $hash 1"
  pack_pipe_common "$STUB_DIR/again.tar.gz"
  check "the same files make the same archive" "$(sha256sum "$STUB_DIR/again.tar.gz" | cut -d' ' -f1)" "$hash"

  run_command cmd_services_master --name "$name"
  check "without it, nothing is copied, the build's is served, and the state says so" \
    "$RUN_STATUS $(grep -c 'pipe-common.tar.gz' "$SSH_COMMANDS") $(grep -c -- '--pipe-common' "$SSM_COMMANDS") $(state_get "$name" distribution_pipe_common)" \
    '0 0 0 published'

  rm "$SANDBOX_REPO_ROOT/$PIPE_COMMON_DIR/setup.py"
  run_command cmd_services_master --name "$name" --pipe-common-from-checkout
  check "a checkout without pipe-common's setup.py is refused, before install-services.sh runs" \
    "$RUN_STATUS $(logged "$PIPE_COMMON_DIR in $SANDBOX_REPO_ROOT has no setup.py") $(grep -c 'bash /opt/cp-sandbox/install-services.sh' "$SSM_COMMANDS")" \
    '1 1 0'
  rm -rf "${SANDBOX_REPO_ROOT:?}/$PIPE_COMMON_DIR" "$STUB_DIR"/ssh-file.* "$STUB_DIR/again.tar.gz"
  state_remove "$name"
  reset_stubs
}

test_optional_services() {
  check "--with takes the services in any order, and lists them in their own" \
    "$(optional_services monitoring,billing) | $(optional_services search,search) | $(optional_services all) | $(optional_services all,search)" \
    'billing monitoring | search | billing search monitoring | billing search monitoring'
  run_command optional_services billing,mail
  check "and refuses a word that is not a service" \
    "$RUN_STATUS $(logged '--with: no service mail; the services are billing search monitoring, or all')" '1 1'
  run_command services_options --name dev-svc --with search,billing
  check "services_options sets them" "$RUN_STATUS" 0
  services_options --name dev-svc --with search,billing
  check "as words" "$SERVICES_WITH" 'billing search'
  check "which with_service finds" \
    "$(with_service search && echo yes) $(with_service monitoring search && echo yes) $(with_service monitoring || echo no)" \
    'yes yes no'
  services_options --name dev-svc
  check "and none without --with" "[$SERVICES_WITH]" '[]'
}

test_service_settings() {
  local name=dev-svc dir service
  reset_stubs
  fake_repo
  up_state "$name"
  dir=$(state_dir "$name")
  CP_SANDBOX_DISTRIBUTION_URL=https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz \
    run_command cmd_services_prepare --name "$name" --docker-subnets "" --with all
  check "services-prepare --with all succeeds" "$RUN_STATUS" 0
  # shellcheck disable=SC2016 # the placeholder is the services' settings', not this shell's
  for service in billing search monitoring; do
    check "the $service service reaches the API on the network, with the token its container exports" \
      "$(grep -E '^cloud.pipeline.(host|token)=' "$dir/$service.properties" | paste -sd '|' -)" \
      'cloud.pipeline.host=https://api:9999/pipeline/restapi/|cloud.pipeline.token=${CP_API_JWT_ADMIN}'
    check "and keeps the rest of its module's settings, with a word on where they came from" \
      "$(grep -c -e "^# The settings of $(service_module "$service")$" -e '^sync.storage.s3.disable=true$' "$dir/$service.properties") $(head -n 1 "$dir/$service.properties")" \
      "2 # Written by sandbox.sh services for sandbox $name: $(service_module "$service")/$SERVICE_SETTINGS, with the settings below it"
  done
  check "billing and search write to the sandbox's Elasticsearch" \
    "$(grep -hE '^elasticsearch.client.(url|port|scheme)=' "$dir/billing.properties" "$dir/search.properties" | sort -u | paste -sd '|' -)" \
    'elasticsearch.client.port=9200|elasticsearch.client.scheme=http|elasticsearch.client.url=elasticsearch'
  check "billing bills runs" "$(grep '^sync.run.disable=' "$dir/billing.properties")" 'sync.run.disable=false'
  check "on the schedule the platform's billing image has by default, and only billing" \
    "$(grep -h '^sync.billing.schedule=' "$dir/billing.properties" "$dir/search.properties" "$dir/monitoring.properties" | paste -sd '|' -)" \
    'sync.billing.schedule=0 30 1 ? * *|sync.billing.schedule=0 */3 * ? * *|sync.billing.schedule=0 */3 * ? * *'
  check "search reads the API's database" "$(grep '^database.url=' "$dir/search.properties")" \
    'database.url=jdbc:postgresql://db:5432/pipeline'
  check "search runs without the NFS events synchronizer, which needs a bucket of events" \
    "$(grep -c '^sync.nfs-file.observer.sync.disable=true$' "$dir/search.properties" "$dir/billing.properties" "$dir/monitoring.properties" | cut -d: -f2 | paste -sd ' ' -)" \
    '1 0 0'
  check "monitoring writes to the sandbox's Elasticsearch, and keeps the credits' time in its working directory" \
    "$(grep -E '^(es.host|es.port|platform.usage.credits.monitor.execution.timestamp.file)=' "$dir/monitoring.properties" | paste -sd '|' -)" \
    'es.host=elasticsearch|es.port=9200|platform.usage.credits.monitor.execution.timestamp.file=lastPlatformUsageCreditsMonitorTime.txt'
  check "the others' Elasticsearch settings stay as their modules have them" \
    "$(grep -c '^elasticsearch.client.url=localhost$' "$dir/monitoring.properties") $(grep -c '^es.port=30094$' "$dir/billing.properties" "$dir/search.properties" | cut -d: -f2 | paste -sd ' ' -)" \
    '1 1 1'
  check "the settings hold no secret" \
    "$(grep -c -e "$FAKE_TOKEN" -e 'eyJ' "$dir/billing.properties" "$dir/search.properties" "$dir/monitoring.properties" | cut -d: -f2 | paste -sd ' ' -)" \
    '0 0 0'

  rm -f "$dir"/*.properties
  CP_SANDBOX_DISTRIBUTION_URL=https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz \
    run_command cmd_services_prepare --name "$name" --docker-subnets ""
  check "services-prepare writes no service's settings without --with" \
    "$RUN_STATUS $(find "$dir" -name '*.properties' ! -name api.properties | wc -l)" '0 0'

  rm -f "$SANDBOX_REPO_ROOT/elasticsearch-agent/$SERVICE_SETTINGS"
  CP_SANDBOX_DISTRIBUTION_URL=https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz \
    run_command cmd_services_prepare --name "$name" --docker-subnets "" --with search
  check "it fails when a module has no settings" \
    "$RUN_STATUS $(logged "elasticsearch-agent/$SERVICE_SETTINGS is not in $SANDBOX_REPO_ROOT")" '1 1'

  rm -f "$SANDBOX_REPO_ROOT/$BILLING_DEPLOY_SETTINGS" "$dir"/*.properties
  CP_SANDBOX_DISTRIBUTION_URL=https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz \
    run_command cmd_services_prepare --name "$name" --docker-subnets "" --with billing
  check "it fails when the billing image has no settings, says so, and writes no billing settings" \
    "$RUN_STATUS $(logged "$BILLING_DEPLOY_SETTINGS is not in $SANDBOX_REPO_ROOT") $(find "$dir" -name billing.properties | wc -l)" \
    '1 1 0'
  # shellcheck disable=SC2016 # the placeholder is the image's settings', not this shell's
  printf 'sync.billing.schedule=${CP_BILLING_SYNC_SCHEDULE}\n' >"$SANDBOX_REPO_ROOT/$BILLING_DEPLOY_SETTINGS"
  CP_SANDBOX_DISTRIBUTION_URL=https://example.com/builds/cloud-pipeline.0.17.0.1.abc.tgz \
    run_command cmd_services_prepare --name "$name" --docker-subnets "" --with billing
  check "it fails when the image's schedule is a variable with no default, and writes no billing settings" \
    "$RUN_STATUS $(logged "$BILLING_DEPLOY_SETTINGS in $SANDBOX_REPO_ROOT gives sync.billing.schedule no value") $(find "$dir" -name billing.properties | wc -l)" \
    '1 1 0'
  state_remove "$name"
  reset_stubs
}

# The billing image's schedule, read from the checkout's own settings, and from made-up ones.
test_billing_schedule() {
  local file=$STUB_DIR/repo/$BILLING_DEPLOY_SETTINGS
  SANDBOX_REPO_ROOT=$REAL_REPO_ROOT
  run_command billing_schedule
  SANDBOX_REPO_ROOT=$STUB_DIR/repo
  check "the billing image's schedule is read from $BILLING_DEPLOY_SETTINGS: once a day, at midnight" \
    "$RUN_STATUS $(cat "$RUN_LOG")" '0 0 0 0 ? * *'
  mkdir -p "$(dirname "$file")"
  # shellcheck disable=SC2016 # the placeholders are the image's settings', not this shell's
  printf 'sync.billing.schedule=${A:0 0 1 ? * *}\nsync.billing.schedule=${B_2:0 0 2 ? * *}\n' >"$file"
  run_command billing_schedule
  check "of the key's lines, the last counts, as in Java's properties, and its default is taken" \
    "$RUN_STATUS $(cat "$RUN_LOG")" '0 0 0 2 ? * *'
  printf 'sync.billing.schedule=0 0 3 ? * *\n' >"$file"
  run_command billing_schedule
  check "a plain value is taken as it is" "$RUN_STATUS $(cat "$RUN_LOG")" '0 0 0 3 ? * *'
  printf 'sync.billing.center.key=billing-group\n' >"$file"
  run_command billing_schedule
  check "without the key, it fails and says so" \
    "$RUN_STATUS $(logged "$BILLING_DEPLOY_SETTINGS in $SANDBOX_REPO_ROOT gives sync.billing.schedule no value")" '1 1'
  rm -f "$file"
}

# The installer's search preferences, read from the checkout's own configure-utils.sh, and from
# made-up ones.
test_search_preferences() {
  local preferences installer=$STUB_DIR/repo/$INSTALLER_UTILS
  SANDBOX_REPO_ROOT=$REAL_REPO_ROOT
  run_command search_preferences
  preferences=$(grep '^\[' "$RUN_LOG" || true)
  SANDBOX_REPO_ROOT=$STUB_DIR/repo
  check "the installer's search preferences are read from $INSTALLER_UTILS" "$RUN_STATUS" 0
  check "with the sandbox's Elasticsearch in place of the platform's, and the installer's visibility" \
    "$(jq -c '[.[] | select(.name | test("^search.elastic.(scheme|host|port)$")) | [.name, .value, .visible]]' <<<"$preferences")" \
    '[["search.elastic.scheme","http",true],["search.elastic.host","elasticsearch",true],["search.elastic.port","9200",false]]'
  check "the index of each kind of document, as the installer's here-document has it" \
    "$(jq -r '.[] | select(.name == "search.elastic.index.type.prefix") | .value | fromjson | [.PIPELINE_RUN, .TOOL] | join(" ")' <<<"$preferences")" \
    'cp-pipeline-run cp-tool'
  check "each preference once, none with a value from the installer's config, and only search ones" \
    "$(jq -r '[length == (map(.name) | unique | length), all(.[]; (.value | contains("$") | not) and (.name | startswith("search.elastic.")))] | join(" ")' <<<"$preferences")" \
    'true true'
  check "every preference the installer sets is there" \
    "$(jq -r 'map(.name) | contains(["search.elastic.allowed.users.field", "search.elastic.index.common.prefix", "search.elastic.type.field"])' <<<"$preferences")" \
    true

  mkdir -p "$(dirname "$installer")"
  cat >"$installer" <<'INSTALLER'
function api_register_other {
    api_set_preference "search.elastic.host" "not-this-one" "false"
}

function api_register_search {
    api_set_preference "search.elastic.scheme" "http" "false"
    api_set_preference "search.elastic.scheme" "${CP_SEARCH_ELK_INTERNAL_SCHEME:-http}" "true"
    api_set_preference "search.elastic.hide.deleted" "${CP_SEARCH_HIDE_DELETED:-true}" "false"
    api_set_preference "search.elastic.type.field" "doc_type" "false"
local search_elastic_index_type_prefix="{}"
read -r -d '' search_elastic_index_type_prefix <<-EOF
	{
	    "TOOL": "cp-tool"
	}
EOF
    search_elastic_index_type_prefix="$(escape_string "$search_elastic_index_type_prefix")"
    api_set_preference "search.elastic.index.type.prefix" "$search_elastic_index_type_prefix" "false"
    api_set_preference "git.repository.indexing.enabled" "true" "false"
}

function api_register_after {
    api_set_preference "search.elastic.port" "1" "false"
}
INSTALLER
  run_command search_preferences
  check "only the installer's search function is read, and a value from its config, but the address, is left and named" \
    "$RUN_STATUS $(grep '^\[' "$RUN_LOG" | jq -c 'map([.name, .value, .visible])') $(logged 'Left as the API has them, since the installer sets them from its config: search.elastic.hide.deleted.')" \
    '0 [["search.elastic.scheme","http",true],["search.elastic.type.field","doc_type",false],["search.elastic.index.type.prefix","{\"TOOL\":\"cp-tool\"}",false]] 1'
  printf 'function api_register_search {\n}\n' >"$installer"
  run_command search_preferences
  check "an installer whose search function sets nothing is refused" \
    "$RUN_STATUS $(logged 'api_register_search in deploy/contents/install/app/configure-utils.sh sets no search preference')" '1 1'
  rm -f "$installer"
  run_command search_preferences
  check "and so is a checkout without the installer" "$RUN_STATUS $(logged "$INSTALLER_UTILS is not in")" '1 1'
}

test_services_sequences() {
  reset_stubs
  state_set dev-seq run_id_base 123400000
  run_command cmd_services_sequences --name dev-seq
  check "services-sequences moves the run and pool node IDs to the block, and leaves them once there" \
    "$RUN_STATUS $(paste -sd '|' "$RUN_LOG")" \
    "0 SELECT setval('pipeline.s_pipeline_run', 123400000, false) WHERE (SELECT last_value FROM pipeline.s_pipeline_run) < 123400000;|SELECT setval('pipeline.s_free_nodes', 123400000, false) WHERE (SELECT last_value FROM pipeline.s_free_nodes) < 123400000;"
  state_unset dev-seq run_id_base
  run_command cmd_services_sequences --name dev-seq
  check "services-sequences fails without a block" "$RUN_STATUS $(logged 'has no run ID block')" '1 1'
  state_remove dev-seq
}

# The real api_call, with curl replaced by one that records its arguments and answers as the API
# would.
test_api_call() {
  local headers
  reset_stubs
  SERVICES_NAME=dev-call
  state_set dev-call name dev-call
  printf 'header.payload.signature\n' >"$(admin_token_file dev-call)"
  : >"$(api_tls_cert_file dev-call)"
  (
    eval "$(declare -f real_api_call | sed 's/^real_api_call/api_call/')"
    # shellcheck disable=SC2329 # called by api_call
    curl() {
      printf '%s\n' "$*" >>"$STUB_DIR/curl-args"
      [[ " $* " != *' --data-binary @- '* ]] || cat >"$STUB_DIR/curl-input"
      next_answer curl "$(printf '{"status": "OK", "payload": {"id": 7}}\n200')" | tr '|' '\n'
    }
    : >"$STUB_DIR/curl-args"
    check "api_call writes the payload of an answer that is OK" "$(api_call '' GET whoami)" '{"id":7}'
    headers=$(sed -E 's/.*--header @([^ ]+) .*/\1/' "$STUB_DIR/curl-args")
    check "the token goes in a header file for its owner only, never on curl's command line" \
      "$(cat "$headers") $(stat -c %a "$headers") $(grep -c 'header.payload.signature' "$STUB_DIR/curl-args" || true)" \
      'Authorization: Bearer header.payload.signature 600 0'
    check "the call trusts the API's own certificate, at api on the network" \
      "$(grep -c -- "--cacert $(api_tls_cert_file dev-call) " "$STUB_DIR/curl-args") $(grep -c 'https://api:9999/pipeline/restapi/whoami$' "$STUB_DIR/curl-args")" \
      '1 1'
    : >"$STUB_DIR/curl-args"
    api_call '' POST dockerRegistry/register '{"password": "pw-of-the-registry"}' >/dev/null
    check "a body goes to curl on its input, never on its command line" \
      "$(grep -c -- '--data-binary @- ' "$STUB_DIR/curl-args") $(grep -c pw-of-the-registry "$STUB_DIR/curl-args" || true) $(cat "$STUB_DIR/curl-input")" \
      '1 0 {"password": "pw-of-the-registry"}'
    queue curl '{"status": "ERROR", "message": "Preference base.api.host is invalid"}|200'
    run_command api_call '' POST preferences '[]'
    check "an answer that is not OK fails, with the API's message" \
      "$RUN_STATUS $(logged 'POST https://api:9999/pipeline/restapi/preferences: HTTP 200, Preference base.api.host is invalid')" '1 1'
    queue curl '<html>Unauthorized</html>|401'
    run_command api_call '' GET whoami
    check "an HTTP error fails, with the code and the start of the answer" \
      "$RUN_STATUS $(logged 'HTTP 401, <html>Unauthorized</html>')" '1 1'
  )
  state_remove dev-call
}

test_services_wait() {
  reset_stubs
  API_WAIT_SECONDS=60
  queue api.GET.whoami '!Connection refused' '!Connection refused' '{"userName": "PIPE_ADMIN"}'
  run_command cmd_services_wait --name dev-wait
  check "services-wait asks until the API answers, and says as whom" \
    "$RUN_STATUS $(api_called 'GET whoami' | wc -l) $(logged 'The API answers, as PIPE_ADMIN.')" '0 3 1'
  API_WAIT_SECONDS=0
  API_ANSWERS["GET whoami"]='!Connection refused'
  run_command cmd_services_wait --name dev-wait
  check "services-wait fails when the API never answers" \
    "$RUN_STATUS $(logged 'the API did not answer at https://api:9999/pipeline/restapi/')" '1 1'
  API_ANSWERS=()
}

# configure_without_networks_config <args...>: services-configure, with the networks config failing
# to be made.
configure_without_networks_config() {
  # shellcheck disable=SC2329 # called by preferences
  networks_config() {
    printf 'stubbed failure\n' >&2
    return 1
  }
  cmd_services_configure "$@"
}

test_services_configure() {
  local name=dev-conf preferences networks
  reset_stubs
  up_state "$name"
  state_set "$name" subnet_zone eu-west-1a key_pair cp-sandbox-dev-conf registry_path 10.0.0.7:5000 \
    api_url https://10.0.0.7:9999/pipeline/restapi/ distribution_url http://10.0.0.7:8090/ \
    distribution_source https://example.com/cloud-pipeline.1.tgz distribution_pipe_common published \
    run_id_base 123400000
  printf '%s\n' '-----BEGIN CERTIFICATE-----' 'ZmFrZQ==' '-----END CERTIFICATE-----' >"$(registry_cert_file "$name")"
  printf '%s\n' "$FAKE_REGISTRY_PASSWORD" >"$(registry_password_file "$name")"
  SANDBOX_REPO_ROOT=/repo
  CP_SANDBOX_WORKER_INSTANCE_PROFILE=arn:aws:iam::123456789012:instance-profile/worker
  CP_SANDBOX_SECURITY_GROUP_IDS='sg-0a, sg-0b'
  : >"$API_CALLS"
  API_ANSWERS=(["GET cloud/region"]='[]' ["POST cloud/region"]='{"id": 4}'
    ["GET dockerRegistry/loadTree"]='{"registries": []}' ["POST dockerRegistry/register"]='{"id": 5}'
    ["POST toolGroup"]='{"id": 6}' ["POST tool/register"]='{"id": 7}'
    ["GET preferences/cluster.max.size"]='{"name": "cluster.max.size", "value": "2"}')
  run_command cmd_services_configure --name "$name"
  check "services-configure succeeds on a fresh API" "$RUN_STATUS" 0
  check "it creates the region as the default one, with the sandbox's key pair for the nodes" \
    "$(api_called 'POST cloud/region' | cut -d' ' -f3- | jq -c '[.provider, .regionId, .name, .default, .sshKeyName, .mountStorageRule]')" \
    '["AWS","eu-west-1","eu-west-1",true,"cp-sandbox-dev-conf","NONE"]'
  preferences=$(api_called 'POST preferences' | cut -d' ' -f3-)
  check "the allowed instance types go first, as the instance type is checked against them" \
    "$(jq -c '[.[].name]' <<<"$preferences" | paste -sd ' ')" \
    '["cluster.allowed.instance.types","cluster.allowed.instance.types.docker"] ["cluster.networks.config","cluster.instance.type","cluster.max.size","cluster.spot","cluster.keep.alive.policy","cluster.keep.alive.minutes","base.api.host","base.pipe.distributions.url","commit.username","commit.deploy.key","commit.additional.envs.to.clean"]'
  check "the instance type, the cluster's size, on demand, idle nodes kept 5 minutes, and the addresses of the API and the distribution, all visible" \
    "$(sed -n 2p <<<"$preferences" | jq -c '[.[1:8][] | .value] + [all(.[:8][]; .visible)]')" \
    '["t3.medium","2","false","IDLE_MINUTES","5","https://10.0.0.7:9999/pipeline/restapi/","http://10.0.0.7:8090/",true]'
  check "the API logs in to a run's node as the nodes' user, with the key where the API's container keeps it, neither visible, as the installer sets them" \
    "$(sed -n 2p <<<"$preferences" | jq -c '[.[8:10][] | [.value, .visible]]')" \
    '[["ec2-user",false],["/root/.ssh/cp-sandbox-node-key.pem",false]]'
  check "a commit blanks the AWS credentials the API hands the run's container, by the region's ID, besides the API's default" \
    "$(sed -n 2p <<<"$preferences" | jq -c '.[10] | [.value, .visible]')" \
    '["CP_EXEC_TIMEOUT,CP_ACCOUNT_ID_4,CP_ACCOUNT_KEY_4,CP_ACCOUNT_TOKEN_4",true]'
  networks=$(sed -n 2p <<<"$preferences" | jq -r '.[0].value')
  check "the networks config starts nodes in the subnet and security groups, from the host image, with the AL2023 init script and the worker profile" \
    "$(jq -c '.regions[0] | [.name, .default, .networks, .security_group_ids, .amis[0].platform, .amis[0].instance_mask, .amis[0].ami, .amis[0].init_script, .amis[0].additional_spec]' <<<"$networks")" \
    '["eu-west-1",true,{"eu-west-1a":"subnet-0fake"},["sg-0a","sg-0b"],"linux","*","ami-host","/repo/scripts/autoscaling/init_multicloud_v1.15.4_al2023.sh",{"IamInstanceProfile":{"Arn":"arn:aws:iam::123456789012:instance-profile/worker"}}]'
  check "and tags them as the sandbox's workers, without a Name, which the node scripts set to the run ID" \
    "$(jq -c '.tags, .regions[0].tags' <<<"$networks" | sort -u)" \
    "{\"cp-sandbox\":\"dev-conf\",\"cp-sandbox-owner\":\"$ME\",\"cp-sandbox-role\":\"worker\",\"cp-sandbox-expires\":\"2030-01-01T00:00:00Z\"}"
  check "it registers the master's registry, with its certificate and its user's login, without the platform's auth" \
    "$(api_called 'POST dockerRegistry/register' | cut -d' ' -f3- | jq -c '[.path, .caCert, .userName, .password, .pipelineAuth, .securityScanEnabled]')" \
    "[\"10.0.0.7:5000\",\"-----BEGIN CERTIFICATE-----\\nZmFrZQ==\\n-----END CERTIFICATE-----\",\"pipe_admin\",\"$FAKE_REGISTRY_PASSWORD\",false,false]"
  check "and says so, with no password in what it prints" \
    "$(logged '                its login: pipe_admin, ') $(logged '                it notifies https://10.0.0.7:9999/pipeline/restapi/dockerRegistry/notify') $(logged "$FAKE_REGISTRY_PASSWORD")" '1 1 0'
  check "then the tool group in it, and the tool in the group" \
    "$(api_called 'POST toolGroup' | cut -d' ' -f3- | jq -c '[.name, .registryId]') $(api_called 'POST tool/register' | cut -d' ' -f3- | jq -c '[.image, .toolGroupId, .cpu, .ram]')" \
    '["library",5] ["library/centos",6,"1","1Gi"]'
  check "it reads every preference back, and shows them, and the registries" \
    "$(api_called 'GET preferences/' | wc -l) $(logged '  cluster.max.size = 2') $(logged 'Registries, tool groups and tools')" '13 1 1'
  check "it says the runs get the build's pipe-common" "$(logged "  pipe-common   the build's")" 1
  state_set "$name" distribution_pipe_common 'checkout 0123abcd'
  run_command cmd_services_configure --name "$name"
  check "or the checkout's, by its hash" "$(logged "  pipe-common   the checkout's, 0123abcd, in place of the build's")" 1
  state_set "$name" distribution_pipe_common published
  : >"$STUB_DIR/jq-args"
  # shellcheck disable=SC2329 # called by services-configure
  jq() {
    printf '%s\n' "$*" >>"$STUB_DIR/jq-args"
    command jq "$@"
  }
  run_command cmd_services_configure --name "$name"
  unset -f jq
  check "no jq call is given the password on its command line" \
    "$RUN_STATUS $(grep -c "$FAKE_REGISTRY_PASSWORD" "$STUB_DIR/jq-args" || true) $([[ -s $STUB_DIR/jq-args ]] && echo called)" \
    '0 0 called'

  : >"$API_CALLS"
  API_ANSWERS=(["GET cloud/region"]='[{"id": 4, "regionId": "eu-west-1"}]'
    ["GET dockerRegistry/loadTree"]='{"registries": [{"id": 5, "path": "10.0.0.7:5000", "groups": [{"id": 6, "name": "library", "tools": [{"id": 7, "image": "library/centos"}]}]}]}')
  run_command cmd_services_configure --name "$name"
  check "a second services-configure sets the preferences and the registry's login again, and creates nothing" \
    "$RUN_STATUS $(api_called POST | cut -d' ' -f1-2 | sort | uniq -c | awk '{print $1, $3}' | paste -sd ',')" \
    '0 1 dockerRegistry/updateCredentials,2 preferences'
  check "the login goes to the registry registered, with its certificate, as the API's update takes them all" \
    "$(api_called 'POST dockerRegistry/updateCredentials' | cut -d' ' -f3- | jq -c '[.id, .path, .userName, .password, (.caCert | startswith("-----BEGIN")), .pipelineAuth]')" \
    "[5,\"10.0.0.7:5000\",\"pipe_admin\",\"$FAKE_REGISTRY_PASSWORD\",true,false]"
  check "and lists the registry with its tool" \
    "$(logged '  10.0.0.7:5000 (ID 5)') $(logged '    library: library/centos')" '1 1'

  API_ANSWERS=(["GET cloud/region"]='[{"id": 4, "regionId": "eu-west-1"}]' ["POST preferences"]='!Preference base.api.host is invalid')
  run_command cmd_services_configure --name "$name"
  check "services-configure fails when the API refuses a preference, with the API's message" \
    "$RUN_STATUS $(logged 'POST preferences: Preference base.api.host is invalid')" '1 1'

  API_ANSWERS=(["GET cloud/region"]='[{"id": 4, "regionId": "eu-west-1"}]')
  : >"$API_CALLS"
  run_command configure_without_networks_config --name "$name"
  check "services-configure fails, and sets no preference, when it cannot make them" \
    "$RUN_STATUS $(api_called 'POST preferences' | wc -l)" '1 0'
  API_ANSWERS=()
  state_remove "$name"
  reset_stubs
}

test_services_configure_with() {
  local name=dev-with lists
  reset_stubs
  up_state "$name"
  state_set "$name" subnet_zone eu-west-1a key_pair cp-sandbox-dev-with registry_path 10.0.0.7:5000 \
    api_url https://10.0.0.7:9999/pipeline/restapi/ distribution_url http://10.0.0.7:8090/ \
    distribution_source https://example.com/cloud-pipeline.1.tgz run_id_base 123400000
  printf '%s\n' '-----BEGIN CERTIFICATE-----' 'ZmFrZQ==' '-----END CERTIFICATE-----' >"$(registry_cert_file "$name")"
  printf '%s\n' "$FAKE_REGISTRY_PASSWORD" >"$(registry_password_file "$name")"
  SANDBOX_REPO_ROOT=$REAL_REPO_ROOT
  API_ANSWERS=(["GET cloud/region"]='[{"id": 4, "regionId": "eu-west-1"}]'
    ["GET dockerRegistry/loadTree"]='{"registries": [{"id": 5, "path": "10.0.0.7:5000", "groups": [{"id": 6, "name": "library", "tools": [{"id": 7, "image": "library/centos"}]}]}]}'
    ["GET preferences/search.elastic.host"]='{"name": "search.elastic.host", "value": "elasticsearch"}')
  : >"$API_CALLS"
  run_command cmd_services_configure --name "$name" --with billing
  check "with the billing service, services-configure succeeds" "$RUN_STATUS" 0
  lists=$(api_called 'POST preferences' | cut -d' ' -f3-)
  check "and sets the installer's search preferences too, last, pointed at the sandbox's Elasticsearch" \
    "$(wc -l <<<"$lists" | tr -d ' ') $(sed -n 3p <<<"$lists" | jq -r '[.[] | select(.name == "search.elastic.host" or .name == "search.elastic.port") | .value] | join(":")')" \
    '3 elasticsearch:9200'
  check "it reads them back too" \
    "$(logged '  search.elastic.host = elasticsearch') $(api_called 'GET preferences/search.elastic.' | wc -l | tr -d ' ')" \
    "1 $(sed -n 3p <<<"$lists" | jq length)"
  check "and names the Elasticsearch and the service's container, settings and log" \
    "$(logged "  elasticsearch http://elasticsearch:9200 on the network, in the container cp-sandbox-$name-elasticsearch") $(logged "  billing       the container cp-sandbox-$name-billing, with $(service_properties_file "$name" billing); log: sandbox.sh services --log billing")" \
    '1 1'

  : >"$API_CALLS"
  run_command cmd_services_configure --name "$name" --with monitoring
  check "the monitoring service alone needs no search preference" \
    "$RUN_STATUS $(api_called 'POST preferences' | wc -l | tr -d ' ') $(logged '  monitoring    the container')" '0 2 1'
  SANDBOX_REPO_ROOT=$STUB_DIR/repo
  API_ANSWERS=()
  state_remove "$name"
  reset_stubs
}

test_services_api() {
  local run
  reset_stubs
  : >"$API_CALLS"
  run='{"id": 7, "status": "RUNNING", "sshPassword": "pw-of-the-run", "envVars": {"API_TOKEN": "token-of-the-run", "CP_ACCOUNT_KEY_1": "key-of-the-account", "RUN_ID": "7"}, "env": [{"name": "SSH_PASS", "value": "pass-of-the-run"}, {"name": "RUN_ID", "value": "7"}], "instance": {"nodeId": "i-0node", "sshKeyName": "cp-sandbox-dev-api"}}'
  API_ANSWERS=(["GET run/7"]=$run ["POST run"]='{"id": 8, "status": "RUNNING"}')
  run_command cmd_services_api --name dev-api GET /run/7
  check "services-api calls the path under the REST root, as the admin, and writes the payload" \
    "$RUN_STATUS $(api_called 'GET run/7' | wc -l) $(jq -c '[.id, .status, .envVars.RUN_ID, .env[1].value, .instance.nodeId, .instance.sshKeyName]' "$RUN_LOG")" \
    '0 1 [7,"RUNNING","7","7","i-0node","cp-sandbox-dev-api"]'
  check "with the values of the keys that name a secret blanked out, nested ones too, and of the name-value pairs that do" \
    "$(jq -c '[.sshPassword, .envVars.API_TOKEN, .envVars.CP_ACCOUNT_KEY_1, .env[0].value]' "$RUN_LOG") $(grep -c -e -of-the- "$RUN_LOG" || true)" \
    '["<blanked>","<blanked>","<blanked>","<blanked>"] 0'
  run_command cmd_services_api --name dev-api --jq '.envVars.API_TOKEN, .status' GET run/7
  check "--jq filters what it writes, secrets blanked first, strings unquoted" \
    "$RUN_STATUS $(paste -sd ' ' "$RUN_LOG")" '0 <blanked> RUNNING'
  export CP_SANDBOX_TEST_SECRET=secret-in-the-environment
  # shellcheck disable=SC2016 # $ENV is jq's
  run_command cmd_services_api --name dev-api --jq '[$ENV | keys[]] | join(",")' GET run/7
  unset CP_SANDBOX_TEST_SECRET
  check "the filter runs with an empty environment, which holds no credential" "$RUN_STATUS $(cat "$RUN_LOG")" '0 PATH'
  run_command cmd_services_api --name dev-api --jq .id POST run '{"dockerImage": "10.0.0.7:5000/library/centos:7"}'
  check "it sends the body given" \
    "$RUN_STATUS $(api_called 'POST run ' | cut -d' ' -f3- | jq -c .) $(cat "$RUN_LOG")" \
    '0 {"dockerImage":"10.0.0.7:5000/library/centos:7"} 8'
  : >"$API_CALLS"
  run_command cmd_services_api --name dev-api POST run '{"dockerImage":'
  check "a body that is not JSON is refused, before any call" \
    "$RUN_STATUS $(logged 'the body is not JSON') $(wc -l <"$API_CALLS")" '1 1 0'
  run_command cmd_services_api --name dev-api PATCH run/7
  check "so is a method the REST API does not take" "$RUN_STATUS $(logged 'not PATCH')" '1 1'
  run_command cmd_services_api --name dev-api GET
  check "and a call without a path" "$RUN_STATUS $(logged 'usage: sandbox.sh api')" '1 1'
  API_ANSWERS=(["GET run/9"]='!Run 9 not found')
  run_command cmd_services_api --name dev-api GET run/9
  check "an answer that is not OK fails, with the API's message" "$RUN_STATUS $(logged 'Run 9 not found')" '1 1'
  API_ANSWERS=()
  reset_stubs
}

# The names a commit blanks are the API's own, read from the checkout: the variables it hands a run's
# container the AWS credentials in, and the default of the preference.
test_commit_envs_are_the_apis() {
  local java=$REAL_REPO_ROOT/api/src/main/java/com/epam/pipeline/manager
  check "the credential variables a commit blanks are those SystemParams names" \
    "$(sed -n -E 's/.* CLOUD_ACCOUNT(_KEY|_TOKEN)?_PREFIX = "([^"]+)";.*/\2/p' "$java/execution/SystemParams.java" | paste -sd ' ')" \
    "${COMMIT_CREDENTIAL_ENV_PREFIXES[*]}"
  check "and the rest is the API's default for commit.additional.envs.to.clean" \
    "$(sed -n -E 's/.*"commit\.additional\.envs\.to\.clean", "([^"]*)".*/\1/p' "$java/preference/SystemPreferences.java")" \
    "$COMMIT_ENVS_TO_CLEAN_DEFAULT"
}

test_down_deletes_the_key_pair() {
  reset_stubs
  DESCRIBED=$(described "$(instance i-master dev-key master running 2030-01-01T00:00:00Z)")
  state_set dev-key instance_id i-master
  STUB_OUTPUT["ec2 describe-key-pairs"]=$(jq -cn --arg me "$ME" '[
    {id: "key-0mine", name: "cp-sandbox-dev-key", tags: [{Key: "cp-sandbox", Value: "dev-key"}, {Key: "cp-sandbox-owner", Value: $me}]},
    {id: "key-0other", name: "other", tags: [{Key: "cp-sandbox", Value: "dev-key"}, {Key: "cp-sandbox-owner", Value: "someone@example.com"}]}]')
  run_command cmd_down --name dev-key
  check "down lists the sandbox's key pair to delete, and leaves another owner's" \
    "$(logged '  delete    key pair cp-sandbox-dev-key (key-0mine)') $(logged '  leave     key-0other, tagged cp-sandbox-owner=someone@example.com') $(called 'ec2 delete-key-pair')" \
    '1 1 '
  check "down looks the key pairs up by the sandbox's tag" \
    "$(called 'ec2 describe-key-pairs' | grep -c -- '--filters Name=tag:cp-sandbox,Values=dev-key' || true)" 1
  run_command cmd_down --name dev-key --yes
  check "down --yes deletes the sandbox's key pair, once the instances are gone, and not the other" \
    "$RUN_STATUS $(grep -n -e 'ec2 wait instance-terminated' -e 'ec2 delete-key-pair' "$CALLS" | cut -d: -f2- | cut -d' ' -f1-4 | paste -sd '|')" \
    '0 ec2 wait instance-terminated --instance-ids|ec2 delete-key-pair --key-pair-id key-0mine'

  DESCRIBED='{"Reservations": []}'
  run_command cmd_down --name dev-key
  check "down with only a key pair left lists it, and does not say there is nothing" \
    "$(logged '  delete    key pair cp-sandbox-dev-key') $(logged 'nothing:')" '1 0'
  reset_stubs
}

test_render_properties
test_cidrs_overlap
test_newest_distribution
test_key_pair
test_services_prepare
test_services_master
test_pipe_common_from_checkout
test_optional_services
test_service_settings
test_billing_schedule
test_search_preferences
test_services_sequences
test_api_call
test_services_wait
test_services_configure
test_services_configure_with
test_services_api
test_commit_envs_are_the_apis
test_down_deletes_the_key_pair
finish
