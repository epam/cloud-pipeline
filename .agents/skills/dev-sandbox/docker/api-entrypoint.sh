#!/bin/bash
#
# Entrypoint of the dev sandbox's API image. Runs as root, with NET_ADMIN and /dev/net/tun, and
# gets the sandbox's local state directory mounted read-only. Before it runs the command:
#   - routes the VPC through the tunnel's SOCKS proxy, so that the API and the node scripts it runs
#     reach the platform's nodes, the master's registry and the master's private address, where
#     the nodes reach this API;
#   - makes the keystore the API serves HTTPS with, and the metadata its SAML setup reads: the
#     identity provider's, fetched from SANDBOX_IDP_METADATA_URL, or one of an identity provider
#     that is not there; and has Java trust the API's certificate, which the API checks
#     base.api.host against;
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
# Where the API's SAML setup reads its identity provider's metadata, and the keystore it signs with,
# under CP_API_SSO_CONFIG. SSO_ALIAS is the key it signs with, saml.sign.key in the API's settings.
IDP_METADATA_FILE=$API_HOME/sso/cp-api-srv-fed-meta.xml
IDP_STORE_FILE=$API_HOME/sso/store.jks
IDP_FETCH_SECONDS=30
IDP_FETCH_RETRIES=2
SSO_ALIAS=sso
SSO_STORE_NAME=store.jks
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

# import_signing_key: the key the API signs its SAML authentication requests with, taken from the
# keystore of SANDBOX_API_SSO_DIR, whose certificate an identity provider has registered as the
# service provider's. Only that key is taken: Tomcat keeps serving the sandbox's own certificate,
# which the platform's nodes reach the API by, and which the API checks base.api.host against.
import_signing_key() {
  local store=$SANDBOX_API_SSO_DIR/$SSO_STORE_NAME
  if [[ ! -f $store ]]; then
    log "no $SSO_STORE_NAME in $SANDBOX_API_SSO_DIR, which SANDBOX_API_SSO_DIR names"
    exit 1
  fi
  if ! keytool -importkeystore -noprompt -srckeystore "$store" -srcstorepass "$STORE_PASSWORD" \
    -srcalias "$SSO_ALIAS" -destkeystore "$IDP_STORE_FILE" -deststoretype JKS \
    -deststorepass "$STORE_PASSWORD" -destkeypass "$STORE_PASSWORD" -destalias "$SSO_ALIAS" \
    >/dev/null 2>&1; then
    log "could not take the $SSO_ALIAS key of $store: it holds no such key, or its password is \
not the one the API's settings name"
    exit 1
  fi
  log "the API signs its authentication requests with the $SSO_ALIAS key of $store"
}

# Tomcat serves HTTPS with the key under the alias ssl, which is the sandbox's own: the platform's
# nodes reach the API at the master's address, and the API checks base.api.host against that
# certificate. The API's SAML setup signs its authentication requests with the key under sso, the
# sandbox's own as well, unless SANDBOX_API_SSO_DIR names a keystore holding one an identity
# provider has registered.
make_keystore() {
  local alias
  mkdir -p "$API_HOME/sso"
  rm -f "$IDP_STORE_FILE"
  for alias in ssl sso; do
    if [[ $alias == "$SSO_ALIAS" && -n ${SANDBOX_API_SSO_DIR:-} ]]; then
      import_signing_key
      continue
    fi
    openssl pkcs12 -export -in "$SANDBOX_DIR/api-tls.crt" -inkey "$SANDBOX_DIR/api-tls-key.pem" \
      -name "$alias" -passout "pass:$STORE_PASSWORD" -out "$API_HOME/$alias.p12"
    keytool -importkeystore -noprompt -srckeystore "$API_HOME/$alias.p12" -srcstoretype PKCS12 \
      -srcstorepass "$STORE_PASSWORD" -destkeystore "$IDP_STORE_FILE" -deststoretype JKS \
      -deststorepass "$STORE_PASSWORD" -destkeypass "$STORE_PASSWORD" -alias "$alias" >/dev/null
    rm -f "$API_HOME/$alias.p12"
  done
}

# fetch_idp_metadata <url>: the metadata of the identity provider the sandbox signs in through,
# which the API reads in place of the one below. A body that is not identity provider metadata
# stops the container here: the API would otherwise fail to start on it, leaving a Spring stack
# trace in its log in place of a line saying what is wrong.
fetch_idp_metadata() {
  local url=$1 fetched=$IDP_METADATA_FILE.fetched
  if ! curl --silent --show-error --fail --location --retry "$IDP_FETCH_RETRIES" \
    --max-time "$IDP_FETCH_SECONDS" --output "$fetched" "$url"; then
    rm -f "$fetched"
    log "could not fetch the identity provider's metadata from $url"
    exit 1
  fi
  if ! grep -q 'EntityDescriptor' "$fetched" || ! grep -q 'IDPSSODescriptor' "$fetched"; then
    rm -f "$fetched"
    log "$url answered no SAML identity provider metadata: no EntityDescriptor with an IDPSSODescriptor in it"
    exit 1
  fi
  mv "$fetched" "$IDP_METADATA_FILE"
  log "the API signs users in through the identity provider at $url"
}

# The API's SAML setup reads its identity provider's metadata as it starts, and the API fails to
# start without metadata it can parse. With SANDBOX_IDP_METADATA_URL, that is the metadata of the
# identity provider it names, and the API's port is published on the workstation's loopback address
# for the browser (lib/services.sh). Without it, the sandbox has no identity provider, so the
# metadata names one that is not there: the API's own certificate, and a sign-in address nothing
# serves. Its REST API takes the JWT tokens either way.
write_idp_metadata() {
  local cert
  if [[ -n ${SANDBOX_IDP_METADATA_URL:-} ]]; then
    fetch_idp_metadata "$SANDBOX_IDP_METADATA_URL"
    return 0
  fi
  cert=$(pem_body "$SANDBOX_DIR/api-tls.crt")
  cat >"$IDP_METADATA_FILE" <<EOF
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
