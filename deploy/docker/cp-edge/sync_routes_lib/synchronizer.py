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
import re
import json
from .config import (
    API_GET_PREF, 
    API_POST_DNS_RECORD, 
    EDGE_DNS_RECORD_FORMAT,
    ROUTE_ID_PATTERN,
    NGINX_SITES_PATH,
    STUB_LOCATION_CONFIG_EXTENSION,
    STUB_CUSTOM_DOMAIN_EXTENSION,
    SVC_URL_TMPL,
    API_UPDATE_SVC
)
from .logger import do_log, RunLogger
from .service_spec_builder import ServiceSpecBuilder
from multiprocessing.pool import ThreadPool as Pool

class RouteSynchronizer:
    def __init__(self, kube_client, api_client, nginx_manager, pool_size=8):
        self.kube = kube_client
        self.api = api_client
        self.nginx = nginx_manager
        self.pool = Pool(pool_size)
        self.spec_builder = ServiceSpecBuilder(api_client)

    def find_preference(self, preference_name):
        response = self.api.call_api(API_GET_PREF.format(preference_name=preference_name)) or {}
        value = response.get('payload', {}).get('value')
        return str(value) if value is not None else None

    def is_true(self, value):
        return value and value.lower() == 'true'

    def create_dns_record(self, service_spec, edge_region_id, edge_region_name, edge_service_external_ip):
         # DNS Creation Logic
         logger = RunLogger(service_spec.run_id, 'CreateDNSRecord', self.api)
         dns_rec = EDGE_DNS_RECORD_FORMAT.format(job_name=service_spec.edge_location, region_name=edge_region_name)
         
         url = API_POST_DNS_RECORD
         if edge_region_id: url += f"?regionId={edge_region_id}"
         
         data = json.dumps({'dnsRecord': dns_rec, 'target': edge_service_external_ip, 'format': 'RELATIVE'})
         logger.info(f'Creating DNS record {dns_rec}...')
         
         resp = self.api.call_api(url, data=data) or {}
         payload = resp.get('payload', {})
         if payload.get('status') != 'INSYNC':
             logger.warning('Failed to create DNS record')
             raise ValueError('Fail to create DNS record')
             
         logger.success(f"Created DNS record {payload.get('dnsRecord')}")
         service_spec.custom_domain = payload.get('dnsRecord')
         service_spec.edge_location = None
         return service_spec

    def update_svc_url_for_run(self, run_id, region_name, service_url):
        if not service_url:
            do_log(f'Assigning #{run_id} with service url has been skipped '
                   'because the corresponding service url has not been found.')
            return
        do_log(f'Assigning #{run_id} with service url \n{service_url}')
        url = API_UPDATE_SVC.format(run_id=run_id, region=region_name)
        response_data = self.api.call_api(url, data=json.dumps({'serviceUrl': '[' + service_url + ']'}))
        if response_data:
            do_log(f'Assigning #{run_id} with service url ... OK')
        else:
            do_log(f'Assigning #{run_id} with service url ... NOT OK.')

    def sync(self):
        run_pod_selector_key = os.getenv('CP_EDGE_RUN_POD_SELECTOR_KEY', "type")
        run_pod_selector_value = os.getenv('CP_EDGE_RUN_POD_SELECTOR_VALUE', "pipeline")
        
        edge_region_name = os.getenv('CP_EDGE_REGION') or self.find_preference('default.edge.region')
        edge_region_id = os.getenv('CP_EDGE_REGION_ID') or self.find_preference('default.edge.region.id')
        skip_custom_dns = self.is_true(os.getenv('CP_EDGE_SKIP_CUSTOM_DNS') or self.find_preference('edge.skip.custom.dns'))
        dns_domain = os.getenv('CP_EDGE_CUSTOM_DOMAIN') or self.find_preference('edge.custom.domain')
        

        edge_service_external_schema = os.environ.get('EDGE_EXTERNAL_SCHEMA', 'https')
        edge_service_external_ip, edge_service_port = self.kube.get_edge_service_details(edge_region_name, edge_region_id)
        if not edge_service_external_ip:
            do_log('Failed to determine Edge Service IP')
            exit(1)
        
        pods_with_endpoints = self.load_pods_for_runs_with_endpoints(run_pod_selector_key, run_pod_selector_value)
        runs_with_endpoints = self.get_active_runs(pods_with_endpoints)
        services_list = self._resolve_expected_routes(pods_with_endpoints, runs_with_endpoints)
        
        nginx_modules_list = self._resolve_actual_routes()
        routes_to_add, routes_to_delete = self._calculate_route_diff(services_list, nginx_modules_list)
        
        self._delete_routes(routes_to_delete, nginx_modules_list)

        regular_routes_to_add = [r for r in routes_to_add if not (services_list[r].create_dns_record and not services_list[r].custom_domain)]
        dns_routes_to_configure = [r for r in routes_to_add if services_list[r].create_dns_record and not services_list[r].custom_domain]
        
        service_url_dict = self._create_regular_routes(regular_routes_to_add, services_list, edge_service_external_ip, edge_service_port, edge_service_external_schema, edge_region_id)
        
        dns_route_runs, dns_route_results = self._create_dns_routes(dns_routes_to_configure, services_list, edge_service_external_ip, edge_region_name, edge_region_id, skip_custom_dns, dns_domain)
        
        if regular_routes_to_add or routes_to_delete:
            self.nginx.reload_nginx_config()
            
        for run_id, url in service_url_dict.items():
            if run_id not in dns_route_runs:
                self.update_svc_url_for_run(run_id, edge_region_name, url)
                
        service_url_dict = self._process_dns_results(dns_route_results, dns_routes_to_configure, services_list, service_url_dict, edge_service_external_ip, edge_service_port, edge_service_external_schema, edge_region_id, edge_region_name, skip_custom_dns, dns_domain)

        if dns_routes_to_configure:
            self.nginx.reload_nginx_config()
            for run_id, url in service_url_dict.items():
                if run_id in dns_route_runs:
                    self.update_svc_url_for_run(run_id, edge_region_name, url)

        self.pool.close()
        self.pool.join()

    def load_pods_for_runs_with_endpoints(self, selector_key, selector_value):
        # From each pod with a container, which has endpoints ("job-type=Service" or container's environment
        # has a parameter from SYSTEM_ENDPOINTS) we shall take:
        # -- PodIP
        # -- PodID
        # -- N entries by a template
        # --- svc-port-N
        # --- svc-path-N
        pods = self.kube.get_pods({selector_key: selector_value})
        pods_with_endpoints = []
        for p in pods.response['items']:
            labels = p['metadata']['labels']
            if labels.get('job-type') == 'Service':
                pods_with_endpoints.append(p)
                continue
            containers = p.get('spec', {}).get('containers', [])
            if containers and containers[0].get('env'):
                env = containers[0]['env']
                matched = [v for v in env if v['name'] in self.spec_builder.system_endpoints_config and 
                           self.spec_builder.match_sys_endpoint_value(v['value'], self.spec_builder.system_endpoints_config[v['name']]['value'])]
                if matched:
                    pods_with_endpoints.append(p)
        return pods_with_endpoints

    def get_active_runs(self, pods):
        run_ids = [p['metadata']['labels']['runid'] for p in pods if p['metadata']['labels'].get('runid')]
        if not run_ids:
            return []
        resp = self.api.call_api("runs?runIds=" + ",".join(run_ids))
        if resp and 'payload' in resp:
            return resp['payload']
        
        unicode_ids = "[" + ", ".join([f"u'{rid}'" for rid in run_ids]) + "]"
        do_log(f'Cannot get list of active runs from the API for the following IDs: {unicode_ids}')
        return []

    def _resolve_expected_routes(self, pods_with_endpoints, runs_with_endpoints):
        services_list = {}
        active_run_ids = [str(r['pipelineRun']['id']) for r in runs_with_endpoints]
        for pod_spec in pods_with_endpoints:
            pod_id = pod_spec['metadata']['name']
            pod_ip = pod_spec['status']['podIP']
            pod_run_id = pod_spec['metadata']['labels'].get('runid')
            if pod_run_id:
                if str(pod_run_id) not in active_run_ids:
                    do_log(f'Cannot find the RunID {pod_run_id} in the list of cached runs, skipping')
                    continue
                services_list.update(self.spec_builder.get_service_list(runs_with_endpoints, pod_id, pod_run_id, pod_ip))
        do_log(f'Found {len(services_list)} expected routes')
        return services_list

    def _resolve_actual_routes(self):
        # Find out existing routes from /etc/nginx/sites-enabled
        nginx_modules_list = {}
        for x in os.listdir(NGINX_SITES_PATH):
            location_config_path = os.path.join(NGINX_SITES_PATH, x)
            if '.conf' in x and os.path.isfile(location_config_path):
                if location_config_path.endswith(STUB_LOCATION_CONFIG_EXTENSION):
                    do_log('Deleting stub route ' + location_config_path)
                    os.remove(location_config_path); continue
                if location_config_path.endswith(STUB_CUSTOM_DOMAIN_EXTENSION):
                    do_log('Deleting custom domain stub route ' + location_config_path)
                    os.remove(location_config_path)
                    self.nginx.remove_custom_domain_all(location_config_path)
                    continue
                nginx_modules_list[x.replace('.loc.conf', '').replace('.inc.conf', '')] = x
        do_log(f'Found {len(nginx_modules_list)} actual routes')
        return nginx_modules_list

    def _calculate_route_diff(self, services_list, nginx_modules_list):
        routes_expected = set(services_list.keys())
        routes_actual = set(nginx_modules_list.keys())
        
        routes_to_check = routes_actual & routes_expected
        routes_to_add = routes_expected - routes_actual
        routes_to_delete = routes_actual - routes_expected
        
        do_log(f'Found {len(routes_to_check)} existing routes, these routes will be checked')
        do_log(f'Found {len(routes_to_add)} missing routes, these routes will be created')
        do_log(f'Found {len(routes_to_delete)} expired routes, these routes will be deleted')

        # All routes that exist in both Nginx and API are checked, whether the routes shall be updated or kept untouched.
        # If some routes differ then they are deleted and created from scratch.
        # Currently only modified sharing users/groups are checked.
        routes_to_update = set()
        for route in routes_to_check:
            path_to_route = os.path.join(NGINX_SITES_PATH, nginx_modules_list[route])
            
            do_log(f'Checking route {path_to_route}')
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
            
            spec = services_list[route]
            shared_users_sids_to_update = spec.shared_users_sids
            shared_groups_sids_to_update = spec.shared_groups_sids

            if shared_users_sids_to_check != shared_users_sids_to_update:
                do_log(f'Detected different shared users. Actual: "{shared_users_sids_to_check}". Expected: "{shared_users_sids_to_update}"')
                routes_to_update.add(route)
            elif shared_groups_sids_to_check != shared_groups_sids_to_update:
                do_log(f'Detected different shared groups. Actual: "{shared_groups_sids_to_check}". Expected: "{shared_groups_sids_to_update}"')
                routes_to_update.add(route)
        
        do_log(f'Found {len(routes_to_update)} changed routes, these routes will be replaced')

        # If a single route of a pod is added/deleted/updated then all other routes of the same pod are replaced.
        # Otherwise, the generated service url will have missing/extra endpoints.
        def get_pod_from_route(route):
            m = re.match(ROUTE_ID_PATTERN, route)
            return m.group(1) if m else None
        
        affected_pods = set([get_pod_from_route(route) for route in (routes_to_add | routes_to_delete | routes_to_update)])
        if None in affected_pods:
            affected_pods.remove(None)
        
        routes_to_replace = set([route for route in routes_to_check if get_pod_from_route(route) in affected_pods])
        routes_to_affect = routes_to_replace - routes_to_update
        do_log(f'Found {len(routes_to_affect)} affected routes, these routes will be replaced')
        
        return (routes_to_add | routes_to_replace), (routes_to_delete | routes_to_replace)

    def _delete_routes(self, routes_to_delete, nginx_modules_list):
        do_log(f"Deleting {len(routes_to_delete)} routes...")
        if not routes_to_delete:
            return
        for route in routes_to_delete:
            path_to_route = os.path.join(NGINX_SITES_PATH, nginx_modules_list[route])
            do_log(f'Deleting route {path_to_route}')
            os.remove(path_to_route)
            self.nginx.remove_custom_domain_all(path_to_route)

    def _create_regular_routes(self, regular_routes_to_add, services_list, edge_service_external_ip, edge_service_port, edge_service_external_schema, edge_region_id):
        # loop through all routes that we need to create, if this route doesn't have option to create custom DNS record
        # we handle it in the main thread, if custom DNS record should be created, since it consume some time ~ 20 sec,
        # we put it to the separate collection to handle it at the end.
        do_log(f"Creating {len(regular_routes_to_add)} routes for regular endpoints...")
        service_url_dict = {}
        for route in regular_routes_to_add:
            spec = services_list[route]
            hostname = spec.custom_domain if spec.custom_domain else edge_service_external_ip
            self.nginx.write_route_config(spec, hostname, spec.custom_domain is not None)
            self.nginx.check_route(
                os.path.join(NGINX_SITES_PATH, spec.edge_location_path + '.conf'),
                '/{}/'.format(spec.edge_location) if spec.edge_location else '/',
                spec, spec.custom_domain is not None, hostname
            )
            url = self._build_svc_url(spec, hostname, edge_service_port, edge_service_external_schema, edge_region_id)
            run_id = spec.run_id
            service_url_dict[run_id] = (service_url_dict[run_id] + ',\n' + url) if run_id in service_url_dict else url
        return service_url_dict

    def _build_svc_url(self, spec, hostname, edge_service_port, edge_service_external_schema, region_id):
        return SVC_URL_TMPL.format(
            external_schema=edge_service_external_schema,
            external_ip=hostname,
            edge_port=str(edge_service_port),
            edge_location=spec.edge_location or '',
            service_name=spec.service_name,
            is_default_endpoint=str(spec.is_default_endpoint).lower(),
            is_same_tab=str(spec.is_same_tab).lower(),
            is_custom_dns=str(spec.create_dns_record).lower(),
            region_id=region_id or 'null'
        )

    def _create_dns_routes(self, dns_routes_to_configure, services_list, edge_service_external_ip, edge_region_name, edge_region_id, skip_custom_dns, dns_domain):
        dns_route_runs = set()
        dns_route_results = []
        if not dns_routes_to_configure:
            do_log("Creating 0 configurations for dns endpoints...")
            return dns_route_runs, dns_route_results

        do_log(f"Creating {len(dns_routes_to_configure)} configurations for dns endpoints...")
        for route in dns_routes_to_configure:
            spec = services_list[route]
            dns_route_runs.add(spec.run_id)
            if skip_custom_dns:
                dns_route_results.append((spec, route))
            else:
                dns_route_results.append(self.pool.apply_async(
                    self.create_dns_record,
                    (spec, edge_region_id, edge_region_name, edge_service_external_ip)
                ))
        return dns_route_runs, dns_route_results

    def _process_dns_results(self, dns_route_results, dns_routes_to_configure, services_list, service_url_dict, edge_service_external_ip, edge_service_port, edge_service_external_schema, edge_region_id, edge_region_name, skip_custom_dns, dns_domain):
        if not dns_routes_to_configure:
            do_log("Creating 0 routes for dns endpoints...")
            return service_url_dict

        dns_routes_to_add = []
        if skip_custom_dns:
            for spec, route in dns_route_results:
                spec.custom_domain = "{}.{}".format(
                    EDGE_DNS_RECORD_FORMAT.format(job_name=spec.edge_location, region_name=edge_region_name),
                    dns_domain
                )
                spec.edge_location = None
                dns_routes_to_add.append(route)
        else:
            for res in dns_route_results:
                try:
                    spec = res.get()
                    if spec:
                        dns_routes_to_add.append(spec.edge_location_path.replace(".inc", ""))
                except Exception as e:
                    do_log(f"DNS creation failed: {e}")
        
        do_log(f"Creating {len(dns_routes_to_add)} routes for dns endpoints...")
        for route in dns_routes_to_configure:
            spec = services_list[route]
            hostname = spec.custom_domain
            self.nginx.write_route_config(spec, hostname, True)
            url = self._build_svc_url(spec, hostname, edge_service_port, edge_service_external_schema, edge_region_id)
            run_id = spec.run_id
            service_url_dict[run_id] = (service_url_dict[run_id] + ',\n' + url) if run_id in service_url_dict else url
        return service_url_dict
