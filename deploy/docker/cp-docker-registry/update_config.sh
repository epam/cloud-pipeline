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

config_path=${1:-/etc/docker/registry/config.yml}

# Setup certificates trust
ln -s /usr/local/share/ca-certificates/cp-api/ssl-public-cert.pem  /usr/local/share/ca-certificates/cp-api-ssl-public-cert.pem
update-ca-certificates

# Setup storage driver configuration (S3 / Azure BLOB / Local FS)
storage_driver_config=""


if [ "$CP_DOCKER_STORAGE_TYPE" == "obj" ]; then
  CP_DOCKER_STORAGE_CHUNKSIZE="${CP_DOCKER_STORAGE_CHUNKSIZE:-52428800}"
  if [ "$CP_CLOUD_PLATFORM" == "aws" ]; then
    echo "S3 storage driver will be configured"

    if [ -z "$CP_DOCKER_STORAGE_KEY_NAME" ]; then
      CP_DOCKER_STORAGE_KEY_NAME="$(echo $(cat $CP_CLOUD_CREDENTIALS_LOCATION | grep aws_access_key_id | cut -d'=' -f2))"
    fi
    if [ -z "$CP_DOCKER_STORAGE_KEY_SECRET" ]; then
      CP_DOCKER_STORAGE_KEY_SECRET="$(echo $(cat $CP_CLOUD_CREDENTIALS_LOCATION | grep aws_secret_access_key | cut -d'=' -f2))"
    fi
    if [ -z "$CP_DOCKER_STORAGE_ROOT_DIR" ]; then
      CP_DOCKER_STORAGE_ROOT_DIR="cloud-pipeline-${CP_DEPLOYMENT_ID:-dockers}"
    fi

read -r -d '' storage_driver_config <<-EOF
  s3:
    region: ${CP_DOCKER_STORAGE_REGION:-$CP_CLOUD_REGION_ID}
    bucket: ${CP_DOCKER_STORAGE_CONTAINER}
    accesskey: ${CP_DOCKER_STORAGE_KEY_NAME}
    secretkey: ${CP_DOCKER_STORAGE_KEY_SECRET}
    secure: true
    chunksize: ${CP_DOCKER_STORAGE_CHUNKSIZE}
    rootdirectory: ${CP_DOCKER_STORAGE_ROOT_DIR}
EOF

  elif [ "$CP_CLOUD_PLATFORM" == "az" ]; then
    echo "Azure storage driver will be configured"

    CP_DOCKER_STORAGE_KEY_NAME=${CP_DOCKER_STORAGE_KEY_NAME:-$CP_AZURE_STORAGE_ACCOUNT}
    CP_DOCKER_STORAGE_KEY_SECRET=${CP_DOCKER_STORAGE_KEY_SECRET:-$CP_AZURE_STORAGE_KEY}

read -r -d '' storage_driver_config <<-EOF
  azure: 
    accountname: ${CP_DOCKER_STORAGE_KEY_NAME}
    accountkey: ${CP_DOCKER_STORAGE_KEY_SECRET}
    container: ${CP_DOCKER_STORAGE_CONTAINER}
    realm: core.windows.net
EOF

  else
    echo "WARN: Cloud Platform \"$CP_CLOUD_PLATFORM\" is not supported for the docker registry storage backend. Local filesystem will be used"
  fi
fi

if [ -z "$storage_driver_config" ]; then
  echo "Setting default local filesystem driver configuration"

read -r -d '' storage_driver_config <<-EOF
  filesystem:
    rootdirectory: /var/lib/registry
    maxthreads: 100
EOF

fi

# Update config file with general params
cat > $config_path <<-EOF
version: 0.1
log:
  fields:
    service: registry
auth:
  token:
    realm: https://{REALM_ADDRESS}/pipeline/restapi/dockerRegistry/oauth
    service: ${CP_DOCKER_INTERNAL_HOST}:${CP_DOCKER_INTERNAL_PORT}
    issuer: "Cloud pipeline"
    rootcertbundle: /usr/local/share/ca-certificates/cp-api/jwt.key.x509
notifications:
  endpoints:
    - name: tools_notifications
      disabled: false
      url: https://${CP_API_SRV_INTERNAL_HOST}:${CP_API_SRV_INTERNAL_PORT}/pipeline/restapi/dockerRegistry/notify
      headers:
             Authorization: [Bearer ${CP_API_JWT_ADMIN}]
             Registry-Path: [${CP_DOCKER_INTERNAL_HOST}:${CP_DOCKER_INTERNAL_PORT}]
      timeout: 1s
      threshold: 5
      backoff: 1s
      ignoredmediatypes:
        - application/octet-stream
        - application/vnd.docker.container.image.rootfs.diff+x-gtar
        - application/vnd.docker.distribution.manifest.list.v2+json
        - application/vnd.docker.container.image.v1+json
        - application/vnd.docker.image.rootfs.diff.tar.gzip
        - application/vnd.docker.image.rootfs.foreign.diff.tar.gzip
        - application/vnd.docker.plugin.v1+json
health:
  storagedriver:
    enabled: true
    interval: 10s
    threshold: 3
storage:
  cache:
    blobdescriptor: inmemory
  delete:
    enabled: true
EOF

# Update config file with storage driver params
echo "${storage_driver_config}" >> $config_path
