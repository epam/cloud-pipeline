#!/bin/bash
# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

CP_GITLAB_IDP_CERT_PATH="${CP_GITLAB_IDP_CERT_PATH:-$CP_IDP_CERT_DIR}"
IDP_CERT_PATH=$CP_GITLAB_IDP_CERT_PATH/idp-public-cert.pem
GIT_SSO_CERT_PATH=$CP_GITLAB_CERT_DIR/sso-public-cert.pem
GIT_SSO_KEY_PATH=$CP_GITLAB_CERT_DIR/sso-private-key.pem

echo
echo "IDP_CERT_PATH:     $IDP_CERT_PATH"
echo "GIT_SSO_CERT_PATH: $GIT_SSO_CERT_PATH"
echo "GIT_SSO_KEY_PATH:  $GIT_SSO_KEY_PATH"
echo

if [ ! -f $IDP_CERT_PATH ]; then
    echo "IdP certificate not found at $IDP_CERT_PATH - gitlab cannot initialize SAML SSO and will not start"
    exit 1
fi
if [ ! -f $GIT_SSO_CERT_PATH ]; then
    echo "Git SSO certificate not found at $GIT_SSO_CERT_PATH - gitlab cannot initialize SAML SSO and will not start"
    exit 1
fi
if [ ! -f $GIT_SSO_KEY_PATH ]; then
    echo "Git SSO key not found at $GIT_SSO_KEY_PATH - gitlab cannot initialize SAML SSO and will not start"
    exit 1
fi
        
IDP_CERT_CONTENTS="$(<$IDP_CERT_PATH)"
GIT_SSO_CERT_CONTENTS="$(<$GIT_SSO_CERT_PATH)"
GIT_SSO_KEY_CONTENTS="$(<$GIT_SSO_KEY_PATH)"

CP_GITLAB_SSO_TARGET_URL_TRAIL="${CP_GITLAB_SSO_TARGET_URL_TRAIL:-"/saml/sso"}"
CP_GITLAB_SLO_TARGET_URL_TRAIL="${CP_GITLAB_SLO_TARGET_URL_TRAIL:-"/saml/sso"}"
CP_GITLAB_SSO_TARGET_URL="${CP_GITLAB_SSO_TARGET_URL:-"https://${CP_IDP_EXTERNAL_HOST}:${CP_IDP_EXTERNAL_PORT}${CP_GITLAB_SSO_TARGET_URL_TRAIL}"}"
CP_GITLAB_SLO_TARGET_URL="${CP_GITLAB_SLO_TARGET_URL:-"https://${CP_IDP_EXTERNAL_HOST}:${CP_IDP_EXTERNAL_PORT}${CP_GITLAB_SLO_TARGET_URL_TRAIL}"}"
CP_GITLAB_WINDOW_MEMORY="${CP_GITLAB_WINDOW_MEMORY:-"128m"}"
CP_GITLAB_PACK_SIZE_LIMIT="${CP_GITLAB_PACK_SIZE_LIMIT:-"512m"}"
CP_GITLAB_EXTERNAL_URL="${CP_GITLAB_EXTERNAL_URL:-https://${CP_GITLAB_INTERNAL_HOST}:${CP_GITLAB_INTERNAL_PORT}}"
CP_GITLAB_SSO_ENDPOINT_ID="${CP_GITLAB_SSO_ENDPOINT_ID:-https://${CP_GITLAB_EXTERNAL_HOST}:${CP_GITLAB_EXTERNAL_PORT}}"
CP_GITLAB_SAML_USER_ATTRIBUTES="${CP_GITLAB_SAML_USER_ATTRIBUTES:-email: ['email']}"

echo
echo "idp_sso_target_url: $CP_GITLAB_SSO_TARGET_URL"
echo "idp_sso_target_url: $CP_GITLAB_SLO_TARGET_URL"
echo

cat >> /etc/gitlab/gitlab.rb <<-EOF

gitlab_rails['db_adapter'] = '${GITLAB_DATABASE_ADAPTER}'
gitlab_rails['db_encoding'] = '${GITLAB_DATABASE_ENCODING}'
gitlab_rails['db_host'] = '${GITLAB_DATABASE_HOST}'
gitlab_rails['db_port'] = ${GITLAB_DATABASE_PORT}
gitlab_rails['db_username'] = '${GITLAB_DATABASE_USERNAME}'
gitlab_rails['db_password'] = '${GITLAB_DATABASE_PASSWORD}'

external_url '${CP_GITLAB_EXTERNAL_URL}'
nginx['ssl_certificate'] = "/opt/gitlab/pki/ssl-public-cert.pem"
nginx['ssl_certificate_key'] = "/opt/gitlab/pki/ssl-private-key.pem"
nginx['listen_port'] = ${CP_GITLAB_INTERNAL_PORT}
prometheus_monitoring['enable'] = false

omnibus_gitconfig['system'] = { "pack" => ["windowMemory = ${CP_GITLAB_WINDOW_MEMORY}", "packSizeLimit = ${CP_GITLAB_PACK_SIZE_LIMIT}"]}

gitlab_rails['omniauth_enabled'] = true
gitlab_rails['omniauth_allow_single_sign_on'] = ['saml']
gitlab_rails['omniauth_auto_link_saml_user'] = true
gitlab_rails['omniauth_block_auto_created_users'] = false
gitlab_rails['omniauth_auto_sign_in_with_provider'] = 'saml'
gitlab_rails['omniauth_providers'] = [
{
  name: 'saml',
  label: 'SSO Login',
  args: {
    assertion_consumer_service_url: '${CP_GITLAB_SSO_ENDPOINT_ID}/users/auth/saml/callback',
    idp_cert: "$IDP_CERT_CONTENTS",
    idp_sso_target_url: '$CP_GITLAB_SSO_TARGET_URL',
    idp_slo_target_url: '$CP_GITLAB_SLO_TARGET_URL',
    issuer: '${CP_GITLAB_SSO_ENDPOINT_ID}',
    name_identifier_format: 'urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified',
    allowed_clock_drift: 60,
    certificate: "$GIT_SSO_CERT_CONTENTS",
    private_key: "$GIT_SSO_KEY_CONTENTS",
    security: {
      authn_requests_signed: true,
      embed_sign: false,
      digest_method: "http://www.w3.org/2000/09/xmldsig#rsa-sha1",
      signature_method: "http://www.w3.org/2000/09/xmldsig#rsa-sha1"
    },
    attribute_statements: { $CP_GITLAB_SAML_USER_ATTRIBUTES }
  }
 }
]
EOF
