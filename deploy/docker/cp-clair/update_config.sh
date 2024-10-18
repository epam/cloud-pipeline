
# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

config_path=${1:-/config/config.yaml}

export CP_CLAIR_DATABASE_POOL_SIZE=${CP_CLAIR_DATABASE_POOL_SIZE:-5}

# Setup certificates
ca_cert_path="$CP_COMMON_CERT_DIR/ca-public-cert.pem"
mkdir -p /usr/local/share/ca-certificates/cp-docker-registry

if [ -f "$ca_cert_path" ]; then
  cp "$ca_cert_path" /usr/local/share/ca-certificates/cp-ca-public-cert.pem
  cp "$ca_cert_path" /usr/local/share/ca-certificates/cp-docker-registry/cp-ca-public-cert.pem
else
  echo "[WARN] CA certificate not found at $ca_cert_path"
fi

docker_cert_path="$CP_DOCKER_CERT_DIR/docker-public-cert.pem"
if [ -f "$ca_cert_path" ]; then
  cp "$docker_cert_path" /usr/local/share/ca-certificates/cp-docker-public-cert.pem
  cp "$docker_cert_path" /usr/local/share/ca-certificates/cp-docker-registry/cp-docker-public-cert.pem
else
  echo "[WARN] Docker registry certificate not found at $docker_cert_path"
fi
#

update-ca-certificates

mkdir -p $(dirname $config_path)
cat > $config_path <<-EOF
clair:
  database:
    # Database driver
    type: pgsql
    options:
      # PostgreSQL Connection string
      # https://www.postgresql.org/docs/current/static/libpq-connect.html#LIBPQ-CONNSTRING
      source: host=$CP_CLAIR_DATABASE_HOST port=$CP_CLAIR_DATABASE_PORT user=$CP_CLAIR_DATABASE_USERNAME password=$CP_CLAIR_DATABASE_PASSWORD sslmode=disable statement_timeout=60000

      # Number of elements kept in the cache
      # Values unlikely to change (e.g. namespaces) are cached in order to save prevent needless roundtrips to the database.
      cachesize: 16384

      # Maximum number of open connections allowed to database
      # If unspecified or <= 0 then no limit is enforced in Clair
      maxopenconnections: $CP_CLAIR_DATABASE_POOL_SIZE

      # 32-bit URL-safe base64 key used to encrypt pagination tokens
      # If one is not provided, it will be generated.
      # Multiple clair instances in the same cluster need the same value.
      paginationkey:

  api:
    # v3 grpc/RESTful API server address
    port: 8080

    # Health server address
    # This is an unencrypted endpoint useful for load balancers to check to healthiness of the clair server.
    healthport: 8081

    # Deadline before an API request will respond with a 503
    timeout: 900s

    # Optional PKI configuration
    # If you want to easily generate client certificates and CAs, try the following projects:
    # https://github.com/coreos/etcd-ca
    # https://github.com/cloudflare/cfssl
    servername:
    cafile:
    keyfile:
    certfile:

  worker:
    namespace_detectors:
      - os-release
      - lsb-release
      - apt-sources
      - alpine-release
      - redhat-release

    feature_listers:
      - apk
      - dpkg
      - rpm

  updater:
    # Frequency the database will be updated with vulnerabilities from the default data sources
    # The value 0 disables the updater entirely.
    interval: 24h
    enabledupdaters:
      - debian
      - ubuntu
      - rhel
      - oracle
      - alpine

  notifier:
    # Number of attempts before the notification is marked as failed to be sent
    attempts: 3

    # Duration before a failed notification is retried
    renotifyinterval: 2h

    http:
      # Optional endpoint that will receive notifications via POST requests
      endpoint:

      # Optional PKI configuration
      # If you want to easily generate client certificates and CAs, try the following projects:
      # https://github.com/cloudflare/cfssl
      # https://github.com/coreos/etcd-ca
      servername:
      cafile:
      keyfile:
      certfile:

      # Optional HTTP Proxy: must be a valid URL (including the scheme).
      proxy:
EOF
