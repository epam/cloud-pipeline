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


nginx_root_config_path = '/etc/nginx/nginx.conf'
nginx_sites_path = '/etc/nginx/sites-enabled'
nginx_module_template = '/etc/sync-routes/route-template.conf'
edge_service_port = 31000
edge_service_external_ip = ''

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
                        pretty_url = run_info["prettyUrl"]
                        if pretty_url.startswith('/'):
                                pretty_url = pretty_url[len('/'):]
                
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
                                endpoints_count = len(endpoints_data)
                                for i in range(endpoints_count):
                                        endpoint = json.loads(endpoints_data[i])
                                        if endpoint["nginx"]:
                                                port = endpoint["nginx"]["port"]
                                                path = endpoint["nginx"].get("path", "")
                                                service_name = '"' + endpoint["name"] + '"' if "name" in endpoint.keys() else "null"
                                                is_default_endpoint = '"' + str(endpoint["isDefault"]).lower() + '"' if "isDefault" in endpoint.keys() else '"false"'
                                                additional = endpoint["nginx"].get("additional", "")
                                                if not pretty_url:
                                                        edge_location = EDGE_ROUTE_LOCATION_TMPL.format(pod_id=pod_id, endpoint_port=port, endpoint_num=i)
                                                else:
                                                        if endpoints_count == 1:
                                                                edge_location = pretty_url
                                                        else:
                                                                pretty_url_suffix = endpoint["name"] if "name" in endpoint.keys() else str(i)
                                                                edge_location = '{}-{}'.format(pretty_url, pretty_url_suffix)
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

                                                service_list[edge_location] = {"pod_id": pod_id,
                                                                                "pod_ip": pod_ip,
                                                                                "pod_owner": pod_owner,
                                                                                "shared_users_sids": shared_users_sids,
                                                                                "shared_groups_sids": shared_groups_sids,
                                                                                "service_name": service_name,
                                                                                "is_default_endpoint": is_default_endpoint,
                                                                                "edge_num": i,
                                                                                "edge_location": edge_location,
                                                                                "edge_target": edge_target,
                                                                                "run_id": pod_run_id ,
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
        

# For each of the entries in the template of the new Pods we shall build nginx route in /etc/nginx/sites-enabled
# -- File name of the route: {PodID}-{svc-port-N}-{N}
# -- location /{PodID}-{svc-port-N}-{N}/ {
# --    proxy_pass http://{PodIP}:{svc-port-N}/;
# -- }

nginx_module_template_contents = ''
with open(nginx_module_template, 'r') as nginx_module_template_file:
    nginx_module_template_contents = nginx_module_template_file.read()

service_url_dict = {}
for added_route in routes_to_add:
        service_spec = services_list[added_route]
        nginx_route_definition = nginx_module_template_contents\
                .replace('{edge_route_location}', service_spec["edge_location"])\
                .replace('{edge_route_target}', service_spec["edge_target"])\
                .replace('{edge_route_owner}', service_spec["pod_owner"]) \
                .replace('{edge_route_shared_users}', service_spec["shared_users_sids"]) \
                .replace('{edge_route_shared_groups}', service_spec["shared_groups_sids"]) \
                .replace('{additional}', service_spec["additional"])
        path_to_route = os.path.join(nginx_sites_path, added_route + '.conf')
        print('Adding new route: ' + path_to_route)
        with open(path_to_route, "w") as added_route_file:
                added_route_file.write(nginx_route_definition)

        service_url = SVC_URL_TMPL.format(external_ip=edge_service_external_ip,
                                          edge_location=service_spec["edge_location"],
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


