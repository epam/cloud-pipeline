#!/usr/bin/env bash
# shellcheck disable=SC2016 # the stubs' bodies are not for this shell to expand
#
# Checks of docker/api-entrypoint.sh, which the API's container starts with: run as root, twice,
# with ip, tun2socks, keytool and curl replaced by stubs that record their calls, and with what it
# hands over to replaced by one that records the environment it gets. openssl is real, and so are
# the keys and the certificate it reads. Then of docker/service-entrypoint.sh, which the optional
# services' containers start with, and of docker/ui-entrypoint.sh, which the GUI's dev server starts
# with, the same way. Runs in the tools image, in a container of its own, which sandbox-test.sh
# starts as root and throws away.
set -euo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
ENTRYPOINT=$(dirname "$(dirname "$TEST_DIR")")/docker/api-entrypoint.sh

WORK=$(mktemp -d)
STUBS=$WORK/stubs
SANDBOX=$WORK/state/dev-api
export STUB_CALLS=$WORK/calls STUB_KEYSTORES=$WORK/keystores HANDED=$WORK/handed
OUTPUT=$WORK/output
# Where the copy of the sandbox's SSH key goes: a directory that is not there yet.
NODE_KEY=$WORK/ssh/node-key.pem
NODE_KEY_BODY=ZmFrZSBub2RlIGtleQ
TOKEN=abcdef.0123456789abcdef
FAILURES=0
STATUS=

[[ $(id -u) == 0 ]] || { echo "api-entrypoint-test.sh runs as root, in a container of its own" >&2; exit 1; }

check() {   # check <description> <actual> <expected>
  if [[ $2 == "$3" ]]; then
    printf 'ok      %s\n' "$1"
  else
    printf 'FAILED  %s\n        expected: %s\n        actual:   %s\n' "$1" "$3" "$2"
    sed 's/^/        | /' "$OUTPUT"
    FAILURES=$((FAILURES + 1))
  fi
}

stub() {   # stub <name> [<body>]: a command that records its call, then runs the body
  printf '#!/bin/bash\nprintf "%%s\\n" "$(basename "$0") $*" >>"$STUB_CALLS"\n%s\n' "${2:-exit 0}" \
    >"$STUBS/$1"
  chmod +x "$STUBS/$1"
}

called() {   # called <command>: the recorded calls of it
  grep -E "^$1( |$)" "$STUB_CALLS" || true
}

# run [args...]: runs the entrypoint as the API's container does, with the stubs first on PATH;
# sets STATUS, and keeps what it printed in OUTPUT. Its calls start a fresh record.
run() {
  : >"$STUB_CALLS"
  rm -f "$HANDED"
  if PATH=$STUBS:$PATH TOOLS_ENTRYPOINT=$STUBS/handed-to JAVA_HOME=$WORK/java ROUTE_CHECK_SECONDS=0 \
    SANDBOX_DIR=$SANDBOX SANDBOX_VPC_CIDRS="10.0.0.0/16 10.1.0.0/16" \
    SANDBOX_SOCKS_PROXY=cp-sandbox-dev-api-master:1080 SANDBOX_MASTER_IP=10.0.0.7 \
    SANDBOX_NODE_SSH_KEY=$NODE_KEY bash "$ENTRYPOINT" "$@" >"$OUTPUT" 2>&1; then
    STATUS=0
  else
    STATUS=$?
  fi
}

handed() {   # handed <key>: a line of what the command was handed over with
  sed -n "s/^$1=//p" "$HANDED"
}

set_up() {
  mkdir -p "$STUBS" "$STUB_KEYSTORES" "$SANDBOX" "$WORK/java/lib/security"
  : >"$WORK/java/lib/security/cacerts"
  # The tun device is there once made.
  stub ip 'if [[ "$1 $2" == "link show" ]]; then [[ -f $STUB_KEYSTORES/../tun-made ]]; exit; fi
[[ "$1 $2" != "tuntap add" ]] || : >"$STUB_KEYSTORES/../tun-made"'
  stub tun2socks
  stub curl '[[ -z ${STUB_CURL_FAILS:-} ]]'
  # Keeps each keystore it is asked to import, to be looked at.
  stub keytool 'prev=
for arg in "$@"; do
  if [[ $prev == -srckeystore ]]; then cp "$arg" "$STUB_KEYSTORES/"; fi
  prev=$arg
done'
  cat >"$STUBS/handed-to" <<'EOF'
#!/bin/bash
{
  printf 'cwd=%s\n' "$PWD"
  printf 'args=%s\n' "$*"
  for name in CP_API_JWT_KEY_PRIVATE CP_API_JWT_KEY_PUBLIC CP_KUBE_KUBEADM_TOKEN CP_API_SSO_CONFIG \
    CP_API_WORKDIR KUBECONFIG; do
    printf '%s=%s\n' "$name" "${!name-}"
  done
} >"$HANDED"
EOF
  chmod +x "$STUBS/handed-to"

  openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=api \
    -addext "subjectAltName=DNS:api,IP:10.0.0.7" -keyout "$SANDBOX/api-tls-key.pem" \
    -out "$SANDBOX/api-tls.crt" 2>/dev/null
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$SANDBOX/jwt-private-key.pem" 2>/dev/null
  openssl pkey -in "$SANDBOX/jwt-private-key.pem" -pubout -out "$SANDBOX/jwt-public-key.pem"
  printf '%s\n' "$TOKEN" >"$SANDBOX/kube-join-token.secret"
  printf 'kind: Config\n' >"$SANDBOX/kubeconfig.secret"
  # As a mounted file may look: readable by others.
  printf '%s\n' '-----BEGIN OPENSSH PRIVATE KEY-----' "$NODE_KEY_BODY" '-----END OPENSSH PRIVATE KEY-----' \
    >"$SANDBOX/ssh-key.pem"
  chmod 0644 "$SANDBOX/ssh-key.pem"
}

test_entrypoint() {
  local private
  run java -jar api.jar --spring.config.location=file:api.properties
  check "the entrypoint succeeds, and hands the command over, as it was given" \
    "$STATUS $(handed args)" '0 java -jar api.jar --spring.config.location=file:api.properties'
  check "it routes each of the VPC's ranges through a tun device" \
    "$(called ip | paste -sd '|')" \
    'ip link show dev tun0|ip tuntap add mode tun dev tun0|ip addr replace 198.18.0.1/15 dev tun0|ip link set dev tun0 up|ip route replace 10.0.0.0/16 dev tun0|ip route replace 10.1.0.0/16 dev tun0'
  check "which tun2socks serves, in the background, from the tunnel's SOCKS proxy" \
    "$(called tun2socks)" 'tun2socks --device tun://tun0 --proxy socks5://cp-sandbox-dev-api-master:1080 --loglevel warn'
  check "and checks the route at the master's private address" \
    "$(called curl)" 'curl --silent --insecure --output /dev/null --max-time 5 https://10.0.0.7:6443/healthz'
  check "the keystore holds the API's TLS key under ssl and under sso, with the store's password" \
    "$(for alias in ssl sso; do openssl pkcs12 -in "$STUB_KEYSTORES/$alias.p12" -passin pass:changeit -nokeys 2>/dev/null | sed -n 's/^ *friendlyName: //p'; done | paste -sd ' ') $(called keytool | grep -c -- '-importkeystore -noprompt' || true)" \
    'ssl sso 2'
  check "the identity provider's metadata names none, with the API's certificate" \
    "$(grep -c 'entityID="urn:cp-sandbox:no-identity-provider"' /var/lib/cp-sandbox-api/sso/cp-api-srv-fed-meta.xml) $(grep -c "<ds:X509Certificate>$(sed '/^-----/d' "$SANDBOX/api-tls.crt" | tr -d '\n')</ds:X509Certificate>" /var/lib/cp-sandbox-api/sso/cp-api-srv-fed-meta.xml)" \
    '1 1'
  check "Java trusts the API's certificate, under an alias of its own" \
    "$(called 'keytool -importcert')" \
    "keytool -importcert -noprompt -keystore $WORK/java/lib/security/cacerts -storepass changeit -alias cp-sandbox-api -file $SANDBOX/api-tls.crt"
  private=$(handed CP_API_JWT_KEY_PRIVATE)
  check "the JWT keys go to the API as one line of base64 DER each, PKCS#8 and X.509, as it reads them" \
    "$(base64 -d <<<"$private" | openssl pkey -inform DER -noout 2>&1 && echo private) $(handed CP_API_JWT_KEY_PUBLIC | base64 -d | openssl pkey -pubin -inform DER -noout 2>&1 && echo public)" \
    'private public'
  check "the join token goes to the API without its newline" "$(handed CP_KUBE_KUBEADM_TOKEN)" "$TOKEN"
  check "the API's settings find the keystore and the working directory, and the command runs from there" \
    "$(handed CP_API_SSO_CONFIG) $(handed CP_API_WORKDIR) $(handed cwd) $(called 'keytool -importkeystore' | grep -c -- '-destkeystore /var/lib/cp-sandbox-api/sso/store.jks -deststoretype JKS' || true)" \
    '/var/lib/cp-sandbox-api/sso /var/lib/cp-sandbox-api/work /var/lib/cp-sandbox-api/work 2'
  check "the kubeconfig is where the node scripts read it, for root only, and KUBECONFIG names it" \
    "$(handed KUBECONFIG) $(cmp -s "$HOME/.kube/config" "$SANDBOX/kubeconfig.secret" && echo copied) $(stat -c %a "$HOME/.kube/config")" \
    "$HOME/.kube/config copied 600"
  check "the sandbox's SSH key is where SANDBOX_NODE_SSH_KEY says, for root only, for the API to log in to the nodes with" \
    "$(cmp -s "$NODE_KEY" "$SANDBOX/ssh-key.pem" && echo copied) $(stat -c '%a %u' "$NODE_KEY")" 'copied 600 0'
  check "it prints no secret" \
    "$(grep -c -e "$TOKEN" -e "$(sed -n 2p "$SANDBOX/jwt-private-key.pem")" -e "$NODE_KEY_BODY" "$OUTPUT" || true)" 0

  run java -jar api.jar
  check "it runs again in the same container, as after docker start: the tun device is kept, the certificate trusted again" \
    "$STATUS $(called 'ip tuntap' | wc -l) $(called 'keytool -delete' | grep -c 'cp-sandbox-api' || true)" '0 0 1'

  STUB_CURL_FAILS=1 run java -jar api.jar
  check "when the master does not answer through the route, it says so, and starts the API all the same" \
    "$STATUS $(grep -c 'the master does not answer at 10.0.0.7:6443 through the SOCKS proxy' "$OUTPUT") $(handed args)" \
    '0 1 java -jar api.jar'

  : >"$STUB_CALLS"
  if PATH=$STUBS:$PATH TOOLS_ENTRYPOINT=$STUBS/handed-to SANDBOX_DIR=$SANDBOX bash "$ENTRYPOINT" true \
    >"$OUTPUT" 2>&1; then STATUS=0; else STATUS=$?; fi
  check "it refuses to start without the sandbox's settings, before it changes anything" \
    "$STATUS $(grep -c 'SANDBOX_VPC_CIDRS' "$OUTPUT") $(wc -l <"$STUB_CALLS")" '1 1 0'

  : >"$STUB_CALLS"
  if PATH=$STUBS:$PATH TOOLS_ENTRYPOINT=$STUBS/handed-to SANDBOX_DIR=$SANDBOX SANDBOX_VPC_CIDRS=10.0.0.0/16 \
    SANDBOX_SOCKS_PROXY=cp-sandbox-dev-api-master:1080 SANDBOX_MASTER_IP=10.0.0.7 bash "$ENTRYPOINT" true \
    >"$OUTPUT" 2>&1; then STATUS=0; else STATUS=$?; fi
  check "and without where to copy the SSH key to" \
    "$STATUS $(grep -c 'SANDBOX_NODE_SSH_KEY' "$OUTPUT") $(wc -l <"$STUB_CALLS")" '1 1 0'
}

# docker/service-entrypoint.sh, which the optional services' containers start with, in the same
# image: it hands the command over with the admin's token in the environment, and nothing else.
test_service_entrypoint() {
  local entrypoint admin=header.payload.signature
  entrypoint=$(dirname "$ENTRYPOINT")/service-entrypoint.sh
  printf '%s\n' "$admin" >"$SANDBOX/admin-token.secret"
  cat >"$STUBS/service-handed-to" <<'EOF'
#!/bin/bash
printf 'args=%s\nCP_API_JWT_ADMIN=%s\n' "$*" "${CP_API_JWT_ADMIN-}" >"$HANDED"
EOF
  chmod +x "$STUBS/service-handed-to"
  rm -f "$HANDED"
  if SANDBOX_DIR=$SANDBOX bash "$entrypoint" "$STUBS/service-handed-to" -jar search.jar >"$OUTPUT" 2>&1; then
    STATUS=0
  else
    STATUS=$?
  fi
  check "the service's entrypoint hands the command over, with the admin's token, without its newline" \
    "$STATUS $(handed args) $(handed CP_API_JWT_ADMIN)" "0 -jar search.jar $admin"
  check "and prints nothing" "$(wc -c <"$OUTPUT" | tr -d ' ')" 0

  rm -f "$HANDED" "$SANDBOX/admin-token.secret"
  if SANDBOX_DIR=$SANDBOX bash "$entrypoint" "$STUBS/service-handed-to" >"$OUTPUT" 2>&1; then
    STATUS=0
  else
    STATUS=$?
  fi
  check "without the token, it says so and runs nothing" \
    "$STATUS $(grep -c "no admin token in $SANDBOX/admin-token.secret: run sandbox.sh services" "$OUTPUT") $([[ -e $HANDED ]] && echo ran)" \
    '1 1 '
}

# docker/ui-entrypoint.sh, which the GUI's dev server starts with: it hands the command over with
# the admin's token as the dev proxy's bearer token, and nothing else.
test_ui_entrypoint() {
  local entrypoint admin=header.payload.signature token=$WORK/ui/admin-token.secret
  entrypoint=$(dirname "$ENTRYPOINT")/ui-entrypoint.sh
  mkdir -p "$(dirname "$token")"
  printf '%s\n' "$admin" >"$token"
  cat >"$STUBS/ui-handed-to" <<'EOF'
#!/bin/bash
printf 'args=%s\nPROXY_BEARER_TOKEN=%s\n' "$*" "${PROXY_BEARER_TOKEN-}" >"$HANDED"
EOF
  chmod +x "$STUBS/ui-handed-to"
  rm -f "$HANDED"
  if SANDBOX_TOKEN_FILE=$token bash "$entrypoint" "$STUBS/ui-handed-to" start >"$OUTPUT" 2>&1; then
    STATUS=0
  else
    STATUS=$?
  fi
  check "the GUI's entrypoint hands the command over, with the admin's token for the dev proxy, without its newline" \
    "$STATUS $(handed args) $(handed PROXY_BEARER_TOKEN)" "0 start $admin"
  check "and prints nothing" "$(wc -c <"$OUTPUT" | tr -d ' ')" 0

  rm -f "$HANDED" "$token"
  if SANDBOX_TOKEN_FILE=$token bash "$entrypoint" "$STUBS/ui-handed-to" >"$OUTPUT" 2>&1; then
    STATUS=0
  else
    STATUS=$?
  fi
  check "without the token, it says so and runs nothing" \
    "$STATUS $(grep -c "no admin token in $token: run sandbox.sh services" "$OUTPUT") $([[ -e $HANDED ]] && echo ran)" \
    '1 1 '
}

set_up
test_entrypoint
test_service_entrypoint
test_ui_entrypoint
rm -rf "$WORK"
if ((FAILURES)); then
  printf '%s check(s) failed\n' "$FAILURES"
  exit 1
fi
