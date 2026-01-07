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
    EDGE_SERVICE_PORT,
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
        return str(response.get('payload', {}).get('value'))

    def is_true(self, value):
        return value and value.lower() == 'true'

    def create_dns_record(self, service_spec, edge_region_id, edge_region_name, edge_service_external_ip):
         # DNS Creation Logic
         logger = RunLogger(service_spec["run_id"], 'CreateDNSRecord', self.api)
         dns_rec = EDGE_DNS_RECORD_FORMAT.format(job_name=service_spec["edge_location"], region_name=edge_region_name)
         
         url = API_POST_DNS_RECORD
         if edge_region_id: url += "?regionId=" + edge_region_id
         
         data = json.dumps({'dnsRecord': dns_rec, 'target': edge_service_external_ip, 'format': 'RELATIVE'})
         logger.info('Creating DNS record {}...'.format(dns_rec))
         
         resp = self.api.call_api(url, data=data) or {}
         payload = resp.get('payload', {})
         if payload.get('status') != 'INSYNC':
             logger.warning('Failed to create DNS record')
             raise ValueError('Fail to create DNS record')
             
         logger.success('Created DNS record {}'.format(payload.get('dnsRecord')))
         service_spec["custom_domain"] = payload.get('dnsRecord')
         service_spec["edge_location"] = None
         return service_spec

    def update_svc_url(self, run_id, region_name, service_url):
         if not service_url: return
         do_log('Assigning #{} with service url'.format(run_id))
         url = API_UPDATE_SVC.format(run_id=run_id, region=region_name)
         self.api.call_api(url, data=json.dumps({'serviceUrl': '[' + service_url + ']'}))

    def sync(self):
        # 1. Config & Kube Init
        run_pod_selector_key = os.getenv('CP_EDGE_RUN_POD_SELECTOR_KEY', "type")
        run_pod_selector_value = os.getenv('CP_EDGE_RUN_POD_SELECTOR_VALUE', "pipeline")
        
        edge_region_name = os.getenv('CP_EDGE_REGION') or self.find_preference('default.edge.region')
        edge_region_id = os.getenv('CP_EDGE_REGION_ID') or self.find_preference('default.edge.region.id')
        
        skip_custom_dns = self.is_true(os.getenv('CP_EDGE_SKIP_CUSTOM_DNS') or self.find_preference('edge.skip.custom.dns'))
        dns_domain = os.getenv('CP_EDGE_CUSTOM_DOMAIN') or self.find_preference('edge.custom.domain')

        ext_ip, ext_port = self.kube.get_edge_service_details(edge_region_name, edge_region_id)
        if not ext_ip: 
            do_log('Failed to determine Edge Service IP')
            exit(1)

        # 2. Endpoints Resolution
        pods = self.kube.get_pods({run_pod_selector_key: run_pod_selector_value})
        
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
                if matched: pods_with_endpoints.append(p)

        run_ids = [p['metadata']['labels']['runid'] for p in pods_with_endpoints if p['metadata']['labels'].get('runid')]
        active_runs = []
        if run_ids:
            resp = self.api.call_api("runs?runIds=" + ",".join(run_ids))
            if resp and 'payload' in resp: 
                active_runs = resp['payload']
            else:
                unicode_ids = "[" + ", ".join(["u'{}'".format(rid) for rid in run_ids]) + "]"
                do_log('Cannot get list of active runs from the API for the following IDs: {}'.format(unicode_ids))

        services = {}
        active_run_ids = [str(r['pipelineRun']['id']) for r in active_runs]
        for p in pods_with_endpoints:
            pod_id = p['metadata']['name']
            pod_ip = p['status']['podIP']
            rid = p['metadata']['labels'].get('runid')
            if rid:
                if str(rid) not in active_run_ids:
                    do_log('Cannot find the RunID {} in the list of cached runs, skipping'.format(rid))
                    continue
                services.update(self.spec_builder.get_service_list(active_runs, pod_id, rid, pod_ip))

        routes_expected = set(services.keys())
        do_log('Found {} expected routes'.format(len(routes_expected)))

        # 3. Nginx Scaning
        nginx_list = {}
        for x in os.listdir(NGINX_SITES_PATH):
            path = os.path.join(NGINX_SITES_PATH, x)
            if not os.path.isfile(path) or '.conf' not in x: continue
            
            if x.endswith(STUB_LOCATION_CONFIG_EXTENSION):
                os.remove(path); continue
            if x.endswith(STUB_CUSTOM_DOMAIN_EXTENSION):
                os.remove(path)
                self.nginx.remove_custom_domain_all(path)
                continue
            
            key = x.replace('.loc.conf', '').replace('.inc.conf', '')
            nginx_list[key] = x

        routes_actual = set(nginx_list.keys())
        do_log('Found {} actual routes'.format(len(routes_actual)))

        routes_to_check = routes_actual & routes_expected
        routes_to_add = routes_expected - routes_actual
        routes_to_delete = routes_actual - routes_expected
        do_log('Found {} existing routes, these routes will be checked'.format(len(routes_to_check)))
        do_log('Found {} missing routes, these routes will be created'.format(len(routes_to_add)))
        do_log('Found {} expired routes, these routes will be deleted'.format(len(routes_to_delete)))
        
        # 4. Check & Update
        routes_to_update = set()
        for r in routes_to_check:
            path = os.path.join(NGINX_SITES_PATH, nginx_list[r])
            with open(path) as f: content = f.read()
            
            match_users = re.search(r'shared_with_users\s+"(.+?)";', content)
            match_groups = re.search(r'shared_with_groups\s+"(.+?)";', content)
            
            curr_users = match_users.group(1) if match_users else ""
            curr_groups = match_groups.group(1) if match_groups else ""
            
            spec = services[r]
            if curr_users != spec["shared_users_sids"] or curr_groups != spec["shared_groups_sids"]:
                routes_to_update.add(r)
        
        do_log('Found {} changed routes, these routes will be replaced'.format(len(routes_to_update)))

        # 5. Dependency Update
        def get_pod_from_route(route):
            m = re.match(ROUTE_ID_PATTERN, route)
            return m.group(1) if m else None
        
        affected_pods = set([get_pod_from_route(r) for r in (routes_to_add | routes_to_delete | routes_to_update)])
        if None in affected_pods: affected_pods.remove(None)
        
        routes_to_replace = set([r for r in routes_to_check if get_pod_from_route(r) in affected_pods])
        routes_to_affect = routes_to_replace - routes_to_update
        do_log('Found {} affected routes, these routes will be replaced'.format(len(routes_to_affect)))
        
        routes_to_add |= routes_to_replace
        routes_to_delete |= routes_to_replace

        do_log("Deleting {} routes...".format(len(routes_to_delete)))

        # 6. Apply Changes
        for r in routes_to_delete:
            path = os.path.join(NGINX_SITES_PATH, nginx_list[r])
            do_log('Deleting route {}'.format(path))
            os.remove(path)
            self.nginx.remove_custom_domain_all(path)

        regular_add = []
        dns_add = []
        for r in routes_to_add:
            spec = services[r]
            if spec["create_dns_record"] and not spec["custom_domain"]:
                dns_add.append(r)
            else:
                regular_add.append(r)
        
        svc_url_dict = {}

        do_log("Creating {} routes for regular endpoints...".format(len(regular_add)))
        for r in regular_add:
            spec = services[r]
            hostname = spec["custom_domain"] if spec["custom_domain"] else ext_ip
            self.nginx.write_route_config(spec, hostname, spec["custom_domain"] is not None)
            self.nginx.verify_and_fix_route(
                os.path.join(NGINX_SITES_PATH, spec['edge_location_path'] + '.conf'),
                '/{}/'.format(spec['edge_location']) if spec['edge_location'] else '/',
                spec, spec["custom_domain"] is not None, hostname
            )
            # Build svc url string ... (simplified)
            url = SVC_URL_TMPL.format(
                external_schema=os.environ.get('EDGE_EXTERNAL_SCHEMA', 'https'),
                external_ip=hostname,
                edge_port=str(EDGE_SERVICE_PORT),
                edge_location=spec.get('edge_location') or '',
                service_name=spec['service_name'],
                is_default_endpoint=str(spec['is_default_endpoint']).lower(),
                is_same_tab=str(spec['is_same_tab']).lower(),
                is_custom_dns=str(spec['create_dns_record']).lower(),
                region_id=edge_region_id or 'null'
            )
            run_id = spec['run_id']
            svc_url_dict[run_id] = (svc_url_dict[run_id] + ',\n' + url) if run_id in svc_url_dict else url

        # DNS async
        dns_route_runs = set()
        dns_results = []
        if dns_add:
            do_log("Creating {} configurations for dns endpoints...".format(len(dns_add)))
            for r in dns_add:
                spec = services[r]
                dns_route_runs.add(spec["run_id"])
                if skip_custom_dns:
                     # Simulate async behavior if skipping
                     dns_results.append((spec, r))
                else:
                     dns_results.append(self.pool.apply_async(
                        self.create_dns_record,
                        (spec, edge_region_id, edge_region_name, ext_ip)
                     ))

        # Check for regular/delete reload
        if regular_add or routes_to_delete:
            self.nginx.reload_nginx()

        # Update regular svc urls
        for rid, url in svc_url_dict.items():
            if rid not in dns_route_runs:
                self.update_svc_url(rid, edge_region_name, url)

        # Post-processing DNS routes
        dns_added_routes = []
        if dns_add:
            if skip_custom_dns:
                for spec, r in dns_results:
                    spec["custom_domain"] = "{}.{}".format(
                        EDGE_DNS_RECORD_FORMAT.format(job_name=spec["edge_location"], region_name=edge_region_name),
                        dns_domain
                    )
                    spec["edge_location"] = None
                    dns_added_routes.append(r)
            else:
                for res in dns_results:
                    try:
                        spec = res.get()
                        if spec:
                            dns_added_routes.append(spec["edge_location_path"].replace(".inc", "")) # Temporary key
                    except Exception as e:
                        do_log("DNS creation failed: {}".format(e))
            
            do_log("Creating {} routes for dns endpoints...".format(len(dns_added_routes)))
            for r in dns_add: # Re-using keys from dns_add for simplicity in this refactor
                spec = services[r]
                hostname = spec["custom_domain"]
                self.nginx.write_route_config(spec, hostname, True)
                # Svc url update
                url = SVC_URL_TMPL.format(
                    external_schema=os.environ.get('EDGE_EXTERNAL_SCHEMA', 'https'),
                    external_ip=hostname,
                    edge_port=str(EDGE_SERVICE_PORT),
                    edge_location=spec.get('edge_location') or '',
                    service_name=spec['service_name'],
                    is_default_endpoint=str(spec['is_default_endpoint']).lower(),
                    is_same_tab=str(spec['is_same_tab']).lower(),
                    is_custom_dns=str(spec['create_dns_record']).lower(),
                    region_id=edge_region_id or 'null'
                )
                run_id = spec['run_id']
                svc_url_dict[run_id] = (svc_url_dict[run_id] + ',\n' + url) if run_id in svc_url_dict else url

            self.nginx.reload_nginx()
            
            for rid, url in svc_url_dict.items():
                if rid in dns_route_runs:
                    self.update_svc_url(rid, edge_region_name, url)
        else:
            do_log("Creating 0 configurations for dns endpoints...")
            do_log("Creating 0 routes for dns endpoints...")

        self.pool.close()
        self.pool.join()
