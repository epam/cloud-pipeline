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

nginx_config_path=${1:-/etc/nginx/nginx.conf}

cat > /tmp/nginx.conf<<-'EOF'
events {
    worker_connections  1024;
}

http {

  server {
    set $docker_external_host "${CP_DOCKER_EXTERNAL_HOST}";
    set $realm_external_host "${CP_API_SRV_EXTERNAL_HOST}";
    set $realm_external_port "${CP_API_SRV_EXTERNAL_PORT}";
    set $realm_internal_host "${CP_API_SRV_INTERNAL_HOST}";
    set $realm_internal_port "${CP_API_SRV_INTERNAL_PORT}";
    set $docker_internal_host "${CP_DOCKER_INTERNAL_HOST}";
    set $docker_internal_port "${CP_DOCKER_INTERNAL_PORT}";

    listen 443 ssl;
    server_name _;

    # SSL
    ssl_certificate /opt/docker-registry/pki/docker-public-cert.pem;
    ssl_certificate_key /opt/docker-registry/pki/docker-private-key.pem;

    # Recommendations from https://raymii.org/s/tutorials/Strong_SSL_Security_On_nginx.html
    ssl_protocols TLSv1.1 TLSv1.2;
    ssl_ciphers 'EECDH+AESGCM:EDH+AESGCM:AES256+EECDH:AES256+EDH';
    ssl_prefer_server_ciphers on;
    ssl_session_cache shared:SSL:10m;

    # disable any limits to avoid HTTP 413 for large image uploads
    client_max_body_size 0;

    proxy_http_version      1.1;
    proxy_buffering         off;
    proxy_request_buffering off;

    # required to avoid HTTP 411: see Issue #1486 (https://github.com/moby/moby/issues/1486)
    chunked_transfer_encoding on;

    # Helth check for both nginx (if it can proxy - it is ok) and a registry
    # If registry responded HTTP 200 for "/" - it is fine (see https://github.com/docker/distribution/pull/874 for details)
    location /health {
      access_log off;
      proxy_pass http://127.0.0.1:80/;
      proxy_connect_timeout   5s;
      proxy_send_timeout      5s;
      proxy_read_timeout      5s;
    }

    location /v2/ {
      # Do not allow connections from docker 1.5 and earlier
      # docker pre-1.6.0 did not properly set the user agent on ping, catch "Go *" user agents
      if ($http_user_agent ~ "^(docker\/1\.(3|4|5(?!\.[0-9]-dev))|Go ).*$" ) {
        return 404;
      }

      proxy_pass                          http://127.0.0.1;
      proxy_set_header  Host              $http_host;   # required for docker client's sake
      proxy_set_header  X-Real-IP         $remote_addr; # pass on real client's IP
      proxy_set_header  X-Forwarded-For   $proxy_add_x_forwarded_for;
      proxy_set_header  X-Forwarded-Proto $scheme;
      proxy_read_timeout                  900;

      set $realm_endpoint "${realm_internal_host}:${realm_internal_port}";
      if ($host = $docker_external_host) {
          set $realm_endpoint "${realm_external_host}:${realm_external_port}";
      }

      proxy_hide_header Www-Authenticate;
      add_header Www-Authenticate 'Bearer realm="https://${realm_endpoint}/pipeline/restapi/dockerRegistry/oauth",service="${docker_internal_host}:${docker_internal_port}"' always;
    }
  }
}
EOF

envsubst '${CP_DOCKER_EXTERNAL_HOST} ${CP_API_SRV_EXTERNAL_HOST} ${CP_API_SRV_EXTERNAL_PORT} ${CP_API_SRV_INTERNAL_HOST} ${CP_API_SRV_INTERNAL_PORT} ${CP_DOCKER_INTERNAL_PORT} ${CP_DOCKER_INTERNAL_HOST} ${CP_DOCKER_INTERNAL_PORT}' < /tmp/nginx.conf > $nginx_config_path
rm -f /tmp/nginx.conf

nginx
