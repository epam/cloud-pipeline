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
CP_GITLAB_BLOCK_AUTO_CREATED_USERS="${CP_GITLAB_BLOCK_AUTO_CREATED_USERS:-"false"}"
CP_GITLAB_EXTERNAL_URL="${CP_GITLAB_EXTERNAL_URL:-https://${CP_GITLAB_INTERNAL_HOST}:${CP_GITLAB_INTERNAL_PORT}}"
CP_GITLAB_SSO_ENDPOINT_ID="${CP_GITLAB_SSO_ENDPOINT_ID:-https://${CP_GITLAB_EXTERNAL_HOST}:${CP_GITLAB_EXTERNAL_PORT}}"
CP_GITLAB_SAML_USER_ATTRIBUTES="${CP_GITLAB_SAML_USER_ATTRIBUTES:-email: ['email']}"
CP_GITLAB_CA_CERT_PATH="${CP_GITLAB_CA_CERT_PATH:-/opt/common/pki/ca-public-cert.pem}"
CP_GITLAB_SSL_CIPHERS="${CP_GITLAB_SSL_CIPHERS:-ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-GCM-SHA256:EECDH+AESGCM:EDH+AESGCM:AES256+EECDH:AES256+EDH:EECDH+AESGCM:EDH+AESGCM:ECDHE-RSA-AES128-GCM-SHA256:AES256+EECDH:DHE-RSA-AES128-GCM-SHA256:AES256+EDH:ECDHE-RSA-AES256-GCM-SHA384:DHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-SHA384:ECDHE-RSA-AES128-SHA256:ECDHE-RSA-AES256-SHA:ECDHE-RSA-AES128-SHA:DHE-RSA-AES256-SHA256:DHE-RSA-AES128-SHA256:DHE-RSA-AES256-SHA:DHE-RSA-AES128-SHA:ECDHE-RSA-DES-CBC3-SHA:EDH-RSA-DES-CBC3-SHA:AES256-GCM-SHA384:AES128-GCM-SHA256:AES256-SHA256:AES128-SHA256:AES256-SHA}"

if [ -f "$CP_GITLAB_CA_CERT_PATH" ]; then
  \cp "$CP_GITLAB_CA_CERT_PATH" /etc/gitlab/trusted-certs/
  echo
  echo "${CP_GITLAB_CA_CERT_PATH} added to /etc/gitlab/trusted-certs/"
  echo
else
  echo
  echo "CP_GITLAB_CA_CERT_PATH is set to ${CP_GITLAB_CA_CERT_PATH}, but the file does not exist. It will not be added to /etc/gitlab/trusted-certs/"
  echo
fi


echo
echo "idp_sso_target_url: $CP_GITLAB_SSO_TARGET_URL"
echo "idp_sso_target_url: $CP_GITLAB_SLO_TARGET_URL"
echo

# If the proxies are not set via env vars, gitlab will consider empty values as "no proxy set"
CP_GITLAB_HTTP_PROXY="${CP_GITLAB_HTTP_PROXY:-$http_proxy}"
CP_GITLAB_HTTPS_PROXY="${CP_GITLAB_HTTPS_PROXY:-$https_proxy}"
CP_GITLAB_NO_PROXY="${CP_GITLAB_NO_PROXY:-$no_proxy}"
GIT_PROXIES="gitlab_rails['env'] = {
  \"http_proxy\" => \"$CP_GITLAB_HTTP_PROXY\",
  \"https_proxy\" => \"$CP_GITLAB_HTTPS_PROXY\",
  \"no_proxy\" => \"$CP_GITLAB_NO_PROXY\"
}"

# If the smtp configuration is available - add it to gitlab as well
if [ "$CP_NOTIFIER_SMTP_FROM" ] && \
    [ "$CP_NOTIFIER_SMTP_SERVER_HOST" ] && \
    [ "$CP_NOTIFIER_SMTP_SERVER_PORT" ]; then
    echo
    echo "SMTP configuration available:"
    echo "  Host: ${CP_NOTIFIER_SMTP_SERVER_HOST}:${CP_NOTIFIER_SMTP_SERVER_PORT}"
    echo "  User: $CP_NOTIFIER_SMTP_USER"
    echo "  From: $CP_NOTIFIER_SMTP_FROM"
    echo

    if [ "$CP_NOTIFIER_SMTP_USER" ]; then
      SMTP_SETTINGS_USERNAME="gitlab_rails['smtp_user_name'] = \"$CP_NOTIFIER_SMTP_USER\""
      SMTP_SETTINGS_AUTH_TYPE="gitlab_rails['smtp_authentication'] = \"login\""
    fi

    if [ "$CP_NOTIFIER_SMTP_PASS" ]; then
      SMTP_SETTINGS_PASS="gitlab_rails['smtp_password'] = \"$CP_NOTIFIER_SMTP_PASS\""
      SMTP_SETTINGS_AUTH_TYPE="gitlab_rails['smtp_authentication'] = \"login\""
    fi
    

    SMTP_SETTINGS="gitlab_rails['smtp_enable'] = true
gitlab_rails['smtp_address'] = \"$CP_NOTIFIER_SMTP_SERVER_HOST\"
gitlab_rails['smtp_port'] = $CP_NOTIFIER_SMTP_SERVER_PORT
gitlab_rails['smtp_domain'] = \"$CP_NOTIFIER_SMTP_SERVER_HOST\"
gitlab_rails['smtp_enable_starttls_auto'] = ${CP_NOTIFIER_SMTP_START_TLS:-false}
gitlab_rails['gitlab_email_from'] = \"$CP_NOTIFIER_SMTP_FROM\"
$SMTP_SETTINGS_USERNAME
$SMTP_SETTINGS_PASS
$SMTP_SETTINGS_AUTH_TYPE"
fi


#######################
# LFS/ObjStore settings
#######################

if [ "$CP_CLOUD_PLATFORM" == "aws" ]; then
    if [ -z "$CP_GITLAB_OBJ_STORE_STORAGE_KEY_ID" ] && [ -f "$CP_CLOUD_CREDENTIALS_LOCATION" ]; then
      CP_GITLAB_OBJ_STORE_STORAGE_KEY_NAME="$(echo $(cat $CP_CLOUD_CREDENTIALS_LOCATION | grep aws_access_key_id | cut -d'=' -f2))"
    fi
    if [ -z "$CP_GITLAB_OBJ_STORE_KEY_SECRET" ] && [ -f "$CP_CLOUD_CREDENTIALS_LOCATION" ]; then
      CP_GITLAB_OBJ_STORE_KEY_SECRET="$(echo $(cat $CP_CLOUD_CREDENTIALS_LOCATION | grep aws_secret_access_key | cut -d'=' -f2))"
    fi
    if [ -z "$CP_GITLAB_OBJ_STORE_STORAGE_KEY_ID" ] || [ -z "$CP_GITLAB_OBJ_STORE_KEY_SECRET" ]; then
      echo "[INFO] object_store: Access keys are NOT set, instance profile will be used"
      LFS_SETTINGS_CREDENTIALS="'use_iam_profile' => true"
    else
      echo "[INFO] object_store: Access keys are provided"
      LFS_SETTINGS_CREDENTIALS="'aws_access_key_id' => '${CP_GITLAB_OBJ_STORE_STORAGE_KEY_ID}',
'aws_secret_access_key' => '${CP_GITLAB_OBJ_STORE_KEY_SECRET}'"
    fi
else
  export CP_GITLAB_OBJ_STORE_ENABLED=false
  echo "[WARN] object_store: Object store is supported for the AWS only. It won't be configured"
fi

CP_GITLAB_OBJ_STORE_LFS_BUCKET="${CP_GITLAB_OBJ_STORE_LFS_BUCKET:-$CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}"
if [ -z "$CP_GITLAB_OBJ_STORE_LFS_BUCKET" ]; then
  export CP_GITLAB_OBJ_STORE_ENABLED=false
  echo "[WARN] object_store: Object store bucket name for lfs is not defined. Shall be set via CP_GITLAB_OBJ_STORE_LFS_BUCKET"
else
  echo "[WARN] object_store: Object store bucket name for lfs is set to $CP_GITLAB_OBJ_STORE_LFS_BUCKET"
fi

CP_GITLAB_OBJ_STORE_REGION=${CP_GITLAB_OBJ_STORE_REGION:-$CP_CLOUD_REGION_ID}
if [ -z "$CP_GITLAB_OBJ_STORE_REGION" ]; then
  export CP_GITLAB_OBJ_STORE_ENABLED=false
  echo "[WARN] object_store: Object store bucket region is not not defined. Shall be set via CP_GITLAB_OBJ_STORE_REGION"
else
  echo "[WARN] object_store: Object store bucket region is set to $CP_GITLAB_OBJ_STORE_REGION"
fi

if [ "$CP_GITLAB_OBJ_STORE_ENABLED" == "true" ]; then
  LFS_SETTINGS="gitlab_rails['object_store']['enabled'] = ${CP_GITLAB_OBJ_STORE_ENABLED:-true}
gitlab_rails['object_store']['proxy_download'] = ${CP_GITLAB_OBJ_STORE_PROXY_DOWNLOAD:-false}
gitlab_rails['object_store']['connection'] = {
  'provider' => 'AWS',
  'region' => 'us-east-1',
  ${LFS_SETTINGS_CREDENTIALS}
}
gitlab_rails['object_store']['objects']['lfs']['enabled'] = true
gitlab_rails['object_store']['objects']['lfs']['bucket'] = '${CP_GITLAB_OBJ_STORE_LFS_BUCKET}'
gitlab_rails['object_store']['objects']['artifacts']['enabled'] = false
gitlab_rails['object_store']['objects']['packages']['enabled'] = false
gitlab_rails['object_store']['objects']['external_diffs']['enabled'] = false
gitlab_rails['object_store']['objects']['uploads']['enabled'] = false
gitlab_rails['object_store']['objects']['dependency_proxy']['enabled'] = false
gitlab_rails['object_store']['objects']['terraform_state']['enabled'] = false
gitlab_rails['object_store']['objects']['pages']['enabled'] = false"
fi

#######################

cat > /etc/gitlab/gitlab.rb <<-EOF

${GIT_PROXIES}

gitlab_rails['db_adapter'] = '${GITLAB_DATABASE_ADAPTER}'
gitlab_rails['db_encoding'] = '${GITLAB_DATABASE_ENCODING}'
gitlab_rails['db_host'] = '${GITLAB_DATABASE_HOST}'
gitlab_rails['db_port'] = ${GITLAB_DATABASE_PORT}
gitlab_rails['db_username'] = '${GITLAB_DATABASE_USERNAME}'
gitlab_rails['db_password'] = '${GITLAB_DATABASE_PASSWORD}'

external_url '${CP_GITLAB_EXTERNAL_URL}'
nginx['ssl_certificate'] = "/opt/gitlab/pki/ssl-public-cert.pem"
nginx['ssl_certificate_key'] = "/opt/gitlab/pki/ssl-private-key.pem"
nginx['ssl_ciphers'] = "${CP_GITLAB_SSL_CIPHERS}"
nginx['listen_port'] = ${CP_GITLAB_INTERNAL_PORT}
prometheus_monitoring['enable'] = false

omnibus_gitconfig['system'] = { "pack" => ["windowMemory = ${CP_GITLAB_WINDOW_MEMORY}", "packSizeLimit = ${CP_GITLAB_PACK_SIZE_LIMIT}"]}

gitlab_rails['omniauth_enabled'] = true
gitlab_rails['omniauth_allow_single_sign_on'] = ['saml']
gitlab_rails['omniauth_auto_link_saml_user'] = true
gitlab_rails['omniauth_block_auto_created_users'] = ${CP_GITLAB_BLOCK_AUTO_CREATED_USERS}
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

${SMTP_SETTINGS}
${LFS_SETTINGS}
EOF

# Start the gitlab runner
/gitlab-runner-scripts/init-gitlab-runner.sh &
