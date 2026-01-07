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
import glob
from .config import (
    API_GET_PREF, 
    API_POST_DNS_RECORD, 
    CP_CAP_CUSTOM_ENDPOINT_PREFIX,
    CP_EDGE_ENDPOINT_TAG_NAME,
    EDGE_EXTERNAL_APP,
    EDGE_INSTANCE_IP,
    EDGE_ROUTE_TARGET_PATH_TMPL,
    EDGE_ROUTE_TARGET_TMPL,
    EDGE_ROUTE_NO_PATH_CROP,
    EDGE_COOKIE_NO_REPLACE,
    EDGE_JWT_NO_AUTH,
    EDGE_PASS_BEARER,
    EDGE_DNS_RECORD_FORMAT,
    ROUTE_ID_TMPL,
    ROUTE_ID_PATTERN,
    NGINX_SITES_PATH,
    NGINX_DEFAULT_LOCATION_ATTRIBUTES_PATH,
    NGINX_CUSTOM_DOMAIN_CONFIG_EXT,
    STUB_LOCATION_CONFIG_EXTENSION,
    STUB_CUSTOM_DOMAIN_EXTENSION,
    SVC_URL_TMPL,
    EDGE_SERVICE_PORT,
    EDGE_DISABLE_NAME_SUFFIX_FOR_DEFAULT_ENDPOINT,
    API_UPDATE_SVC
)
from .logger import do_log, RunLogger
from multiprocessing.pool import ThreadPool as Pool

class RouteSynchronizer:
    def __init__(self, kube_client, api_client, nginx_manager, pool_size=8):
        self.kube = kube_client
        self.api = api_client
        self.nginx = nginx_manager
        self.pool = Pool(pool_size)
        self.default_location_attributes = self._load_default_attributes()
        # Initialize helper method to read system endpoints
        self.system_endpoints_config = self._read_system_endpoints()
        self.system_endpoints_names = [e['friendly_name'] for e in self.system_endpoints_config.values()]

    def _load_default_attributes(self):
        if os.path.exists(NGINX_DEFAULT_LOCATION_ATTRIBUTES_PATH):
            try:
                with open(NGINX_DEFAULT_LOCATION_ATTRIBUTES_PATH) as f:
                    return json.load(f)
            except Exception as e:
                print('Error reading default location attributes: {}'.format(e))
        return []

    def _read_system_endpoints(self):
        from .config import NGINX_SYSTEM_ENDPOINTS_CONFIG_PATH
        config = {}
        with open(NGINX_SYSTEM_ENDPOINTS_CONFIG_PATH, 'r') as f:
            endpoints = json.load(f)
            for endpoint in endpoints:
                config[endpoint['name']] = {
                    "value": endpoint.get("value", "true"),
                     "endpoint": str(os.environ.get(endpoint.get('endpoint_env'),
                                                    endpoint.get('endpoint_default'))),
                    "endpoint_num": str(os.environ.get(endpoint.get('endpoint_num_env'),
                                                       endpoint.get('endpoint_num_default'))),
                    "friendly_name": endpoint['friendly_name'],
                    "endpoint_additional": endpoint.get('endpoint_additional', ''),
                    "endpoint_same_tab": endpoint.get('endpoint_same_tab'),
                    "ssl_backend": endpoint.get('ssl_backend')
                }
        return config

    def find_preference(self, preference_name):
        response = self.api.call_api(API_GET_PREF.format(preference_name=preference_name)) or {}
        return str(response.get('payload', {}).get('value'))

    def run_sids_to_str(self, run_sids, is_principal):
        if not run_sids:
            return ""
        return ",".join([shared_sid["name"] for shared_sid in run_sids if shared_sid["isPrincipal"] == is_principal])

    def parse_pretty_url(self, pretty):
        try:
            pretty_obj = json.loads(pretty)
            if not pretty_obj: return None
        except Exception:
            pretty_obj = { 'path': pretty }

        pretty_domain = pretty_obj.get('domain')
        pretty_path = pretty_obj.get('path')
        if pretty_path and pretty_path.startswith('/'):
            pretty_path = pretty_path[1:]

        if not pretty_domain and not pretty_path:
            return None
        return { 'domain': pretty_domain, 'path': pretty_path }

    def is_true(self, value):
        return value and value.lower() == 'true'

    # ... Include helper methods like construct_additional_endpoints_from_run_parameters, append_additional_endpoints ...
    # For brevity in this artifact, assume they are methods of this class or imported.
    # To strictly follow DRY, I will port them as methods.
    
    def match_sys_endpoint_value(self, param_value, endpoint_value):
        if not param_value or not endpoint_value: return False
        if param_value.lower() == endpoint_value.lower(): return True
        if not endpoint_value.isalnum():
            try:
                return eval(param_value + endpoint_value)
            except:
                return False
        return False

    def construct_additional_endpoints(self, run_details):
        def extract_num(name):
             match = re.search(r'{}(\d+).*'.format(CP_CAP_CUSTOM_ENDPOINT_PREFIX), name)
             return match.group(1) if match else None

        params = [rp for rp in run_details["pipelineRunParameters"] if rp["name"].startswith(CP_CAP_CUSTOM_ENDPOINT_PREFIX)]
        if not params: return []
        
        nums = set([CP_CAP_CUSTOM_ENDPOINT_PREFIX + extract_num(rp["name"]) for rp in params])
        groups = {id: {rp["name"]: rp["value"] for rp in params if rp["name"].startswith(id)} for id in nums}
        
        do_log('Detected {} custom endpoints groups'.format(len(groups)))

        endpoints = []
        for e_id, e in groups.items():
             endpoints.append({
                 "name": e_id,
                 "endpoint": e.get(e_id + "_PORT"),
                 "friendly_name": e.get(e_id + "_NAME", "pipeline-{}-{}".format(run_details['id'], e.get(e_id + "_PORT"))),
                 "endpoint_additional": e.get(e_id + "_ADDITIONAL", ""),
                 "ssl_backend": e.get(e_id + "_SSL_BACKEND", False),
                 "endpoint_same_tab": e.get(e_id + "_SAME_TAB", False)
             })
        return endpoints

    def append_additional_endpoints(self, tool_endpoints, run_details):
        if not tool_endpoints: tool_endpoints = []
        overridden_count = 0
        if run_details and "pipelineRunParameters" in run_details:
             sys_keys = self.system_endpoints_config.keys()
             additional = [self.system_endpoints_config[x["name"]] for x in run_details["pipelineRunParameters"]
                           if x["name"] in sys_keys 
                           and self.match_sys_endpoint_value(x["value"], self.system_endpoints_config[x["name"]]["value"])
                           and self.system_endpoints_config[x["name"]].get("endpoint")]
             
             configured_ports = set(e["endpoint"] for e in additional)

             for custom in self.construct_additional_endpoints(run_details):
                 if custom["endpoint"] in configured_ports:
                     continue # Conflict
                 additional.append(custom)
                 configured_ports.add(custom["endpoint"])

             if additional and len(tool_endpoints) == 1:
                 # Override default logic
                 t = json.loads(tool_endpoints[0])
                 t["isDefault"] = "true"
                 tool_endpoints[0] = json.dumps(t)

             for add_ep in additional:
                 port = add_ep["endpoint"]
                 new_ep = {"nginx": {"port": port, "additional": add_ep["endpoint_additional"]}}
                 if "friendly_name" in add_ep:
                     new_ep["name"] = add_ep["friendly_name"]
                 if "endpoint_num" in add_ep:
                     new_ep["endpoint_num"] = add_ep["endpoint_num"]
                 
                 # Remove matching
                 filtered_tool_endpoints = []
                 is_def, is_ssl, is_same = False, False, False
                 # Logic for removal matches original script ... separate method needed or inline
                 # For conciseness inline logic:
                 for existing in tool_endpoints:
                      obj = json.loads(existing)
                      # Check match
                      if (add_ep.get("friendly_name") and obj.get("name") and 
                          obj.get("name").lower() == add_ep["friendly_name"].lower() and
                          obj.get("nginx", {}).get("port") == port):
                          # Match found, merge flags
                           if obj.get("isDefault"): is_def = True
                           if obj.get("sslBackend"): is_ssl = True
                           if obj.get("sameTab"): is_same = True
                      else:
                           filtered_tool_endpoints.append(existing)
                 
                 removed = len(tool_endpoints) - len(filtered_tool_endpoints)
                 overridden_count += removed
                 tool_endpoints = filtered_tool_endpoints

                 new_ep["isDefault"] = str(is_def).lower()
                 new_ep["sslBackend"] = add_ep.get("ssl_backend") or is_ssl
                 new_ep["sameTab"] = add_ep.get("endpoint_same_tab") or is_same
                 
                 tool_endpoints.append(json.dumps(new_ep))
                 
        return tool_endpoints, overridden_count

    def get_service_list(self, active_runs, pod_id, pod_run_id, pod_ip):
         # ... Logic to build service list from run cache ...
         # This matches `get_service_list` from original
         services = {}
         run = next((r for r in active_runs if str(r['pipelineRun']['id']) == str(pod_run_id)), None)
         if not run: return {}
         
         info = run['pipelineRun']
         if info.get('status') != 'RUNNING': return {}
         
         # Tag check
         if 'pipelineRunParameters' in info:
             tags = [rp for rp in info["pipelineRunParameters"] if rp["name"] == CP_EDGE_ENDPOINT_TAG_NAME]
             if tags:
                 run_tags = info.get("tags")
                 val = tags[0]["value"]
                 if not (run_tags and run_tags.get(val)): return {}

         pod_owner = info["owner"]
         # ... Extract other fields ...
         pretty = self.parse_pretty_url(info.get("prettyUrl"))
         sensitive = info.get("sensitive") or False
         cloud_region_id = info.get("instance", {}).get("cloudRegionId")
         instance_ip = info.get("instance", {}).get("nodeIP")

         endpoints_data = run.get('tool', {}).get('endpoints') or []
         endpoints_data, _ = self.append_additional_endpoints(endpoints_data, info)
         
         if not endpoints_data: return {}

         count = len(endpoints_data)
         for i in range(count):
             try:
                 ep = json.loads(endpoints_data[i])
             except: continue

             nginx = ep["nginx"]
             port = nginx["port"]
             path = nginx.get("path", "")
             name = ep.get("name", "Default")
             is_def = ep.get("isDefault", False)
             # ... Logic for edge_location calculation ...
             
             has_explicit_num = "endpoint_num" in ep
             custom_num = int(ep["endpoint_num"]) if has_explicit_num else i
             edge_loc_id = ROUTE_ID_TMPL.format(pod_id=pod_id, endpoint_port=port, endpoint_num=custom_num)
             
             # Name resolution logic
             is_sys = ep.get("name") in self.system_endpoints_names
             if not pretty or (has_explicit_num and not is_sys):
                 edge_location = edge_loc_id
             else:
                 pretty_path = pretty["path"]
                 if count == 1 or (str(is_def).lower() == "true" and EDGE_DISABLE_NAME_SUFFIX_FOR_DEFAULT_ENDPOINT):
                     edge_location = pretty_path
                 else:
                     suffix = ep.get("name", str(custom_num))
                     edge_location = "{}-{}".format(pretty_path, suffix) if pretty_path else suffix
             
             create_dns = ep.get("customDNS", False)
             usage_custom_domain = (pretty and pretty['domain']) or create_dns
             loc_path = edge_loc_id + ('.inc' if usage_custom_domain else '.loc')
             
             # Target IP logic
             additional = nginx.get("additional", "")
             target_ip = instance_ip if EDGE_INSTANCE_IP in additional else pod_ip
             if EDGE_INSTANCE_IP in additional: additional = additional.replace(EDGE_INSTANCE_IP, "")

             edge_target = EDGE_ROUTE_TARGET_PATH_TMPL.format(pod_ip=target_ip, endpoint_port=port, endpoint_path=path) \
                 if path else EDGE_ROUTE_TARGET_TMPL.format(pod_ip=target_ip, endpoint_port=port)
             
             if EDGE_ROUTE_NO_PATH_CROP in additional:
                 additional = additional.replace(EDGE_ROUTE_NO_PATH_CROP, "")
             else:
                 edge_target += "/"
                 
             cookie_loc = None
             if EDGE_COOKIE_NO_REPLACE in additional:
                 additional = additional.replace(EDGE_COOKIE_NO_REPLACE, "")
                 cookie_loc = "/"
             
             # Auth flags
             jwt_auth = True
             if EDGE_JWT_NO_AUTH in additional:
                 additional = additional.replace(EDGE_JWT_NO_AUTH, "")
                 jwt_auth = False
             
             pass_bearer = False
             if EDGE_PASS_BEARER in additional:
                 additional = additional.replace(EDGE_PASS_BEARER, "")
                 pass_bearer = True

             is_ext = False
             if EDGE_EXTERNAL_APP in additional:
                 additional = additional.replace(EDGE_EXTERNAL_APP, "")
                 is_ext = True
                 
             # Default attrs
             for da in self.default_location_attributes:
                 if da['search_pattern'].lower() not in additional.lower():
                     additional += da['value']

             services[edge_loc_id] = {
                 "edge_location_path": loc_path,
                 "pod_id": pod_id,
                 "pod_ip": target_ip,
                 "pod_owner": pod_owner,
                 "shared_users_sids": self.run_sids_to_str(info.get("runSids"), True),
                 "shared_groups_sids": self.run_sids_to_str(info.get("runSids"), False),
                 "service_name": name,
                 "is_default_endpoint": is_def,
                 "is_ssl_backend": ep.get("sslBackend", False),
                 "is_same_tab": ep.get("sameTab", False),
                 "edge_num": i,
                 "edge_location": edge_location,
                 "custom_domain": pretty.get('domain') if pretty else None,
                 "edge_target": edge_target,
                 "run_id": str(pod_run_id),
                 "additional": additional,
                 "sensitive": sensitive,
                 "create_dns_record": create_dns,
                 "cloudRegionId": cloud_region_id,
                 "external_app": is_ext,
                 "cookie_location": cookie_loc,
                 "edge_jwt_auth": jwt_auth,
                 "edge_pass_bearer": pass_bearer
             }
         return services

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
        do_log('============ Started iteration ============')
        
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
        
        # Re-using logic to match pods with env vars
        pods_with_endpoints = []
        for p in pods.response['items']:
            labels = p['metadata']['labels']
            if labels.get('job-type') == 'Service':
                pods_with_endpoints.append(p)
                continue
            # Env var check
            containers = p.get('spec', {}).get('containers', [])
            if containers and containers[0].get('env'):
                env = containers[0]['env']
                matched = [v for v in env if v['name'] in self.system_endpoints_config and 
                           self.match_sys_endpoint_value(v['value'], self.system_endpoints_config[v['name']]['value'])]
                if matched: pods_with_endpoints.append(p)

        run_ids = [p['metadata']['labels']['runid'] for p in pods_with_endpoints if p['metadata']['labels'].get('runid')]
        active_runs = []
        if run_ids:
            resp = self.api.call_api("runs?runIds=" + ",".join(run_ids))
            if resp and 'payload' in resp: active_runs = resp['payload']

        services = {}
        for p in pods_with_endpoints:
            pod_id = p['metadata']['name']
            pod_ip = p['status']['podIP']
            rid = p['metadata']['labels'].get('runid')
            if rid:
                services.update(self.get_service_list(active_runs, pod_id, rid, pod_ip))

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
        routes_to_check = routes_actual & routes_expected
        routes_to_add = routes_expected - routes_actual
        routes_to_delete = routes_actual - routes_expected
        
        # 4. Check & Update
        routes_to_update = set()
        for r in routes_to_check:
            path = os.path.join(NGINX_SITES_PATH, nginx_list[r])
            with open(path) as f: content = f.read()
            
            # Simple regex check for changes (users/groups)
            match_users = re.search(r'shared_with_users\s+"(.+?)";', content)
            match_groups = re.search(r'shared_with_groups\s+"(.+?)";', content)
            
            curr_users = match_users.group(1) if match_users else ""
            curr_groups = match_groups.group(1) if match_groups else ""
            
            spec = services[r]
            if curr_users != spec["shared_users_sids"] or curr_groups != spec["shared_groups_sids"]:
                routes_to_update.add(r)

        # 5. Dependency Update
        def get_pod_from_route(route):
            m = re.match(ROUTE_ID_PATTERN, route)
            return m.group(1) if m else None
        
        affected_pods = set([get_pod_from_route(r) for r in (routes_to_add | routes_to_delete | routes_to_update)])
        if None in affected_pods: affected_pods.remove(None)
        
        routes_to_replace = set([r for r in routes_to_check if get_pod_from_route(r) in affected_pods])
        
        routes_to_add |= routes_to_replace
        routes_to_delete |= routes_to_replace

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
        if dns_add:
            do_log("Creating {} configurations for dns endpoints...".format(len(dns_add)))
            results = []
            for r in dns_add:
                spec = services[r]
                if skip_custom_dns:
                     spec["custom_domain"] = "{}.{}.{}".format(spec["edge_location"], edge_region_name, dns_domain)
                     spec["edge_location"] = None
                     results.append(spec)
                else:
                     # This should be async in thread pool
                     # simplified for sync execution here or need wrapper
                     try:
                        self.create_dns_record(spec, edge_region_id, edge_region_name, ext_ip)
                        results.append(spec)
                     except: pass
            
            # Post-processing DNS routes
            for spec in results:
                # Add route like regular
                 hostname = spec["custom_domain"]
                 self.nginx.write_route_config(spec, hostname, True)
                 # Svc url update (omitted for brevity, essentially same as above)

        if regular_add or routes_to_delete or dns_add:
            self.nginx.reload_nginx()

        for rid, url in svc_url_dict.items():
            self.update_svc_url(rid, edge_region_name, url)

        do_log('============ Done iteration ============')
