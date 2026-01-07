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
        value = response.get('payload', {}).get('value')
        return str(value) if value is not None else None

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
        conf = self._load_config()
        ext_ip, ext_port = self.kube.get_edge_service_details(conf['region_name'], conf['region_id'])
        if not ext_ip:
            do_log('Failed to determine Edge Service IP')
            exit(1)

        pods_with_endpoints = self._get_pods_with_endpoints(conf['selector_key'], conf['selector_value'])
        active_runs = self._get_active_runs(pods_with_endpoints)
        services = self._resolve_expected_routes(pods_with_endpoints, active_runs)
        
        nginx_list = self._resolve_actual_routes()
        routes_to_add, routes_to_delete = self._calculate_route_diff(services, nginx_list)
        
        self._delete_routes(routes_to_delete, nginx_list)

        regular_add = [r for r in routes_to_add if not (services[r]["create_dns_record"] and not services[r]["custom_domain"])]
        dns_add = [r for r in routes_to_add if services[r]["create_dns_record"] and not services[r]["custom_domain"]]
        
        svc_url_dict = self._create_regular_routes(regular_add, services, ext_ip, conf)
        
        dns_route_runs, dns_results = self._create_dns_routes(dns_add, services, ext_ip, conf)
        
        if regular_add or routes_to_delete:
            self.nginx.reload_nginx()
            
        for rid, url in svc_url_dict.items():
            if rid not in dns_route_runs:
                self.update_svc_url(rid, conf['region_name'], url)
                
        svc_url_dict = self._process_dns_results(dns_results, dns_add, services, svc_url_dict, conf, ext_ip)

        if dns_add:
            self.nginx.reload_nginx()
            for rid, url in svc_url_dict.items():
                if rid in dns_route_runs:
                    self.update_svc_url(rid, conf['region_name'], url)

        self.pool.close()
        self.pool.join()

    def _load_config(self):
        return {
            'selector_key': os.getenv('CP_EDGE_RUN_POD_SELECTOR_KEY', "type"),
            'selector_value': os.getenv('CP_EDGE_RUN_POD_SELECTOR_VALUE', "pipeline"),
            'region_name': os.getenv('CP_EDGE_REGION') or self.find_preference('default.edge.region'),
            'region_id': os.getenv('CP_EDGE_REGION_ID') or self.find_preference('default.edge.region.id'),
            'skip_custom_dns': self.is_true(os.getenv('CP_EDGE_SKIP_CUSTOM_DNS') or self.find_preference('edge.skip.custom.dns')),
            'dns_domain': os.getenv('CP_EDGE_CUSTOM_DOMAIN') or self.find_preference('edge.custom.domain')
        }

    def _get_pods_with_endpoints(self, selector_key, selector_value):
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

    def _get_active_runs(self, pods):
        run_ids = [p['metadata']['labels']['runid'] for p in pods if p['metadata']['labels'].get('runid')]
        if not run_ids:
            return []
        resp = self.api.call_api("runs?runIds=" + ",".join(run_ids))
        if resp and 'payload' in resp:
            return resp['payload']
        
        unicode_ids = "[" + ", ".join(["u'{}'".format(rid) for rid in run_ids]) + "]"
        do_log('Cannot get list of active runs from the API for the following IDs: {}'.format(unicode_ids))
        return []

    def _resolve_expected_routes(self, pods, active_runs):
        services = {}
        active_run_ids = [str(r['pipelineRun']['id']) for r in active_runs]
        for p in pods:
            pod_id = p['metadata']['name']
            pod_ip = p['status']['podIP']
            rid = p['metadata']['labels'].get('runid')
            if rid:
                if str(rid) not in active_run_ids:
                    do_log('Cannot find the RunID {} in the list of cached runs, skipping'.format(rid))
                    continue
                services.update(self.spec_builder.get_service_list(active_runs, pod_id, rid, pod_ip))
        do_log('Found {} expected routes'.format(len(services)))
        return services

    def _resolve_actual_routes(self):
        nginx_list = {}
        for x in os.listdir(NGINX_SITES_PATH):
            path = os.path.join(NGINX_SITES_PATH, x)
            if not os.path.isfile(path) or '.conf' not in x:
                continue
            if x.endswith(STUB_LOCATION_CONFIG_EXTENSION):
                os.remove(path); continue
            if x.endswith(STUB_CUSTOM_DOMAIN_EXTENSION):
                os.remove(path)
                self.nginx.remove_custom_domain_all(path)
                continue
            key = x.replace('.loc.conf', '').replace('.inc.conf', '')
            nginx_list[key] = x
        do_log('Found {} actual routes'.format(len(nginx_list)))
        return nginx_list

    def _calculate_route_diff(self, services, nginx_list):
        expected = set(services.keys())
        actual = set(nginx_list.keys())
        
        to_check = actual & expected
        to_add = expected - actual
        to_delete = actual - expected
        
        do_log('Found {} existing routes, these routes will be checked'.format(len(to_check)))
        do_log('Found {} missing routes, these routes will be created'.format(len(to_add)))
        do_log('Found {} expired routes, these routes will be deleted'.format(len(to_delete)))

        to_update = set()
        for r in to_check:
            path = os.path.join(NGINX_SITES_PATH, nginx_list[r])
            with open(path) as f:
                content = f.read()
            
            match_users = re.search(r'shared_with_users\s+"(.+?)";', content)
            match_groups = re.search(r'shared_with_groups\s+"(.+?)";', content)
            
            curr_users = match_users.group(1) if match_users else ""
            curr_groups = match_groups.group(1) if match_groups else ""
            
            spec = services[r]
            if curr_users != spec["shared_users_sids"] or curr_groups != spec["shared_groups_sids"]:
                to_update.add(r)
        
        do_log('Found {} changed routes, these routes will be replaced'.format(len(to_update)))

        def get_pod_from_route(route):
            m = re.match(ROUTE_ID_PATTERN, route)
            return m.group(1) if m else None
        
        affected_pods = set([get_pod_from_route(r) for r in (to_add | to_delete | to_update)])
        if None in affected_pods:
            affected_pods.remove(None)
        
        to_replace = set([r for r in to_check if get_pod_from_route(r) in affected_pods])
        affected = to_replace - to_update
        do_log('Found {} affected routes, these routes will be replaced'.format(len(affected)))
        
        return (to_add | to_replace), (to_delete | to_replace)

    def _delete_routes(self, routes_to_delete, nginx_list):
        do_log("Deleting {} routes...".format(len(routes_to_delete)))
        if not routes_to_delete:
            return
        for r in routes_to_delete:
            path = os.path.join(NGINX_SITES_PATH, nginx_list[r])
            do_log('Deleting route {}'.format(path))
            os.remove(path)
            self.nginx.remove_custom_domain_all(path)

    def _create_regular_routes(self, regular_add, services, ext_ip, conf):
        do_log("Creating {} routes for regular endpoints...".format(len(regular_add)))
        svc_url_dict = {}
        for r in regular_add:
            spec = services[r]
            hostname = spec["custom_domain"] if spec["custom_domain"] else ext_ip
            self.nginx.write_route_config(spec, hostname, spec["custom_domain"] is not None)
            self.nginx.verify_and_fix_route(
                os.path.join(NGINX_SITES_PATH, spec['edge_location_path'] + '.conf'),
                '/{}/'.format(spec['edge_location']) if spec['edge_location'] else '/',
                spec, spec["custom_domain"] is not None, hostname
            )
            url = self._build_svc_url(spec, hostname, conf['region_id'])
            run_id = spec['run_id']
            svc_url_dict[run_id] = (svc_url_dict[run_id] + ',\n' + url) if run_id in svc_url_dict else url
        return svc_url_dict

    def _build_svc_url(self, spec, hostname, region_id):
        return SVC_URL_TMPL.format(
            external_schema=os.environ.get('EDGE_EXTERNAL_SCHEMA', 'https'),
            external_ip=hostname,
            edge_port=str(EDGE_SERVICE_PORT),
            edge_location=spec.get('edge_location') or '',
            service_name=spec['service_name'],
            is_default_endpoint=str(spec['is_default_endpoint']).lower(),
            is_same_tab=str(spec['is_same_tab']).lower(),
            is_custom_dns=str(spec['create_dns_record']).lower(),
            region_id=region_id or 'null'
        )

    def _create_dns_routes(self, dns_add, services, ext_ip, conf):
        dns_route_runs = set()
        dns_results = []
        if not dns_add:
            do_log("Creating 0 configurations for dns endpoints...")
            return dns_route_runs, dns_results

        do_log("Creating {} configurations for dns endpoints...".format(len(dns_add)))
        for r in dns_add:
            spec = services[r]
            dns_route_runs.add(spec["run_id"])
            if conf['skip_custom_dns']:
                dns_results.append((spec, r))
            else:
                dns_results.append(self.pool.apply_async(
                    self.create_dns_record,
                    (spec, conf['region_id'], conf['region_name'], ext_ip)
                ))
        return dns_route_runs, dns_results

    def _process_dns_results(self, dns_results, dns_add, services, svc_url_dict, conf, ext_ip):
        if not dns_add:
            do_log("Creating 0 routes for dns endpoints...")
            return svc_url_dict

        dns_added_routes = []
        if conf['skip_custom_dns']:
            for spec, r in dns_results:
                spec["custom_domain"] = "{}.{}".format(
                    EDGE_DNS_RECORD_FORMAT.format(job_name=spec["edge_location"], region_name=conf['region_name']),
                    conf['dns_domain']
                )
                spec["edge_location"] = None
                dns_added_routes.append(r)
        else:
            for res in dns_results:
                try:
                    spec = res.get()
                    if spec:
                        dns_added_routes.append(spec["edge_location_path"].replace(".inc", ""))
                except Exception as e:
                    do_log("DNS creation failed: {}".format(e))
        
        do_log("Creating {} routes for dns endpoints...".format(len(dns_added_routes)))
        for r in dns_add:
            spec = services[r]
            hostname = spec["custom_domain"]
            self.nginx.write_route_config(spec, hostname, True)
            url = self._build_svc_url(spec, hostname, conf['region_id'])
            run_id = spec['run_id']
            svc_url_dict[run_id] = (svc_url_dict[run_id] + ',\n' + url) if run_id in svc_url_dict else url
        return svc_url_dict
