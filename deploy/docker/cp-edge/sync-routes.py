# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import glob
import json
import os
import re
import subprocess
from datetime import datetime
from multiprocessing.pool import ThreadPool as Pool

import requests
import time
import urllib3

CP_CAP_CUSTOM_ENDPOINT_PREFIX = 'CP_CAP_CUSTOM_TOOL_ENDPOINT_'

try:
        from pykube.config import KubeConfig
        from pykube.http import HTTPClient
        from pykube.http import HTTPError
        from pykube.objects import Pod
        from pykube.objects import Service
        from pykube.objects import Event
except ImportError:
        raise RuntimeError('pykube is not installed. KubernetesJobTask requires pykube.')

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
ROUTE_ID_PATTERN = '^(.*)-(\d+)-(\d+)$'
EDGE_ROUTE_TARGET_TMPL = '{pod_ip}:{endpoint_port}'
EDGE_ROUTE_TARGET_PATH_TMPL = '{pod_ip}:{endpoint_port}/{endpoint_path}'
EDGE_ROUTE_NO_PATH_CROP = 'CP_EDGE_NO_PATH_CROP'
EDGE_ROUTE_CREATE_DNS = 'CP_EDGE_ROUTE_CREATE_DNS'
EDGE_DNS_RECORD_FORMAT = os.getenv('CP_EDGE_DNS_RECORD_FORMAT', '{job_name}.{region_name}')
EDGE_EXTERNAL_APP = 'CP_EDGE_EXTERNAL_APP'
EDGE_INSTANCE_IP = 'CP_EDGE_INSTANCE_IP'
RUN_ID = 'runid'
API_UPDATE_SVC = 'run/{run_id}/serviceUrl?region={region}'
API_GET_RUNS_LIST_DETAILS = 'runs?runIds={run_ids}'
API_POST_DNS_RECORD = 'cluster/dnsrecord'
API_GET_PREF = 'preferences/{preference_name}'
NUMBER_OF_RETRIES = 10
SECS_TO_WAIT_BEFORE_RETRY = 15
STUB_LOCATION_CONFIG_EXTENSION = '.stub.loc.conf'
STUB_CUSTOM_DOMAIN_EXTENSION = '.stub.conf'

EDGE_SVC_ROLE_LABEL = 'cloud-pipeline/role'
EDGE_SVC_ROLE_LABEL_VALUE = 'EDGE'
EDGE_SVC_HOST_LABEL = 'cloud-pipeline/external-host'
EDGE_SVC_PORT_LABEL = 'cloud-pipeline/external-port'
EDGE_SVC_REGION_LABEL = 'cloud-pipeline/region'

nginx_custom_domain_config_ext = '.srv.conf'
nginx_custom_domain_loc_suffix = 'CP_EDGE_CUSTOM_DOMAIN'
nginx_custom_domain_loc_tmpl = 'include {}; # ' + nginx_custom_domain_loc_suffix
nginx_root_config_path = '/etc/nginx/nginx.conf'
nginx_sites_path = '/etc/nginx/sites-enabled'
nginx_domains_path = '/etc/nginx/sites-enabled/custom-domains'
external_apps_domains_path = '/etc/nginx/external-apps'
nginx_loc_module_template = '/etc/nginx/endpoints-config/route.template.loc.conf'
nginx_srv_module_template = '/etc/nginx/endpoints-config/route.template' + nginx_custom_domain_config_ext
nginx_sensitive_loc_module_template = '/etc/nginx/endpoints-config/sensitive.template.loc.conf'
nginx_loc_module_stub_template = '/etc/nginx/endpoints-config/route.template.stub.loc.conf'
nginx_sensitive_routes_config_path = '/etc/nginx/endpoints-config/sensitive.routes.json'
nginx_system_endpoints_config_path = '/etc/nginx/endpoints-config/system_endpoints.json'
nginx_default_location_attributes_path = '/etc/nginx/endpoints-config/default_location_attributes.json'
edge_service_port = 31000
edge_service_external_ip = ''
pki_search_path = '/etc/edge/pki/'
pki_search_suffix_cert = '-public-cert.pem'
pki_search_suffix_key = '-private-key.pem'
pki_default_cert = '/etc/edge/pki/ssl-public-cert.pem'
pki_default_cert_key = '/etc/edge/pki/ssl-private-key.pem'
DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"

DEFAULT_LOCATION_ATTRIBUTES = []
if os.path.exists(nginx_default_location_attributes_path):
        try:
                with open(nginx_default_location_attributes_path) as location_attributes_fd:
                        DEFAULT_LOCATION_ATTRIBUTES = json.load(location_attributes_fd)
        except Exception as loc_attr_read_exception:
                print('An error occured while reading default location attributes: {}'.format(loc_attr_read_exception))
else:
        print('Default location attributes config was not found at {}'.format(nginx_default_location_attributes_path))

urllib3.disable_warnings()
api_url = os.environ.get('API')
api_token = os.environ.get('API_TOKEN')
if not api_url or not api_token:
        print('API url or API token are not set. Exiting')
        exit(1)
edge_service_external_schema=os.environ.get('EDGE_EXTERNAL_SCHEMA', 'https')


api_headers = {'Content-Type': 'application/json',
               'Authorization': 'Bearer {}'.format(api_token)}

pool_size = 8
dns_services_pool = Pool(pool_size)

class ServiceEndpoint:
        def __init__(self, num, port, path, additional):
                self.num = num
                self.port = port
                self.path = path
                self.additional = additional

def do_log(msg):
        print('[{}] {}'.format(datetime.now().strftime("%Y-%m-%d %H:%M:%S"), msg))

def call_api(method_url, data=None):
        result = None
        for n in range(NUMBER_OF_RETRIES):
                try:
                        do_log('Calling API {}'.format(method_url))
                        if data:
                                response = requests.post(method_url, verify=False, data=data, headers=api_headers)
                        else:
                                response = requests.get(method_url, verify=False, headers=api_headers)
                        response_data = json.loads(response.text)
                        if response_data['status'] == 'OK':
                                do_log('Calling API ... OK')
                                result = response_data
                        else:
                                err_msg = 'No error message available'
                                if 'message' in response_data:
                                        err_msg = response_data['message']
                                do_log('Calling API ... NOT OK ({})\n{}'.format(method_url, err_msg))
                                do_log('As the API technically succeeded, it will not be retried')
                        break
                except Exception as api_exception:
                        do_log('Calling API ... NOT OK ({})\n{}'.format(method_url, str(api_exception)))

                if n < NUMBER_OF_RETRIES - 1:
                        do_log('Sleep for {} sec and perform API call again ({}/{})'.format(SECS_TO_WAIT_BEFORE_RETRY, n + 2, NUMBER_OF_RETRIES))
                        time.sleep(SECS_TO_WAIT_BEFORE_RETRY)
                else:
                        do_log('All attempts failed. API call failed')
        return result


# todo: Use RunLogger from pipe commons instead
class RunLogger:

        def __init__(self, run_id, task_name):
                self.run_id = run_id
                self.task_name = task_name

        def info(self, message):
                self._log(message=message, status='RUNNING')

        def warning(self, message):
                self._log(message='\033[93m' + message + '\033[0m', status='RUNNING')

        def success(self, message):
                self._log(message='\033[92m' + message + '\033[0m', status='SUCCESS')

        def _log(self, message, status):
                do_log("Log run log: " + message)
                now = datetime.utcfromtimestamp(time.time()).strftime(DATE_FORMAT)
                date = now[0:len(now) - 3]
                log_entry = json.dumps({"runId": self.run_id,
                                        "date": date,
                                        "status": status,
                                        "logText": message,
                                        "taskName": self.task_name})
                call_api(os.path.join(api_url, "run/{run_id}/log".format(run_id=self.run_id)), data=log_entry)


def run_sids_to_str(run_sids, is_principal):
        if not run_sids:
                return ""
        return ",".join([shared_sid["name"] for shared_sid in run_sids if shared_sid["isPrincipal"] == is_principal])

def parse_pretty_url(pretty):
        try:
                pretty_obj = json.loads(pretty)
                if not pretty_obj:
                        return None
        # todo: Use only specific exception types
        except Exception:
                pretty_obj = { 'path': pretty }

        pretty_domain = None
        pretty_path = None
        if 'domain' in pretty_obj and pretty_obj['domain']:
                pretty_domain = pretty_obj['domain']
        if 'path' in pretty_obj:
                pretty_path = pretty_obj['path']
                if pretty_path.startswith('/'):
                        pretty_path = pretty_path[len('/'):]

        if not pretty_domain and not pretty_path:
                return None
        else:
                return { 'domain': pretty_domain, 'path': pretty_path }

def substr_indices(lines, substr):
        return [i for i, line in enumerate(lines) if substr in line]

def store_file_from_lines(lines, path):
        with open(path, 'w') as path_file:
                path_file.write('\n'.join(lines))

def get_domain_config_path(domain, is_external_app=False):
        domains_path =  external_apps_domains_path if is_external_app else nginx_domains_path
        return os.path.join(domains_path, domain + nginx_custom_domain_config_ext)

def add_custom_domain(domain, location_block, is_external_app=False):
        if not os.path.isdir(nginx_domains_path):
                os.mkdir(nginx_domains_path)
        domain_path = get_domain_config_path(domain, is_external_app=is_external_app)
        domain_cert = search_custom_domain_cert(domain)
        if os.path.exists(domain_path):
                with open(domain_path, 'r') as domain_path_file:
                        domain_path_contents = domain_path_file.read()
        else:
                with open(nginx_srv_module_template, 'r') as nginx_srv_module_template_file:
                        domain_path_contents = nginx_srv_module_template_file.read()
                domain_path_contents = domain_path_contents \
                                        .replace('{edge_route_server_name}', domain) \
                                        .replace('{edge_route_server_ssl_certificate}', domain_cert[0]) \
                                        .replace('{edge_route_server_ssl_certificate_key}', domain_cert[1])

        location_block_include = nginx_custom_domain_loc_tmpl.format(location_block)
        domain_path_lines = domain_path_contents.splitlines()

        # Check if the location_block already added to the domain config
        existing_loc = substr_indices(domain_path_lines, location_block_include)
        if existing_loc:
                do_log('-> Location block {} already exists for domain {}'.format(location_block, domain))
                return

        # If it's a new location entry - add it to the domain config after the {edge_route_location_block} line
        insert_loc = substr_indices(domain_path_lines, '# {edge_route_location_block}')
        if not insert_loc:
                do_log('-> Cannot find an insert location in the domain config {}'.format(domain_path))
                return
        domain_path_lines.insert(insert_loc[-1] + 1, location_block_include)

        # Save the domain config back to file
        store_file_from_lines(domain_path_lines, domain_path)


def remove_custom_domain(domain, location_block, is_external_app=False):
        location_block_include = nginx_custom_domain_loc_tmpl.format(location_block)
        domain_path = get_domain_config_path(domain, is_external_app=is_external_app)
        if not os.path.exists(domain_path):
                return False
        domain_path_lines = []
        with open(domain_path, 'r') as domain_path_file:
                domain_path_contents = domain_path_file.read()
                domain_path_lines = domain_path_contents.splitlines()

        existing_loc = substr_indices(domain_path_lines, location_block_include)
        if not existing_loc:
                return False
        del domain_path_lines[existing_loc[-1]]

        if (not is_external_app and sum(nginx_custom_domain_loc_suffix in line for line in domain_path_lines) == 0):
                # If no more location block exist in the domain - delete the config file
                # Do not delete if this is an "external application", where the server block is managed externally
                do_log('-> No more location blocks are available for {}, deleting the config file: {}'.format(domain, domain_path))
                os.remove(domain_path)
        else:
                # Save the domain config back to file
                store_file_from_lines(domain_path_lines, domain_path)
        return True

def remove_custom_domain_all(location_block):
        for domains_root_path in [ nginx_domains_path, external_apps_domains_path ]:
                domain_path_list = [f for f in glob.glob(domains_root_path + '/*' + nginx_custom_domain_config_ext)]
                for domain_path in domain_path_list:
                        custom_domain = os.path.basename(domain_path).replace(nginx_custom_domain_config_ext, '')
                        is_external_app = domains_root_path == external_apps_domains_path
                        if remove_custom_domain(custom_domain, location_block, is_external_app=is_external_app):
                                do_log('-> Removed {} location block from {} domain config'.format(location_block, custom_domain))

def search_custom_domain_cert(domain):
        domain_cert_list = [f for f in glob.glob(pki_search_path + '/*' + pki_search_suffix_cert)]
        domain_cert_candidates = []
        for cert_path in domain_cert_list:
                cert_name = os.path.basename(cert_path).replace(pki_search_suffix_cert, '')
                if domain.endswith(cert_name):
                        domain_cert_candidates.append(cert_name)

        cert_path = None
        key_path = None
        if domain_cert_candidates:
                domain_cert_candidates.sort(key=len, reverse=True)
                cert_name = domain_cert_candidates[0]
                cert_path = os.path.join(pki_search_path, cert_name + pki_search_suffix_cert)
                key_path = os.path.join(pki_search_path, cert_name + pki_search_suffix_key)
                if not os.path.isfile(key_path):
                        do_log('-> Certificate for {} is found at {}, but a key does not exist at {}'.format(domain, cert_path, key_path))
                        key_path = None
        if not cert_path or not key_path:
                cert_path = pki_default_cert
                key_path = pki_default_cert_key

        do_log('-> Certificate:Key for {} will be used: {}:{}'.format(domain, cert_path, key_path))
        return cert_path, key_path

def read_system_endpoints():
        system_endpoints = {}
        with open(nginx_system_endpoints_config_path, 'r') as system_endpoints_file:
                system_endpoints_list = json.load(system_endpoints_file)
                for endpoint in system_endpoints_list:
                        system_endpoints[endpoint['name']] = {
                                "value": endpoint['value'] if 'value' in endpoint else "true",
                                "endpoint": str(os.environ.get(endpoint['endpoint_env'],
                                                               endpoint['endpoint_default'])),
                                "endpoint_num":  str(os.environ.get(endpoint['endpoint_num_env'],
                                                                    endpoint['endpoint_num_default'])),
                                "friendly_name": endpoint['friendly_name'],
                                "endpoint_additional": endpoint['endpoint_additional'] if 'endpoint_additional' in endpoint else '',
                                "endpoint_same_tab": endpoint['endpoint_same_tab'] if 'endpoint_same_tab' in endpoint else None,
                                "ssl_backend": endpoint['ssl_backend'] if 'ssl_backend' in endpoint else None
                        }
        return system_endpoints

SYSTEM_ENDPOINTS = read_system_endpoints()
SYSTEM_ENDPOINTS_NAMES = [endpoint['friendly_name'] for endpoint in SYSTEM_ENDPOINTS.values()]

def is_system_endpoint_name(endpoint):
        if endpoint and "name" in endpoint and endpoint["name"]:
                return endpoint["name"] in SYSTEM_ENDPOINTS_NAMES
        else:
                return False

# Function to construct endpoint was configured with Run Parameters.
# Group of Run Parameters started with CP_CAP_CUSTOM_TOOL_ENDPOINT_<num> considered as configuration of additional endpoint
# that should be available for this run. Full list of supported params are:
#
# CP_CAP_CUSTOM_TOOL_ENDPOINT_<num>_PORT
# CP_CAP_CUSTOM_TOOL_ENDPOINT_<num>_NAME
# CP_CAP_CUSTOM_TOOL_ENDPOINT_<num>_ADDITIONAL
# CP_CAP_CUSTOM_TOOL_ENDPOINT_<num>_NUM
# CP_CAP_CUSTOM_TOOL_ENDPOINT_<num>_SSL_BACKEND
# CP_CAP_CUSTOM_TOOL_ENDPOINT_<num>_SAME_TAB
#
# Method will group such parametes by <num> and construct from such group an endpoint.
def construct_additional_endpoints_from_run_parameters(run_details):

        def extract_endpoint_num_from_run_parameter(run_parameter):
                match = re.search('{}(\d+).*'.format(CP_CAP_CUSTOM_ENDPOINT_PREFIX), run_parameter["name"])
                if match:
                        return match.group(1)
                return None


        custom_endpoint_run_parameters = [rp for rp in run_details["pipelineRunParameters"]
                                          if rp["name"].startswith(CP_CAP_CUSTOM_ENDPOINT_PREFIX)]

        if not custom_endpoint_run_parameters:
                return []

        custom_endpoints_nums = set([CP_CAP_CUSTOM_ENDPOINT_PREFIX + extract_endpoint_num_from_run_parameter(rp)
                                     for rp in custom_endpoint_run_parameters])
        do_log('Detected {} custom endpoints groups: {}.'
               .format(len(custom_endpoint_run_parameters), ", ".join(str(num) for num in custom_endpoints_nums)))

        custom_endpoint_param_groups = {
                id : {
                        rp["name"] : rp["value"]
                        for rp in custom_endpoint_run_parameters if rp["name"].startswith(id)
                } for id in custom_endpoints_nums
        }

        return [
                {
                        "name" : e_id,
                        "endpoint": e.get(e_id + "_PORT"),
                        "friendly_name": e.get(e_id + "_NAME", "pipeline-" + str(run_details['id']) + "-" + e.get(e_id + "_PORT")),
                        "endpoint_additional": e.get(e_id + "_ADDITIONAL", ""),
                        "ssl_backend": e.get(e_id + "_SSL_BACKEND", False),
                        "endpoint_same_tab": e.get(e_id + "_SAME_TAB", False)
                } for e_id, e in custom_endpoint_param_groups.items()
        ]

def match_sys_endpoint_value(param_value, endpoint_value):
        if not param_value or not endpoint_value:
                return False
        if param_value.lower() == endpoint_value.lower():
                return True
        # This way we can set envpoint value to boolean expressions, e.g. ">0"
        if not endpoint_value.isalnum():
                try:
                        return eval(param_value + endpoint_value)
                except:
                        return False
        
        return False

def append_additional_endpoints(tool_endpoints, run_details):
        if not tool_endpoints:
                tool_endpoints = []
        system_endpoints_params = SYSTEM_ENDPOINTS.keys()
        overridden_endpoints_count = 0
        if run_details and "pipelineRunParameters" in run_details:
                # Get a list of endpoints from SYSTEM_ENDPOINTS which match the run's parameters (param name and a value)
                additional_endpoints_to_configure = [SYSTEM_ENDPOINTS[x["name"]] for x in run_details["pipelineRunParameters"]
                                                     if x["name"] in system_endpoints_params
                                                        and match_sys_endpoint_value(x["value"], SYSTEM_ENDPOINTS[x["name"]]["value"])
                                                        and "endpoint" in SYSTEM_ENDPOINTS[x["name"]]
                                                        and SYSTEM_ENDPOINTS[x["name"]]["endpoint"]]
                additional_endpoint_ports_to_configure = set([e["endpoint"] for e in additional_endpoints_to_configure])

                # Filter out any endpoint if it matches with system ones
                for custom_endpoint in construct_additional_endpoints_from_run_parameters(run_details):
                        if custom_endpoint["endpoint"] in additional_endpoint_ports_to_configure:
                                do_log('Endpoint {} with port: {} conflict with already configured ones, it will be filtered out.'
                                       .format(custom_endpoint["name"], custom_endpoint["endpoint"]))
                                continue
                        # Append additional custom endpoint that are configured with run parameters
                        additional_endpoints_to_configure.append(custom_endpoint)
                        additional_endpoint_ports_to_configure.add(custom_endpoint["endpoint"])

                # If only a single endpoint is defined for the tool - we shall make sure it is set to default. Otherwise "system endpoint" may become a default one
                # If more then one endpoint is defined - we shall not make the changes, as it is up to the owner of the tool
                if additional_endpoints_to_configure and len(tool_endpoints) == 1:
                        current_tool_endpoint = json.loads(tool_endpoints[0])
                        current_tool_endpoint["isDefault"] = "true"
                        tool_endpoints[0] = json.dumps(current_tool_endpoint)

                # Append additional endpoints to the existing list
                for additional_endpoint in additional_endpoints_to_configure:
                        tool_endpoint = { "nginx": { "port": additional_endpoint["endpoint"], "additional": additional_endpoint["endpoint_additional"] }}
                        system_endpoint_port = additional_endpoint["endpoint"]
                        system_endpoint_ssl_backend = additional_endpoint["ssl_backend"]
                        system_endpoint_same_tab = additional_endpoint["endpoint_same_tab"]
                        system_endpoint_name = None
                        if "friendly_name" in additional_endpoint:
                                tool_endpoint["name"] = additional_endpoint["friendly_name"]
                                system_endpoint_name = additional_endpoint["friendly_name"]
                        if "endpoint_num" in additional_endpoint and additional_endpoint["endpoint_num"]:
                                tool_endpoint["endpoint_num"] = additional_endpoint["endpoint_num"]
                        non_matching_with_system_tool_endpoints, \
                                is_default_endpoint, \
                                is_ssl_backend, \
                                is_same_tab = \
                                remove_from_tool_endpoints_if_fully_matches(system_endpoint_name,
                                                                            system_endpoint_port, tool_endpoints)
                        removed_endpoints_count = len(tool_endpoints) - len(non_matching_with_system_tool_endpoints)
                        tool_endpoint["isDefault"] = str(is_default_endpoint).lower()
                        tool_endpoint["sslBackend"] = system_endpoint_ssl_backend if system_endpoint_ssl_backend else is_ssl_backend
                        tool_endpoint["sameTab"] = system_endpoint_same_tab if system_endpoint_same_tab else is_same_tab
                        if removed_endpoints_count != 0:
                                tool_endpoints = non_matching_with_system_tool_endpoints
                                overridden_endpoints_count += removed_endpoints_count
                        tool_endpoints.append(json.dumps(tool_endpoint))
        return tool_endpoints, overridden_endpoints_count

def remove_from_tool_endpoints_if_fully_matches(endpoint_name, endpoint_port, tool_endpoints):
        non_matching_tool_endpoints = []
        is_default_endpoint = False
        is_ssl_backend = False
        is_same_tab = False
        for endpoint in tool_endpoints:
                tool_endpoint_obj = json.loads(endpoint)
                if tool_endpoint_obj \
                        and endpoint_name \
                        and 'name' in tool_endpoint_obj \
                        and tool_endpoint_obj['name'] \
                        and tool_endpoint_obj['name'].lower() == endpoint_name.lower() \
                        and 'nginx' in tool_endpoint_obj \
                        and tool_endpoint_obj['nginx'] \
                        and 'port' in tool_endpoint_obj['nginx'] \
                        and tool_endpoint_obj['nginx']['port'] == endpoint_port:
                        if 'isDefault' in tool_endpoint_obj and tool_endpoint_obj['isDefault']:
                                is_default_endpoint = is_default_endpoint | tool_endpoint_obj['isDefault']
                        if 'sslBackend' in tool_endpoint_obj and tool_endpoint_obj['sslBackend']:
                                is_ssl_backend = is_ssl_backend | tool_endpoint_obj['sslBackend']
                        if 'sameTab' in tool_endpoint_obj and tool_endpoint_obj['sameTab']:
                                is_same_tab = is_same_tab | tool_endpoint_obj['sameTab']
                else:
                        non_matching_tool_endpoints.append(endpoint)
        return non_matching_tool_endpoints, is_default_endpoint, is_ssl_backend, is_same_tab

def get_active_runs(pods):
        if not pods:
                return []
        pod_run_ids = [x['metadata']['labels']['runid'] for x in pods]
        get_runs_list_details_method = os.path.join(api_url,
                                                    API_GET_RUNS_LIST_DETAILS.format(run_ids=','.join(pod_run_ids)))
        response_data = call_api(get_runs_list_details_method)
        if not response_data or 'payload' not in response_data:
                do_log('Cannot get list of active runs from the API for the following IDs: {}'.format(pod_run_ids))
                return []

        return response_data["payload"]


def get_service_list(active_runs_list, pod_id, pod_run_id, pod_ip):
        service_list = {}
        run_cache = [cached_run for cached_run in active_runs_list if str(cached_run['pipelineRun']['id']) == str(pod_run_id)]
        run_cache = next(iter(run_cache), None)
        if not run_cache:
                do_log('Cannot find the RunID {} in the list of cached runs, skipping'.format(pod_run_id))
                return {}

        run_info = run_cache['pipelineRun']
        if run_info:
                if run_info.get("status") != 'RUNNING':
                        do_log('Status for pipeline with id: {}, is not RUNNING. Service urls will not been proxied'.format(pod_run_id))
                        return {}

                pod_owner = run_info["owner"]
                docker_image = run_info["dockerImage"]
                runs_sids = run_info.get("runSids")
                pretty_url = run_info.get("prettyUrl")
                pretty_url = parse_pretty_url(pretty_url) if pretty_url else None
                sensitive = run_info.get("sensitive") or False

                cloud_region_id = run_info.get("instance", {}).get("cloudRegionId") or None
                instance_ip = run_info.get("instance", {}).get("nodeIP") or None

                do_log('Processing {} #{} by {} ({})...'.format(pod_id, pod_run_id, pod_owner, docker_image))

                shared_users_sids = run_sids_to_str(runs_sids, True)
                if shared_users_sids:
                        do_log('Detected shared user sids: {}'.format(shared_users_sids))

                shared_groups_sids = run_sids_to_str(runs_sids, False)
                if shared_groups_sids:
                        do_log('Detected shared group sids: {}'.format(shared_groups_sids))

                endpoints_data = run_cache.get('tool', {}).get('endpoints') or []
                tool_endpoints_count = len(endpoints_data)
                do_log('Detected {} tool settings endpoints.'.format(tool_endpoints_count))

                endpoints_data, overridden_endpoints_count = append_additional_endpoints(endpoints_data, run_info)
                additional_system_endpoints_count = len(endpoints_data) - tool_endpoints_count
                do_log('Detected {} run parameters endpoints.'.format(additional_system_endpoints_count))
                if overridden_endpoints_count:
                        do_log('Detected {} overridden tool settings endpoints.'.format(overridden_endpoints_count))

                if endpoints_data:
                        endpoints_count = len(endpoints_data)
                        for i in range(endpoints_count):
                                endpoint = json.loads(endpoints_data[i])
                                if endpoint["nginx"]:
                                        port = endpoint["nginx"]["port"]
                                        path = endpoint["nginx"].get("path", "")
                                        service_name = endpoint.get("name", "Default")
                                        is_default_endpoint = endpoint.get("isDefault", False)
                                        is_ssl_backend = endpoint.get("sslBackend", False)
                                        is_same_tab = endpoint.get("sameTab", False)
                                        create_dns_record = endpoint.get("customDNS", False)
                                        additional = endpoint["nginx"].get("additional", "")
                                        has_explicit_endpoint_num = "endpoint_num" in endpoint.keys()
                                        custom_endpoint_num = int(endpoint["endpoint_num"]) if has_explicit_endpoint_num else i
                                        edge_location_id = ROUTE_ID_TMPL.format(pod_id=pod_id, endpoint_port=port, endpoint_num=custom_endpoint_num)
                                        if not pretty_url or (has_explicit_endpoint_num and not is_system_endpoint_name(endpoint)):
                                                edge_location = edge_location_id
                                        else:
                                                pretty_url_path = pretty_url["path"]
                                                if endpoints_count == 1:
                                                        edge_location = pretty_url_path
                                                else:
                                                        pretty_url_suffix = endpoint["name"] if "name" in endpoint.keys() else str(custom_endpoint_num)
                                                        if pretty_url_path:
                                                                edge_location = '{}-{}'.format(pretty_url_path, pretty_url_suffix)
                                                        else:
                                                                edge_location = pretty_url_suffix


                                        if (pretty_url and pretty_url['domain']) or create_dns_record:
                                                edge_location_path = edge_location_id + '.inc'
                                        else:
                                                edge_location_path = edge_location_id + '.loc'

                                        if EDGE_INSTANCE_IP in additional:
                                                additional = additional.replace(EDGE_INSTANCE_IP, "")
                                                target_ip = instance_ip
                                        else:
                                                target_ip = pod_ip

                                        edge_target = \
                                                EDGE_ROUTE_TARGET_PATH_TMPL.format(pod_ip=target_ip, endpoint_port=port, endpoint_path=path) \
                                                        if path \
                                                        else EDGE_ROUTE_TARGET_TMPL.format(pod_ip=target_ip, endpoint_port=port)

                                        # If CP_EDGE_NO_PATH_CROP is present (any place) in the "additional" section of the route config
                                        # then trailing "/" is not added to the proxy pass target. This will allow to forward original requests trailing path
                                        if EDGE_ROUTE_NO_PATH_CROP in additional:
                                                additional = additional.replace(EDGE_ROUTE_NO_PATH_CROP, "")
                                        else:
                                                edge_target = edge_target + "/"

                                        is_external_app = False
                                        if EDGE_EXTERNAL_APP in additional:
                                                additional = additional.replace(EDGE_EXTERNAL_APP, "")
                                                is_external_app = True

                                        for default_attribute in DEFAULT_LOCATION_ATTRIBUTES:
                                                if 'search_pattern' not in default_attribute or 'value' not in default_attribute:
                                                        continue
                                                if default_attribute['search_pattern'].lower() not in additional.lower():
                                                        additional = additional + default_attribute['value']

                                        service_list[edge_location_id] = {
                                                "edge_location_path": edge_location_path,
                                                "pod_id": pod_id,
                                                "pod_ip": target_ip,
                                                "pod_owner": pod_owner,
                                                "shared_users_sids": shared_users_sids,
                                                "shared_groups_sids": shared_groups_sids,
                                                "service_name": service_name,
                                                "is_default_endpoint": is_default_endpoint,
                                                "is_ssl_backend": is_ssl_backend,
                                                "is_same_tab": is_same_tab,
                                                "edge_num": i,
                                                "edge_location": edge_location,
                                                "custom_domain": pretty_url.get('domain') if pretty_url else None,
                                                "edge_target": edge_target,
                                                "run_id": pod_run_id,
                                                "additional": additional,
                                                "sensitive": sensitive,
                                                "create_dns_record": create_dns_record,
                                                "cloudRegionId": cloud_region_id,
                                                "external_app": is_external_app
                                        }
                else:
                        do_log('No endpoints required for the tool {}'.format(docker_image))
        else:
                do_log('Unable to get details of a RunID {} from API due to errors'.format(pod_run_id))
        return service_list


# From each pod with a container, which has endpoints ("job-type=Service" or container's environment
# has a parameter from SYSTEM_ENDPOINTS) we shall take:
# -- PodIP
# -- PodID
# -- N entries by a template
# --- svc-port-N
# --- svc-path-N

def load_pods_for_runs_with_endpoints():
        pods_with_endpoints = []
        all_pipeline_pods = Pod.objects(kube_api).filter(selector={'type': 'pipeline'})\
                                                 .filter(field_selector={"status.phase": "Running"})
        for pod in all_pipeline_pods.response['items']:
                labels = pod['metadata']['labels']
                if 'job-type' in labels and labels['job-type'] == 'Service':
                        pods_with_endpoints.append(pod)
                        continue
                if 'spec' in pod \
                        and pod['spec'] \
                        and 'containers' in pod['spec'] \
                        and pod['spec']['containers'] \
                        and 'env' in pod['spec']['containers'][0] \
                        and pod['spec']['containers'][0]['env']:
                    pipeline_env_parameters = pod['spec']['containers'][0]['env']
                    matched_sys_endpoints = list(filter(lambda env_var: env_var['name'] in SYSTEM_ENDPOINTS.keys()
                                                                        and match_sys_endpoint_value(env_var['value'], SYSTEM_ENDPOINTS[env_var["name"]]["value"]),
                                                        pipeline_env_parameters))
                    if matched_sys_endpoints:
                                pods_with_endpoints.append(pod)
        return pods_with_endpoints


def create_dns_record(service_spec, edge_region_id, edge_region_name):
        run_logger = RunLogger(run_id=service_spec["run_id"], task_name='CreateDNSRecord')

        dns_custom_record = EDGE_DNS_RECORD_FORMAT.format(job_name=service_spec["edge_location"],
                                                          region_name=edge_region_name)
        dns_record_create = os.path.join(api_url, API_POST_DNS_RECORD)
        if edge_region_id:
                dns_record_create += "?regionId=" + edge_region_id
        data = json.dumps({
                'dnsRecord': dns_custom_record,
                'target': edge_service_external_ip,
                'format': 'RELATIVE'
        })

        run_logger.info('Creating DNS record {}...'.format(dns_custom_record))
        dns_record_create_response = call_api(dns_record_create, data) or {}
        dns_record_create_response_payload = dns_record_create_response.get('payload', {})
        dns_record_status = dns_record_create_response_payload.get('status')
        dns_record_domain = dns_record_create_response_payload.get('dnsRecord')

        if dns_record_status != 'INSYNC':
                run_logger.warning('Failed to create DNS record {}'.format(dns_custom_record))
                raise ValueError('Fail to create DNS record {} for run #{}'
                                 .format(dns_custom_record, service_spec["run_id"]))
        run_logger.success('Created DNS record {}'.format(dns_record_domain))

        service_spec["custom_domain"] = dns_record_domain
        service_spec["edge_location"] = None


def create_service_dns_record(service_spec, route, edge_region_id, edge_region_name):
        try:
                do_log('Creating DNS record for {}'.format(route))
                create_dns_record(service_spec, edge_region_id, edge_region_name)
                do_log("Creating DNS record for {} ... OK ".format(route))
                return route
        except ValueError as e:
                do_log("Creating DNS record for {} ... NOT OK ({})".format(route, str(e)))
                return None

def create_service_location(service_spec, service_url_dict, edge_region_id):
        has_custom_domain = service_spec["custom_domain"] is not None
        service_hostname = service_spec["custom_domain"] if has_custom_domain else edge_service_external_ip
        service_location = '/{}/'.format(service_spec["edge_location"]) if service_spec["edge_location"] else "/"
        # Replace the duplicated forward slashes with a single instance to workaround possible issue when the location is set to "/path//"
        service_location = re.sub('/+', '/', service_location)

        nginx_route_definition = nginx_loc_module_template_contents \
                .replace('{edge_route_location}', service_location) \
                .replace('{edge_route_target}', service_spec["edge_target"]) \
                .replace('{edge_route_owner}', service_spec["pod_owner"]) \
                .replace('{run_id}', service_spec["run_id"]) \
                .replace('{edge_route_shared_users}', service_spec["shared_users_sids"]) \
                .replace('{edge_route_shared_groups}', service_spec["shared_groups_sids"]) \
                .replace('{edge_route_schema}', 'https' if service_spec["is_ssl_backend"] else 'http') \
                .replace('{additional}', service_spec["additional"])
        nginx_sensitive_route_definitions = []
        if service_spec["sensitive"]:
                for sensitive_route in sensitive_routes:
                        # proxy_pass cannot have trailing slash for regexp locations
                        edge_target = service_spec["edge_target"]
                        if edge_target.endswith("/"):
                                edge_target = edge_target[:-1]
                        nginx_sensitive_route_definition = nginx_sensitive_loc_module_template_contents \
                                .replace('{edge_route_location}', service_location + sensitive_route['route']) \
                                .replace('{edge_route_sensitive_methods}', '|'.join(sensitive_route['methods'])) \
                                .replace('{edge_route_target}', edge_target) \
                                .replace('{edge_route_owner}', service_spec["pod_owner"]) \
                                .replace('{run_id}', service_spec["run_id"]) \
                                .replace('{edge_route_shared_users}', service_spec["shared_users_sids"]) \
                                .replace('{edge_route_shared_groups}', service_spec["shared_groups_sids"]) \
                                .replace('{additional}', service_spec["additional"])
                        nginx_sensitive_route_definitions.append(nginx_sensitive_route_definition)
        path_to_route = os.path.join(nginx_sites_path, service_spec.get('edge_location_path') + '.conf')
        if service_spec["sensitive"]:
                do_log('Adding new sensitive route ' + path_to_route)
        else:
                do_log('Adding new route ' + path_to_route)
        with open(path_to_route, "w") as added_route_file:
                added_route_file.write(nginx_route_definition)
                if nginx_sensitive_route_definitions:
                        for nginx_sensitive_route_definition in nginx_sensitive_route_definitions:
                                added_route_file.write(nginx_sensitive_route_definition)

        if has_custom_domain:
                do_log('Adding new route {} to server block {}'.format(path_to_route, service_hostname))
                add_custom_domain(service_hostname, path_to_route, is_external_app=service_spec['external_app'])

        check_route(path_to_route, service_location, service_spec, has_custom_domain, service_hostname)

        service_url = SVC_URL_TMPL.format(external_schema=edge_service_external_schema,
                                          external_ip=service_hostname,
                                          edge_location=service_spec.get('edge_location') or '',
                                          edge_port=str(edge_service_port),
                                          service_name=service_spec['service_name'],
                                          is_default_endpoint=str(service_spec['is_default_endpoint']).lower(),
                                          is_same_tab=str(service_spec['is_same_tab']).lower(),
                                          is_custom_dns=str(service_spec['create_dns_record']).lower(),
                                          region_id=edge_region_id or 'null')
        run_id = service_spec['run_id']
        if run_id in service_url_dict:
                service_url = service_url_dict[run_id] + ',\n' + service_url
        service_url_dict[run_id] = service_url


def update_svc_url_for_run(run_id, edge_region_name):
        service_url = service_url_dict.get(run_id)
        if not service_url:
                do_log('Assigning #{} with service url has been skipped '
                       'because the corresponding service url has not been found.'.format(run_id))
                return
        do_log('Assigning #{} with service url \n{}'.format(run_id, service_url))
        update_svc_method = os.path.join(api_url, API_UPDATE_SVC.format(run_id=run_id, region=edge_region_name))
        data = json.dumps({'serviceUrl': ('[' + service_url + ']')})
        response_data = call_api(update_svc_method, data=data)
        if response_data:
                do_log('Assigning #{} with service url ... OK'.format(run_id))
        else:
                do_log('Assigning #{} with service url ... NOT OK.'.format(run_id))


def find_preference(api_preference_query, preference_name):
        load_method = os.path.join(api_url, api_preference_query.format(preference_name=preference_name))
        response = call_api(load_method) or {}
        return str(response.get('payload', {}).get('value'))


def write_stub_location_configuration(path_to_route, service_location, service_spec, has_custom_domain):
        route_location = service_location if service_location == '/' else service_location[:-1]
        nginx_route_definition = nginx_loc_module_stub_template_contents \
                .replace('{edge_route_location}', route_location) \
                .replace('{edge_route_owner}', service_spec["pod_owner"]) \
                .replace('{edge_route_shared_users}', service_spec["shared_users_sids"]) \
                .replace('{edge_route_shared_groups}', service_spec["shared_groups_sids"])

        path_to_route_extension = ".conf" if has_custom_domain else ".loc.conf"
        stub_extension = STUB_CUSTOM_DOMAIN_EXTENSION if has_custom_domain else STUB_LOCATION_CONFIG_EXTENSION

        path_to_stub = path_to_route.replace(path_to_route_extension, stub_extension)
        with open(path_to_stub, "w") as stub_file:
                stub_file.write(nginx_route_definition)
        do_log('Adding new stub route ' + path_to_stub)
        return path_to_stub


def reload_nginx_config():
        do_log('Reloading nginx...')
        subprocess.check_output('nginx -s reload', shell=True)


def check_nginx_config():
        test_config_command = 'nginx -c %s -t' % nginx_root_config_path
        try:
                subprocess.check_output(test_config_command, shell=True)
                do_log('Adding new route ... OK')
                return True
        except subprocess.CalledProcessError as e:
                do_log('Adding new route ... NOT OK (%s)' % e.returncode)
                return False


def check_route(path_to_route, service_location, service_spec, has_custom_domain, service_hostname):
        if check_nginx_config():
                return

        do_log('Deleting invalid route...')
        os.remove(path_to_route)
        if has_custom_domain:
                do_log('Deleting invalid custom domain route...')
                remove_custom_domain_all(path_to_route)

        path_to_stub = write_stub_location_configuration(path_to_route,
                                                         service_location,
                                                         service_spec,
                                                         has_custom_domain)

        if check_nginx_config():
                if has_custom_domain:
                        do_log('Adding new stub route {} to server block {}'.format(path_to_stub, service_hostname))
                        add_custom_domain(service_hostname, path_to_stub, is_external_app=service_spec['external_app'])
                return

        do_log('Deleting invalid stub route...')
        os.remove(path_to_stub)

def get_pods(routes):
        for route in routes:
                match = re.match(ROUTE_ID_PATTERN, route)
                if not match:
                        do_log('Detected inccorrectly formatted route {}'.format(route))
                        continue
                pod_id = match.group(1)
                if not pod_id:
                        do_log('Detected inccorrectly formatted route {}'.format(route))
                        continue
                yield pod_id

def get_pod(route):
        pods = set(get_pods([route]))
        return pods.pop() if pods else None


def get_affected_routes(involved_routes, all_routes):
        involved_pods = set(get_pods(involved_routes))
        return set(route for route in all_routes if get_pod(route) in involved_pods)


do_log('============ Started iteration ============')

kube_api = HTTPClient(KubeConfig.from_service_account())
kube_api.session.verify = False

edge_region_name = os.getenv('CP_EDGE_REGION') or find_preference(API_GET_PREF, 'default.edge.region')
edge_region_id = os.getenv('CP_EDGE_REGION_ID') or find_preference(API_GET_PREF, 'default.edge.region.id')

# Try to get edge_service_external_ip and edge_service_port for service labels several times before get it from
# service spec IP and nodePort because it is possible that we will do it while redeploy and label just doesn't
# applied yet - so we will wait
edge_kube_service_object = None
for n in range(NUMBER_OF_RETRIES):
    edge_kube_service = Service.objects(kube_api).filter(selector={
            EDGE_SVC_ROLE_LABEL: EDGE_SVC_ROLE_LABEL_VALUE, EDGE_SVC_REGION_LABEL: edge_region_name})
    if not edge_kube_service.response['items']:
        do_log('EDGE service is not found by labels: cloud-pipeline/role=EDGE and %s=%s'
               % (EDGE_SVC_REGION_LABEL, edge_region_name))
        exit(1)
    else:
        edge_kube_service_object = edge_kube_service.response['items'][0]
        edge_kube_service_object_metadata = edge_kube_service_object['metadata']

        if 'labels' in edge_kube_service_object_metadata and EDGE_SVC_HOST_LABEL in edge_kube_service_object_metadata['labels']:
            do_log('Getting EDGE service host from service label')
            edge_service_external_ip = edge_kube_service_object_metadata['labels'][EDGE_SVC_HOST_LABEL]

        if 'labels' in edge_kube_service_object_metadata and EDGE_SVC_PORT_LABEL in edge_kube_service_object_metadata['labels']:
            do_log('Getting EDGE service host port from service label')
            edge_service_port = edge_kube_service_object_metadata['labels'][EDGE_SVC_PORT_LABEL]

        if edge_service_external_ip and edge_service_port:
            break
        else:
            do_log('Sleep for {} sec and perform kube API call again ({}/{})'.format(SECS_TO_WAIT_BEFORE_RETRY, n + 1, NUMBER_OF_RETRIES))
            time.sleep(SECS_TO_WAIT_BEFORE_RETRY)

if not edge_kube_service_object:
    do_log('EDGE service is not found by labels: cloud-pipeline/role=EDGE and %s=%s'
           % (EDGE_SVC_REGION_LABEL, edge_region_name))
    exit(1)

if not edge_service_external_ip:
    do_log('Getting EDGE service host from externalIP')
    edge_service_external_ip = edge_kube_service_object['spec']['externalIPs'][0]
if not edge_service_port:
    do_log('Getting EDGE service host port from nodePort')
    edge_service_port = edge_kube_service_object['ports'][0]['nodePort']
do_log('EDGE: {}:{} ({} #{})'.format(edge_service_external_ip, edge_service_port,
                                     edge_region_name, edge_region_id or 'undefined'))

pods_with_endpoints = load_pods_for_runs_with_endpoints()
runs_with_endpoints = get_active_runs(pods_with_endpoints)

services_list = {}
for pod_spec in pods_with_endpoints:
        pod_id = pod_spec['metadata']['name']
        pod_ip = pod_spec['status']['podIP']
        pod_run_id = pod_spec['metadata']['labels']['runid']

        if not pod_run_id:
                do_log('RunID not found for pod: ' + pod_id + ', skipping')
                continue

        services_list.update(get_service_list(runs_with_endpoints, pod_id, pod_run_id, pod_ip))

routes_expected = set(services_list.keys())
do_log('Found {} expected routes'.format(len(routes_expected)))

# Find out existing routes from /etc/nginx/sites-enabled
nginx_modules_list = {}
for x in os.listdir(nginx_sites_path):
        location_config_path = os.path.join(nginx_sites_path, x)
        if '.conf' in x and os.path.isfile(location_config_path):
                if location_config_path.endswith(STUB_LOCATION_CONFIG_EXTENSION):
                        do_log('Deleting stub route ' + location_config_path)
                        os.remove(location_config_path)
                        continue
                if location_config_path.endswith(STUB_CUSTOM_DOMAIN_EXTENSION):
                        do_log('Deleting custom domain stub route ' + location_config_path)
                        os.remove(location_config_path)
                        remove_custom_domain_all(location_config_path)
                        continue
                nginx_modules_list[x.replace('.loc.conf', '').replace('.inc.conf', '')] = x

routes_actual = set(nginx_modules_list.keys())
do_log('Found {} actual routes'.format(len(routes_actual)))

routes_to_check = routes_actual & routes_expected
routes_to_add = routes_expected - routes_actual
routes_to_delete = routes_actual - routes_expected
do_log('Found {} existing routes, these routes will be checked'.format(len(routes_to_check)))
do_log('Found {} missing routes, these routes will be created'.format(len(routes_to_add)))
do_log('Found {} expired routes, these routes will be deleted'.format(len(routes_to_delete)))

# All routes that exist in both Nginx and API are checked, whether the routes shall be updated or kept untouched.
# If some routes differ then they are deleted and created from scratch.
# Currently only modified sharing users/groups are checked.
routes_to_update = set()
for route in routes_to_check:
        path_to_route = os.path.join(nginx_sites_path, nginx_modules_list[route])

        do_log('Checking route {}'.format(path_to_route))
        with open(path_to_route) as route_file:
                route_file_contents = route_file.read()

        shared_users_sids_to_check = ""
        shared_groups_sids_to_check = ""
        for route_search_results in re.finditer(r"shared_with_users\s{1,}\"(.+?)\";"
                                                r"|shared_with_groups\s{1,}\"(.+?)\";",
                                                route_file_contents):
                g1 = route_search_results.group(1)
                g2 = route_search_results.group(2)
                shared_users_sids_to_check = g1 if g1 else shared_users_sids_to_check
                shared_groups_sids_to_check = g2 if g2 else shared_groups_sids_to_check

        service_spec = services_list[route]
        shared_users_sids_to_update = service_spec["shared_users_sids"]
        shared_groups_sids_to_update = service_spec["shared_groups_sids"]

        if shared_users_sids_to_check != shared_users_sids_to_update:
                do_log('Detected different shared users. Actual: "{}". Expected: "{}"'
                       .format(shared_users_sids_to_check, shared_users_sids_to_update))
                routes_to_update.add(route)
        elif shared_groups_sids_to_check != shared_groups_sids_to_update:
                do_log('Detected different shared groups. Actual: "{}". Expected: "{}"'
                       .format(shared_groups_sids_to_check, shared_groups_sids_to_update))
                routes_to_update.add(route)

do_log('Found {} changed routes, these routes will be replaced'.format(len(routes_to_update)))

# If a single route of a pod is added/deleted/updated then all other routes of the same pod are replaced.
# Otherwise, the generated service url will have missing/extra endpoints.
routes_to_replace = get_affected_routes(routes_to_add | routes_to_delete | routes_to_update, routes_to_check)
routes_to_affect = routes_to_replace - routes_to_update
do_log('Found {} affected routes, these routes will be replaced'.format(len(routes_to_affect)))

routes_to_add |= routes_to_replace
routes_to_delete |= routes_to_replace

do_log("Deleting {} routes...".format(len(routes_to_delete)))
for route in routes_to_delete:
        path_to_route = os.path.join(nginx_sites_path, nginx_modules_list[route])
        do_log('Deleting route {}'.format(path_to_route))
        os.remove(path_to_route)
        remove_custom_domain_all(path_to_route)

with open(nginx_loc_module_template, 'r') as nginx_loc_module_template_file:
    nginx_loc_module_template_contents = nginx_loc_module_template_file.read()

with open(nginx_sensitive_loc_module_template, 'r') as nginx_sensitive_loc_module_template_file:
    nginx_sensitive_loc_module_template_contents = nginx_sensitive_loc_module_template_file.read()

with open(nginx_sensitive_routes_config_path, 'r') as sensitive_routes_file:
    sensitive_routes = json.load(sensitive_routes_file)

with open(nginx_loc_module_stub_template, 'r') as stub_template_file:
    nginx_loc_module_stub_template_contents = stub_template_file.read()


# loop through all routes that we need to create, if this route doesn't have option to create custom DNS record
# we handle it in the main thread, if custom DNS record should be created, since it consume some time ~ 20 sec,
# we put it to the separate collection to handle it at the end.
regular_routes_to_add, dns_routes_to_configure = [], []
for route in routes_to_add:
        service_spec = services_list[route]
        if service_spec["create_dns_record"] and not service_spec["custom_domain"]:
                dns_routes_to_configure.append(route)
        else:
                regular_routes_to_add.append(route)

service_url_dict = {}

do_log("Creating {} routes for regular endpoints...".format(len(regular_routes_to_add)))
for route in regular_routes_to_add:
        service_spec = services_list[route]
        create_service_location(service_spec, service_url_dict, edge_region_id)

dns_route_runs = set()
dns_route_results = []
do_log("Creating {} configurations for dns endpoints...".format(len(dns_routes_to_configure)))
for route in dns_routes_to_configure:
        service_spec = services_list[route]
        dns_route_runs.add(service_spec["run_id"])
        dns_route_results.append(dns_services_pool.apply_async(
                create_service_dns_record,
                (service_spec, route, edge_region_id, edge_region_name)))

if regular_routes_to_add or routes_to_delete:
        reload_nginx_config()

for run_id in service_url_dict:
        if run_id not in dns_route_runs:
                update_svc_url_for_run(run_id, edge_region_name)

dns_services_pool.close()
dns_services_pool.join()
dns_routes_to_add = set(result.get() for result in dns_route_results if result.get())

do_log("Creating {} routes for dns endpoints...".format(len(dns_routes_to_add)))
for route in dns_routes_to_add:
        service_spec = services_list[route]
        create_service_location(service_spec, service_url_dict, edge_region_id)

if dns_routes_to_add:
        reload_nginx_config()

for run_id in service_url_dict:
        if run_id in dns_route_runs:
                update_svc_url_for_run(run_id, edge_region_name)

do_log('============ Done iteration ============')
do_log('')
