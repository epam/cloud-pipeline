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

import os
import json
import glob
import re
import requests
from subprocess import check_output
import urllib3
from time import sleep

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
SVC_URL_TMPL = '{{"url" : "{external_schema}://{external_ip}:{edge_port}/{edge_location}", "name": {service_name}, "isDefault": {is_default_endpoint} }}'
EDGE_ROUTE_LOCATION_TMPL = '{pod_id}-{endpoint_port}-{endpoint_num}'
EDGE_ROUTE_TARGET_TMPL = '{pod_ip}:{endpoint_port}'
EDGE_ROUTE_TARGET_PATH_TMPL = '{pod_ip}:{endpoint_port}/{endpoint_path}'
EDGE_ROUTE_NO_PATH_CROP = 'CP_EDGE_NO_PATH_CROP'
RUN_ID = 'runid'
API_UPDATE_SVC = 'run/{run_id}/serviceUrl'
API_GET_RUN_DETAILS = 'run/{run_id}'
API_GET_TOOL = 'tool/load?registry={registry}&image={image}'
NUMBER_OF_RETRIES = 10
SECS_TO_WAIT_BEFORE_RETRY = 15

EDGE_SVC_ROLE_LABEL = 'cloud-pipeline/role'
EDGE_SVC_ROLE_LABEL_VALUE = 'EDGE'
EDGE_SVC_HOST_LABEL = 'cloud-pipeline/external-host'
EDGE_SVC_PORT_LABEL = 'cloud-pipeline/external-port'

nginx_custom_domain_config_ext = '.srv.conf'
nginx_custom_domain_loc_suffix = 'CP_EDGE_CUSTOM_DOMAIN'
nginx_custom_domain_loc_tmpl = 'include {}; # ' + nginx_custom_domain_loc_suffix
nginx_root_config_path = '/etc/nginx/nginx.conf'
nginx_sites_path = '/etc/nginx/sites-enabled'
nginx_domains_path = '/etc/nginx/sites-enabled/custom-domains'
nginx_loc_module_template = '/etc/nginx/endpoints-config/route.template.loc.conf'
nginx_srv_module_template = '/etc/nginx/endpoints-config/route.template' + nginx_custom_domain_config_ext
edge_service_port = 31000
edge_service_external_ip = ''
pki_search_path = '/opt/edge/pki/'
pki_search_suffix_cert = '-public-cert.pem'
pki_search_suffix_key = '-private-key.pem'
pki_default_cert = '/opt/edge/pki/ssl-public-cert.pem'
pki_default_cert_key = '/opt/edge/pki/ssl-private-key.pem'

urllib3.disable_warnings()
api_url = os.environ.get('API')
api_token = os.environ.get('API_TOKEN')
if not api_url or not api_token:
        print('API url or API token are not set. Exiting')
        exit(1)
edge_service_external_schema=os.environ.get('EDGE_EXTERNAL_SCHEMA', 'https')


api_headers = {'Content-Type': 'application/json',
               'Authorization': 'Bearer {}'.format(api_token)}

class ServiceEndpoint:
        def __init__(self, num, port, path, additional):
                self.num = num
                self.port = port
                self.path = path
                self.additional = additional

def call_api(method_url, data=None):
        result = None
        for n in range(NUMBER_OF_RETRIES):
                try:
                        print('Calling API {}'.format(method_url))
                        response = None
                        if data:
                                response = requests.post(method_url, verify=False, data=data, headers=api_headers)
                        else:
                                response = requests.get(method_url, verify=False, headers=api_headers)
                        response_data = json.loads(response.text)
                        if response_data['status'] == 'OK':
                                print('API call status OK')
                                result = response_data
                                break
                        else:
                                err_msg = 'No error message available'
                                if 'message' in response_data:
                                        err_msg = response_data['message']
                                print('Error ocurred while calling API ({})\n{}'.format(method_url, err_msg))
                except Exception as api_exception:
                        print('Error ocurred while calling API ({})\n{}'.format(method_url, str(api_exception)))

                if n < NUMBER_OF_RETRIES - 1:
                        print('Sleep for {} sec and perform API call again ({}/{})'.format(SECS_TO_WAIT_BEFORE_RETRY, n + 2, NUMBER_OF_RETRIES))
                        sleep(SECS_TO_WAIT_BEFORE_RETRY)
                else:
                        print('All attempts failed. API call failed')
        return result


def run_sids_to_str(run_sids, is_principal):
        if not run_sids or len(run_sids) == 0:
                return ""
        return ",".join([shared_sid["name"] for shared_sid in run_sids if shared_sid["isPrincipal"] == is_principal])

def parse_pretty_url(pretty):
        try:
                pretty_obj = json.loads(pretty)
                if not pretty_obj:
                        return None
        except:
                pretty_obj = { 'path': pretty }
        
        pretty_domain = None
        pretty_path = None
        if 'domain' in pretty_obj:
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

def get_domain_config_path(domain):
        return os.path.join(nginx_domains_path, domain + nginx_custom_domain_config_ext)

def add_custom_domain(domain, location_block):
        if not os.path.isdir(nginx_domains_path):
                os.mkdir(nginx_domains_path)
        domain_path = get_domain_config_path(domain)
        domain_cert = search_custom_domain_cert(domain)
        domain_path_contents = None
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
        if len(existing_loc) != 0:
                print('Location block {} already exists for domain {}'.format(location_block, domain))
                return

        # If it's a new location entry - add it to the domain config after the {edge_route_location_block} line
        insert_loc = substr_indices(domain_path_lines, '# {edge_route_location_block}')
        if len(insert_loc) == 0:
                print('Cannot find an insert location in the domain config {}'.format(domain_path))
                return
        domain_path_lines.insert(insert_loc[-1] + 1, location_block_include)

        # Save the domain config back to file
        store_file_from_lines(domain_path_lines, domain_path)


def remove_custom_domain(domain, location_block):
        location_block_include = nginx_custom_domain_loc_tmpl.format(location_block)
        domain_path = get_domain_config_path(domain)
        if not os.path.exists(domain_path):
                return False
        domain_path_lines = []
        with open(domain_path, 'r') as domain_path_file:
                domain_path_contents = domain_path_file.read()
                domain_path_lines = domain_path_contents.splitlines()

        existing_loc = substr_indices(domain_path_lines, location_block_include)
        if len(existing_loc) == 0:
                return False
        del domain_path_lines[existing_loc[-1]]

        if (sum(nginx_custom_domain_loc_suffix in line for line in domain_path_lines) == 0):
                # If no more location block exist in the domain - delete the config file
                print('No more location blocks are available for {}, deleting the config file: {}'.format(domain, domain_path))
                os.remove(domain_path)
        else:
                # Save the domain config back to file
                store_file_from_lines(domain_path_lines, domain_path)
        return True

def remove_custom_domain_all(location_block):
        domain_path_list = [f for f in glob.glob(nginx_domains_path + '/*' + nginx_custom_domain_config_ext)]
        for domain_path in domain_path_list:
                custom_domain = os.path.basename(domain_path).replace(nginx_custom_domain_config_ext, '')
                if remove_custom_domain(custom_domain, location_block):
                        print('Removed {} location block from {} domain config'.format(location_block, custom_domain))

def search_custom_domain_cert(domain):
        domain_cert_list = [f for f in glob.glob(pki_search_path + '/*' + pki_search_suffix_cert)]
        domain_cert_candidates = []
        for cert_path in domain_cert_list:
                cert_name = os.path.basename(cert_path).replace(pki_search_suffix_cert, '')
                if domain.endswith(cert_name):
                        domain_cert_candidates.append(cert_name)

        cert_path = None
        key_path = None
        if len(domain_cert_candidates) > 0:
                domain_cert_candidates.sort(key=len, reverse=True)
                cert_name = domain_cert_candidates[0]
                cert_path = os.path.join(pki_search_path, cert_name + pki_search_suffix_cert)
                key_path = os.path.join(pki_search_path, cert_name + pki_search_suffix_key)
                if not os.path.isfile(key_path):
                        print('Certificate for {} is found at {}, but a key does not exist at {}'.format(domain, cert_path, key_path))
                        key_path = None
        if not cert_path or not key_path:
                cert_path = pki_default_cert
                key_path = pki_default_cert_key
        
        print('Certificate:Key for {} will be used: {}:{}'.format(domain, cert_path, key_path))
        return (cert_path, key_path)

# FIXME: once we'll get more than one "system endpoint" - a list of such endpoints shall be moved to the configuration
SYSTEM_ENDPOINTS={ "CP_CAP_SPARK": { 
                        "value": "true", 
                        "endpoint": str(os.environ.get("CP_CAP_SPARK_UI_PROXY_PORT", "8088")), 
                        "endpoint_num":  str(os.environ.get("CP_CAP_SPARK_UI_PROXY_ENDPOINT_ID", "1000")),
                        "friendly_name": "SparkUI" }}
def append_system_endpoints(tool_endpoints, run_details):
        if not tool_endpoints:
                tool_endpoints = []
        system_endpoints_params = SYSTEM_ENDPOINTS.keys()
        if run_details and "pipelineRunParameters" in run_details:
                # Get a list of endpoints from SYSTEM_ENDPOINTS which match the run's parameters (param name and a value)
                system_endpoints_matched = [SYSTEM_ENDPOINTS[x["name"]] for x in run_details["pipelineRunParameters"] 
                                                if x["name"] in system_endpoints_params 
                                                   and x["value"] == SYSTEM_ENDPOINTS[x["name"]]["value"]
                                                   and "endpoint" in SYSTEM_ENDPOINTS[x["name"]]
                                                   and SYSTEM_ENDPOINTS[x["name"]]["endpoint"]]

                # If only a single endpoint is defined for the tool - we shall make sure it is set to default. Otherwise "system endpoint" may become a default one
                # If more then one endpoint is defined - we shall not make the changes, as it is up to the owner of the tool
                if len(system_endpoints_matched) > 0 and len(tool_endpoints) == 1:
                        current_tool_endpoint = json.loads(tool_endpoints[0])
                        current_tool_endpoint["isDefault"] = "true"
                        tool_endpoints[0] = json.dumps(current_tool_endpoint)

                # Append system endpoints to the existing list
                for system_endpoint in system_endpoints_matched:
                        tool_endpoint = { "nginx": { "port": system_endpoint["endpoint"] }, "isDefault": "false" }
                        if "friendly_name" in system_endpoint:
                                tool_endpoint["name"] = system_endpoint["friendly_name"]
                        if "endpoint_num" in system_endpoint and system_endpoint["endpoint_num"]:
                                tool_endpoint["endpoint_num"] = system_endpoint["endpoint_num"]
                        tool_endpoints.append(json.dumps(tool_endpoint))
        return tool_endpoints 

def get_service_list(pod_id, pod_run_id, pod_ip):
        service_list = {}
        get_run_details_method = os.path.join(api_url, API_GET_RUN_DETAILS.format(run_id=pod_run_id))
        response_data = call_api(get_run_details_method)
        if response_data:
                run_info = response_data["payload"]

                if not run_info["status"] or run_info["status"] != 'RUNNING':
                        print('Status for pipeline with id: {}, is not RUNNING. Service urls will not been proxied'.format(pod_run_id))
                        return {}

                pod_owner = run_info["owner"]
                docker_image = run_info["dockerImage"]
                runs_sids = None
                if "runSids" in run_info:
                        runs_sids = run_info["runSids"]
                pretty_url = None
                if "prettyUrl" in run_info:
                        pretty_url = parse_pretty_url(run_info["prettyUrl"])

                
                print('User {} is determined as an owner of PodID ({}) - RunID ({})'.format(pod_owner, pod_id, pod_run_id))

                shared_users_sids = run_sids_to_str(runs_sids, True)
                if shared_users_sids and len(shared_users_sids) > 0:
                        print('Users {} are determined as shared sids of PodID ({}) - RunID ({})'.format(shared_users_sids, pod_id, pod_run_id))

                shared_groups_sids = run_sids_to_str(runs_sids, False)
                if shared_groups_sids and len(shared_groups_sids) > 0:
                        print('Groups {} are determined as shared sids of PodID ({}) - RunID ({})'.format(shared_groups_sids, pod_id, pod_run_id))

                registry, separator, image = docker_image.partition("/")
                load_tool_method = os.path.join(api_url, API_GET_TOOL.format(registry=registry, image=image))
                endpoints_response = call_api(load_tool_method)
                if endpoints_response and "payload" in endpoints_response and "endpoints" in endpoints_response["payload"]:
                        endpoints_data = endpoints_response["payload"]["endpoints"]
                        if endpoints_data:
                                # FIXME: at the moment, "system endpoints" are added only to the runs, that already have at least one endpoint defined for the tool
                                #        it shall be fixed further to allow "system endpoints" enablement for "non-interactive" tools
                                endpoints_data = append_system_endpoints(endpoints_data, run_info)
                                endpoints_count = len(endpoints_data)
                                for i in range(endpoints_count):
                                        endpoint = json.loads(endpoints_data[i])
                                        if endpoint["nginx"]:
                                                port = endpoint["nginx"]["port"]
                                                path = endpoint["nginx"].get("path", "")
                                                service_name = '"' + endpoint["name"] + '"' if "name" in endpoint.keys() else "null"
                                                is_default_endpoint = '"' + str(endpoint["isDefault"]).lower() + '"' if "isDefault" in endpoint.keys() else '"false"'
                                                additional = endpoint["nginx"].get("additional", "")
                                                has_explicit_endpoint_num = "endpoint_num" in endpoint.keys()
                                                custom_endpoint_num = int(endpoint["endpoint_num"]) if has_explicit_endpoint_num else i
                                                if not pretty_url or has_explicit_endpoint_num:
                                                        edge_location = EDGE_ROUTE_LOCATION_TMPL.format(pod_id=pod_id, endpoint_port=port, endpoint_num=custom_endpoint_num)
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

                                                if pretty_url and pretty_url['domain']:
                                                        edge_location_id = '{}-{}.inc'.format(pretty_url['domain'], edge_location)
                                                else:
                                                        edge_location_id = '{}.loc'.format(edge_location)

                                                edge_target = \
                                                        EDGE_ROUTE_TARGET_PATH_TMPL.format(pod_ip=pod_ip, endpoint_port=port, endpoint_path=path) \
                                                                if path \
                                                                else EDGE_ROUTE_TARGET_TMPL.format(pod_ip=pod_ip, endpoint_port=port)
                                                
                                                # If CP_EDGE_NO_PATH_CROP is present (any place) in the "additional" section of the route config
                                                # then trailing "/" is not added to the proxy pass target. This will allow to forward original requests trailing path
                                                if EDGE_ROUTE_NO_PATH_CROP in additional:
                                                        additional = additional.replace(EDGE_ROUTE_NO_PATH_CROP, "")
                                                else:
                                                        edge_target = edge_target + "/"

                                                service_list[edge_location_id] = {"pod_id": pod_id,
                                                                                "pod_ip": pod_ip,
                                                                                "pod_owner": pod_owner,
                                                                                "shared_users_sids": shared_users_sids,
                                                                                "shared_groups_sids": shared_groups_sids,
                                                                                "service_name": service_name,
                                                                                "is_default_endpoint": is_default_endpoint,
                                                                                "edge_num": i,
                                                                                "edge_location": edge_location,
                                                                                "custom_domain": pretty_url['domain'] if pretty_url else None,
                                                                                "edge_target": edge_target,
                                                                                "run_id": pod_run_id,
                                                                                "additional" : additional}
                        else:
                                print('Unable to get details of the tool {} from API due to errors. Empty endpoints will be returned'.format(docker_image))
                else:
                        print('Unable to get details of the tool {} from API due to errors. Empty endpoints will be returned'.format(docker_image))
        else:
                print('Unable to get details of a RunID {} from API due to errors'.format(pod_run_id))
        return service_list

kube_api = HTTPClient(KubeConfig.from_service_account())
kube_api.session.verify = False

edge_kube_service = Service.objects(kube_api).filter(selector={EDGE_SVC_ROLE_LABEL: EDGE_SVC_ROLE_LABEL_VALUE})
if len(edge_kube_service.response['items']) == 0:
        print('EDGE service is not found by label: cloud-pipeline/role=EDGE')
        exit(1)
else:
        edge_kube_service_object = edge_kube_service.response['items'][0]
        edge_kube_service_object_metadata = edge_kube_service_object['metadata']

        if 'labels' in edge_kube_service_object_metadata and EDGE_SVC_HOST_LABEL in edge_kube_service_object_metadata['labels']:
                edge_service_external_ip = edge_kube_service_object_metadata['labels'][EDGE_SVC_HOST_LABEL]

        if 'labels' in edge_kube_service_object_metadata and EDGE_SVC_PORT_LABEL in edge_kube_service_object_metadata['labels']:
                edge_service_port = edge_kube_service_object_metadata['labels'][EDGE_SVC_PORT_LABEL]

        if not edge_service_external_ip:
                edge_service_external_ip = edge_kube_service_object['spec']['externalIPs'][0]
        if not edge_service_port:
                edge_service_port = edge_kube_service_object['ports'][0]['nodePort']
        print('EDGE service port: ' + str(edge_service_port))
        print('EDGE service ip: ' + edge_service_external_ip)

# From each pod with "job-type=Service"  we shall take:
# -- PodIP
# -- PodID
# -- N entries by a template
# --- svc-port-N
# --- svc-path-N
pods = Pod.objects(kube_api).filter(selector={'job-type': 'Service'})\
                            .filter(field_selector={"status.phase": "Running"})

services_list = {}
for pod_spec in pods.response['items']:
        pod_id = pod_spec['metadata']['name']
        pod_ip = pod_spec['status']['podIP']
        pod_run_id = pod_spec['metadata']['labels']['runid']

        if not pod_run_id:
                print('RunID not found for pod: ' + pod_id + ', skipping')
                continue

        services_list.update(get_service_list(pod_id, pod_run_id, pod_ip))

print('Found ' + str(len(services_list)) + ' running PODs with job-type: Service')

routes_kube = set([x for x in services_list])

# Find out existing routes from /etc/nginx/sites-enabled
nginx_modules_list = {}
for x in os.listdir(nginx_sites_path):
        if '.conf' in x and os.path.isfile(os.path.join(nginx_sites_path, x)):
                nginx_modules_list[x.replace('.conf', '')] = x
routes_current = set([x for x in nginx_modules_list])

# For each of the routes that exist in both Pods and NGINX we shall check, whether routes shall be updated
# If they do not match - nginx config will be deleted, thus it will be further recreated during "add" step
# For now only users/groups sharing is checked
routes_to_update = routes_current.intersection(routes_kube)
print('Found ' + str(len(routes_to_update)) + ' routes with existing configs, they will be checked for updates')

routes_were_updated = False
for update_route in routes_to_update:
        path_to_update_route = os.path.join(nginx_sites_path, nginx_modules_list[update_route])

        print('Checking nginx config for updates: {}'.format(path_to_update_route))
        with open(path_to_update_route) as update_route_file: 
                update_route_file_contents = update_route_file.read()

        shared_users_sids_to_check = ""
        shared_groups_sids_to_check = ""
        for update_route_search_results in re.finditer(r"shared_with_users\s{1,}\"(.+?)\";|shared_with_groups\s{1,}\"(.+?)\";", update_route_file_contents):
                g1 = update_route_search_results.group(1)
                g2 = update_route_search_results.group(2)
                shared_users_sids_to_check = g1 if g1 and len(g1) > 0 else shared_users_sids_to_check
                shared_groups_sids_to_check = g2 if g2 and len(g2) > 0 else shared_groups_sids_to_check

        service_spec = services_list[update_route]
        shared_users_sids_to_update = service_spec["shared_users_sids"]
        shared_groups_sids_to_update = service_spec["shared_groups_sids"]

        print('- Shared users found: "{}", while expected: "{}"'.format(shared_users_sids_to_check, shared_users_sids_to_update))
        print('- Shared groups found: "{}", while expected: "{}"'.format(shared_groups_sids_to_check, shared_groups_sids_to_update))

        # If nginx config and settings from API do not match - delete nginx config
        if shared_users_sids_to_check != shared_users_sids_to_update or shared_groups_sids_to_check != shared_groups_sids_to_update:
                print('nginx config will be deleted {}'.format(path_to_update_route))
                os.remove(path_to_update_route)
                routes_current.remove(update_route)
                routes_were_updated = True

# Perform merge of the existing routes and pods
print('Found out expired and new routes ...')
routes_to_delete = routes_current - routes_kube
print('Found ' + str(len(routes_to_delete)) + ' expired routes, this routes will be deleted')
routes_to_add = routes_kube - routes_current
print('Found ' + str(len(routes_to_add)) + ' pods without routes, routes for this pods will be added')

# For each of the routes that are not present in the list of pods - delete files from /etc/nginx/sites-enabled
for obsolete_route in routes_to_delete:
        path_to_route = os.path.join(nginx_sites_path, nginx_modules_list[obsolete_route])
        print('Deleting obsolete route: ' + path_to_route)
        os.remove(path_to_route)
        remove_custom_domain_all(path_to_route)

        

# For each of the entries in the template of the new Pods we shall build nginx route in /etc/nginx/sites-enabled
# -- File name of the route: {PodID}-{svc-port-N}-{N}
# -- location /{PodID}-{svc-port-N}-{N}/ {
# --    proxy_pass http://{PodIP}:{svc-port-N}/;
# -- }

nginx_loc_module_template_contents = ''
with open(nginx_loc_module_template, 'r') as nginx_loc_module_template_file:
    nginx_loc_module_template_contents = nginx_loc_module_template_file.read()

service_url_dict = {}
for added_route in routes_to_add:
        service_spec = services_list[added_route]

        has_custom_domain = service_spec["custom_domain"] is not None
        service_hostname = service_spec["custom_domain"] if has_custom_domain else edge_service_external_ip
        service_location = '/{}/'.format(service_spec["edge_location"]) if service_spec["edge_location"] else "/"

        nginx_route_definition = nginx_loc_module_template_contents\
                .replace('{edge_route_location}', service_location)\
                .replace('{edge_route_target}', service_spec["edge_target"])\
                .replace('{edge_route_owner}', service_spec["pod_owner"]) \
                .replace('{edge_route_shared_users}', service_spec["shared_users_sids"]) \
                .replace('{edge_route_shared_groups}', service_spec["shared_groups_sids"]) \
                .replace('{additional}', service_spec["additional"])

        path_to_route = os.path.join(nginx_sites_path, added_route + '.conf')
        print('Adding new route: ' + path_to_route)
        with open(path_to_route, "w") as added_route_file:
                added_route_file.write(nginx_route_definition)

        if has_custom_domain:
                print('Adding {} route to the server block {}'.format(path_to_route, service_hostname))
                add_custom_domain(service_hostname, path_to_route)

        service_url = SVC_URL_TMPL.format(external_ip=service_hostname,
                                          edge_location=service_spec["edge_location"] if service_spec["edge_location"] else "",
                                          edge_port=str(edge_service_port),
                                          service_name=service_spec["service_name"],
                                          is_default_endpoint=service_spec["is_default_endpoint"],
                                          external_schema=edge_service_external_schema)
        run_id = service_spec["run_id"]
        if run_id in service_url_dict:
                service_url = service_url_dict[run_id] + ',' + service_url
        service_url_dict[run_id] = service_url

# Once all entries are added to the template - run "nginx -s reload"
# TODO: Add error handling, if non-zero is returned - restore previous state
if len(routes_to_add) > 0 or len(routes_to_delete) or routes_were_updated:
        print('Reloading nginx config')
        check_output('nginx -s reload', shell=True)


# For all added entries - call API and set Service URL property for the run:
# -- Get ServiceExternalIP from the EDGE-labeled service description
# -- http://{ServiceExternalIP}/{PodID}-{svc-port-N}-{N}

for run_id in service_url_dict:
        # make array of json objects
        service_urls_json = '[' + service_url_dict[run_id] + ']'
        update_svc_method = os.path.join(api_url, API_UPDATE_SVC.format(run_id=run_id))
        print('Assigning service url ({}) to RunID: {}'.format(service_urls_json, run_id))

        data = json.dumps({'serviceUrl': service_urls_json})
        response_data = call_api(update_svc_method, data=data)
        if response_data:
                print('Service url ({}) assigned to RunID: {}'.format(service_urls_json, run_id))
        else:
                print('Service url was not assigned due to API errors')
