#!/bin/bash
#
# Entrypoint of the dev sandbox's API image. Runs as root, with NET_ADMIN and /dev/net/tun, and
# gets the sandbox's local state directory mounted read-only. Before it runs the command:
#   - routes the VPC through the tunnel's SOCKS proxy, so that the API and the node scripts it runs
#     reach the platform's nodes, the master's registry and the master's private address, where
#     the nodes reach this API;
#   - makes the keystore the API serves HTTPS with, and the metadata of an identity provider that
#     is not there, which its SAML setup reads, and has Java trust the API's certificate, which
#     the API checks base.api.host against;
#   - exports the secrets the API's settings name, read from their files;
#   - copies the kubeconfig to ~/.kube/config, where the node scripts read it;
#   - copies the sandbox's SSH key to SANDBOX_NODE_SSH_KEY, for root only, which the API logs in
#     to a run's node with, for its commit, pause and the other operations there.
# Then tools-entrypoint.sh does what it does for the tools image: the config file's quotes and
# empty AWS variables, and a copy of ~/.aws.
#
# The environment sandbox.sh sets: SANDBOX_DIR, the sandbox's local state; SANDBOX_VPC_CIDRS, the
# VPC's address ranges, separated by spaces; SANDBOX_SOCKS_PROXY, host:port of the tunnel's SOCKS
# proxy; SANDBOX_MASTER_IP, the master's private address; SANDBOX_NODE_SSH_KEY, where the API's
# commit.deploy.key preference names the SSH key.
set -euo pipefail

API_HOME=/var/lib/cp-sandbox-api
STORE_PASSWORD=changeit
TUN_DEVICE=tun0
TUN_ADDRESS=198.18.0.1/15
ROUTE_CHECK_PORT=6443
ROUTE_CHECK_SECONDS=${ROUTE_CHECK_SECONDS:-30}
# What runs the command, once this is done.
TOOLS_ENTRYPOINT=${TOOLS_ENTRYPOINT:-/usr/local/bin/tools-entrypoint.sh}

: "${SANDBOX_DIR:?}" "${SANDBOX_VPC_CIDRS:?}" "${SANDBOX_SOCKS_PROXY:?}" "${SANDBOX_MASTER_IP:?}" \
  "${SANDBOX_NODE_SSH_KEY:?}"

log() {
  printf 'api-entrypoint: %s\n' "$*" >&2
}

# The SOCKS proxy is the tunnel container's, on the Docker network: tun2socks resolves its name
# for each connection, so a tunnel that comes back on another address is still found. Each step
# holds when the container starts again, with docker start.
route_vpc() {
  local cidr deadline=$((SECONDS + ROUTE_CHECK_SECONDS))
  ip link show dev "$TUN_DEVICE" >/dev/null 2>&1 || ip tuntap add mode tun dev "$TUN_DEVICE"
  ip addr replace "$TUN_ADDRESS" dev "$TUN_DEVICE"
  ip link set dev "$TUN_DEVICE" up
  tun2socks --device "tun://$TUN_DEVICE" --proxy "socks5://$SANDBOX_SOCKS_PROXY" --loglevel warn &
  for cidr in $SANDBOX_VPC_CIDRS; do
    ip route replace "$cidr" dev "$TUN_DEVICE"
  done
  log "the VPC ($SANDBOX_VPC_CIDRS) is routed through the SOCKS proxy at $SANDBOX_SOCKS_PROXY"
  until curl --silent --insecure --output /dev/null --max-time 5 \
    "https://$SANDBOX_MASTER_IP:$ROUTE_CHECK_PORT/healthz"; do
    if ((SECONDS >= deadline)); then
      log "warning: the master does not answer at $SANDBOX_MASTER_IP:$ROUTE_CHECK_PORT through the \
SOCKS proxy; the API starts all the same, but cannot reach the VPC until the tunnel works"
      return 0
    fi
    sleep 2
  done
}

# Tomcat serves HTTPS with the key under the alias ssl; the API's SAML setup wants a key under sso
# as well. Both are the API's own TLS key.
make_keystore() {
  local alias
  mkdir -p "$API_HOME/sso"
  rm -f "$API_HOME/sso/store.jks"
  for alias in ssl sso; do
    openssl pkcs12 -export -in "$SANDBOX_DIR/api-tls.crt" -inkey "$SANDBOX_DIR/api-tls-key.pem" \
      -name "$alias" -passout "pass:$STORE_PASSWORD" -out "$API_HOME/$alias.p12"
    keytool -importkeystore -noprompt -srckeystore "$API_HOME/$alias.p12" -srcstoretype PKCS12 \
      -srcstorepass "$STORE_PASSWORD" -destkeystore "$API_HOME/sso/store.jks" -deststoretype JKS \
      -deststorepass "$STORE_PASSWORD" -destkeypass "$STORE_PASSWORD" -alias "$alias" >/dev/null
    rm -f "$API_HOME/$alias.p12"
  done
}

# The API reads its SAML identity provider's metadata at startup, and retries, logging an error,
# every few minutes while there is none. The sandbox has no identity provider, so the metadata
# names one that is not there: the API's own certificate, and a sign-in address nothing serves.
# Its REST API takes the JWT tokens alone.
write_idp_metadata() {
  local cert
  cert=$(pem_body "$SANDBOX_DIR/api-tls.crt")
  cat >"$API_HOME/sso/cp-api-srv-fed-meta.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="urn:cp-sandbox:no-identity-provider">
  <md:IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
    <md:KeyDescriptor use="signing">
      <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
        <ds:X509Data><ds:X509Certificate>$cert</ds:X509Certificate></ds:X509Data>
      </ds:KeyInfo>
    </md:KeyDescriptor>
    <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://localhost/no-identity-provider"/>
  </md:IDPSSODescriptor>
</md:EntityDescriptor>
EOF
}

trust_api_certificate() {
  local cacerts
  for cacerts in "$JAVA_HOME/lib/security/cacerts" "$JAVA_HOME/jre/lib/security/cacerts"; do
    [[ -f $cacerts ]] || continue
    keytool -delete -noprompt -keystore "$cacerts" -storepass "$STORE_PASSWORD" \
      -alias cp-sandbox-api >/dev/null 2>&1 || true
    keytool -importcert -noprompt -keystore "$cacerts" -storepass "$STORE_PASSWORD" \
      -alias cp-sandbox-api -file "$SANDBOX_DIR/api-tls.crt" >/dev/null
    return 0
  done
  log "no cacerts under $JAVA_HOME"
  exit 1
}

# pem_body <file>: the base64 of a PEM file on one line, without its BEGIN and END lines: the form
# the API reads its JWT keys in.
pem_body() {
  sed '/^-----/d' "$1" | tr -d '\n'
}

export_secrets() {
  CP_API_JWT_KEY_PRIVATE=$(pem_body "$SANDBOX_DIR/jwt-private-key.pem")
  CP_API_JWT_KEY_PUBLIC=$(pem_body "$SANDBOX_DIR/jwt-public-key.pem")
  CP_KUBE_KUBEADM_TOKEN=$(tr -d '[:space:]' <"$SANDBOX_DIR/kube-join-token.secret")
  export CP_API_JWT_KEY_PRIVATE CP_API_JWT_KEY_PUBLIC CP_KUBE_KUBEADM_TOKEN
}

main() {
  mkdir -p "$API_HOME/work"
  route_vpc
  make_keystore
  write_idp_metadata
  trust_api_certificate
  export_secrets
  install -D -m 0600 "$SANDBOX_DIR/kubeconfig.secret" "$HOME/.kube/config"
  export KUBECONFIG=$HOME/.kube/config
  # A copy, at a path without spaces: the API splits the SSH command it runs at spaces, and ssh
  # refuses a key that others may read, as a mounted file may look.
  install -D -m 0600 "$SANDBOX_DIR/ssh-key.pem" "$SANDBOX_NODE_SSH_KEY"
  export CP_API_SSO_CONFIG=$API_HOME/sso CP_API_WORKDIR=$API_HOME/work
  # The node scripts write their logs to the working directory.
  cd "$CP_API_WORKDIR"
  exec "$TOOLS_ENTRYPOINT" "$@"
}

main "$@"
