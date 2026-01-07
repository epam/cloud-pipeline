# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import os

# Kubernetes Namespaces & Labels
CP_KUBE_NAMESPACE = os.getenv("CP_KUBE_NAMESPACE", "default")
EDGE_SVC_ROLE_LABEL = 'cloud-pipeline/role'
EDGE_SVC_ROLE_LABEL_VALUE = 'EDGE'
EDGE_SVC_HOST_LABEL = 'cloud-pipeline/external-host'
EDGE_SVC_PORT_LABEL = 'cloud-pipeline/external-port'
EDGE_SVC_REGION_LABEL = 'cloud-pipeline/region'

# Endpoint Customization
CP_CAP_CUSTOM_ENDPOINT_PREFIX = 'CP_CAP_CUSTOM_TOOL_ENDPOINT_'
CP_EDGE_ENDPOINT_TAG_NAME = 'CP_EDGE_ENDPOINT_TAG_NAME'

# String Templates
SVC_PORT_TMPL = 'svc-port-'
SVC_PATH_TMPL = 'svc-path-'
SVC_URL_TMPL = '{{ ' \
               '"url" : "{external_schema}://{external_ip}:{edge_port}/{edge_location}", ' \
               '"name": "{service_name}", ' \
               '"isDefault": {is_default_endpoint}, ' \
               '"sameTab": {is_same_tab}, ' \
               '"customDNS": {is_custom_dns}, ' \
               '"regionId": {region_id} ' \
               '}}'
ROUTE_ID_TMPL = '{pod_id}-{endpoint_port}-{endpoint_num}'
ROUTE_ID_PATTERN = r'^(.*)-(\d+)-(\d+)$'
EDGE_ROUTE_TARGET_TMPL = '{pod_ip}:{endpoint_port}'
EDGE_ROUTE_TARGET_PATH_TMPL = '{pod_ip}:{endpoint_port}/{endpoint_path}'

# Feature Flags & Constants
EDGE_ROUTE_NO_PATH_CROP = 'CP_EDGE_NO_PATH_CROP'
EDGE_ROUTE_CREATE_DNS = 'CP_EDGE_ROUTE_CREATE_DNS'
EDGE_COOKIE_NO_REPLACE = 'CP_EDGE_COOKIE_NO_REPLACE'
EDGE_JWT_NO_AUTH = 'CP_EDGE_JWT_NO_AUTH'
EDGE_PASS_BEARER = 'CP_EDGE_PASS_BEARER'
EDGE_BEARER_COOKIE_EXTRA = os.getenv('CP_EDGE_BEARER_COOKIE_EXTRA', '')
EDGE_DNS_RECORD_FORMAT = os.getenv('CP_EDGE_DNS_RECORD_FORMAT', '{job_name}.{region_name}')
EDGE_DISABLE_NAME_SUFFIX_FOR_DEFAULT_ENDPOINT = os.getenv('EDGE_DISABLE_NAME_SUFFIX_FOR_DEFAULT_ENDPOINT', 'True').lower() == 'true'
EDGE_EXTERNAL_APP = 'CP_EDGE_EXTERNAL_APP'
EDGE_INSTANCE_IP = 'CP_EDGE_INSTANCE_IP'

# API Constants
RUN_ID = 'runid'
API_UPDATE_SVC = 'run/{run_id}/serviceUrl?region={region}'
API_GET_RUNS_LIST_DETAILS = 'runs?runIds={run_ids}'
API_POST_DNS_RECORD = 'cluster/dnsrecord'
API_GET_PREF = 'preferences/{preference_name}'
NUMBER_OF_RETRIES = 10
SECS_TO_WAIT_BEFORE_RETRY = 15

# Nginx Paths
STUB_LOCATION_CONFIG_EXTENSION = '.stub.loc.conf'
STUB_CUSTOM_DOMAIN_EXTENSION = '.stub.conf'
NGINX_CUSTOM_DOMAIN_CONFIG_EXT = '.srv.conf'
NGINX_CUSTOM_DOMAIN_LOC_SUFFIX = 'CP_EDGE_CUSTOM_DOMAIN'
NGINX_CUSTOM_DOMAIN_LOC_TMPL = 'include {}; # ' + NGINX_CUSTOM_DOMAIN_LOC_SUFFIX
NGINX_ROOT_CONFIG_PATH = '/etc/nginx/nginx.conf'
NGINX_SITES_PATH = '/etc/nginx/sites-enabled'
NGINX_DOMAINS_PATH = '/etc/nginx/sites-enabled/custom-domains'
EXTERNAL_APPS_DOMAINS_PATH = '/etc/nginx/external-apps'
API_DOMAIN_PATH = '/etc/nginx/ingress/cp-api-srv.conf'
NGINX_LOC_MODULE_TEMPLATE = '/etc/nginx/endpoints-config/route.template.loc.conf'
NGINX_SRV_MODULE_TEMPLATE = '/etc/nginx/endpoints-config/route.template' + NGINX_CUSTOM_DOMAIN_CONFIG_EXT
NGINX_SENSITIVE_LOC_MODULE_TEMPLATE = '/etc/nginx/endpoints-config/sensitive.template.loc.conf'
NGINX_LOC_MODULE_STUB_TEMPLATE = '/etc/nginx/endpoints-config/route.template.stub.loc.conf'
NGINX_SENSITIVE_ROUTES_CONFIG_PATH = '/etc/nginx/endpoints-config/sensitive.routes.json'
NGINX_SYSTEM_ENDPOINTS_CONFIG_PATH = '/etc/nginx/endpoints-config/system_endpoints.json'
NGINX_DEFAULT_LOCATION_ATTRIBUTES_PATH = '/etc/nginx/endpoints-config/default_location_attributes.json'

# PKI Paths
PKI_SEARCH_PATH = '/etc/edge/pki/'
PKI_SEARCH_SUFFIX_CERT = '-public-cert.pem'
PKI_SEARCH_SUFFIX_KEY = '-private-key.pem'
PKI_DEFAULT_CERT = '/etc/edge/pki/ssl-public-cert.pem'
PKI_DEFAULT_CERT_KEY = '/etc/edge/pki/ssl-private-key.pem'

# Other
EDGE_SERVICE_PORT = 31000
DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
