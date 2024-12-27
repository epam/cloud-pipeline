
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
mkdir -p /etc/pki/ca-trust/source/anchors

if [ -f "$ca_cert_path" ]; then
  cp "$ca_cert_path" /etc/pki/ca-trust/source/anchors/cp-ca.pem
else
  echo "[WARN] CA certificate not found at $ca_cert_path"
fi

docker_cert_path="$CP_DOCKER_CERT_DIR/docker-public-cert.pem"
if [ -f "$ca_cert_path" ]; then
  cp "$docker_cert_path" /etc/pki/ca-trust/source/anchors/cp-docker-public-cert.pem
else
  echo "[WARN] Docker registry certificate not found at $docker_cert_path"
fi

update-ca-trust extract
#

mkdir -p $(dirname $config_path)
cat > $config_path <<-EOF
http_listen_addr: 0.0.0.0:8080
introspection_addr: 0.0.0.0:8081
log_level: debug
indexer:
  connstring: host=$CP_CLAIR_DATABASE_HOST port=$CP_CLAIR_DATABASE_PORT user=$CP_CLAIR_DATABASE_USERNAME dbname=$CP_CLAIR_DATABASE_DATABASE password=$CP_CLAIR_DATABASE_PASSWORD sslmode=disable
  scanlock_retry: 10
  layer_scan_concurrency: 5
  migrations: true
matcher:
  indexer_addr: localhost:8080
  connstring: host=$CP_CLAIR_DATABASE_HOST port=$CP_CLAIR_DATABASE_PORT user=$CP_CLAIR_DATABASE_USERNAME dbname=$CP_CLAIR_DATABASE_DATABASE password=$CP_CLAIR_DATABASE_PASSWORD sslmode=disable
  max_conn_pool: $CP_CLAIR_DATABASE_POOL_SIZE
  migrations: true
  period: 24h
notifier:
  indexer_addr: localhost:8080
  matcher_addr: localhost:8080
  connstring: host=$CP_CLAIR_DATABASE_HOST port=$CP_CLAIR_DATABASE_PORT user=$CP_CLAIR_DATABASE_USERNAME dbname=$CP_CLAIR_DATABASE_DATABASE password=$CP_CLAIR_DATABASE_PASSWORD sslmode=disable
  migrations: true
  poll_interval: 2h
  delivery_interval: 2h
metrics:
  name: "prometheus"
EOF
