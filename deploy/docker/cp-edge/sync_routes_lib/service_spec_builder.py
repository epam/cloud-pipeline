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
    ROUTE_ID_TMPL,
    EDGE_DISABLE_NAME_SUFFIX_FOR_DEFAULT_ENDPOINT,
    NGINX_SYSTEM_ENDPOINTS_CONFIG_PATH,
    NGINX_DEFAULT_LOCATION_ATTRIBUTES_PATH
)
from .logger import do_log

class ServiceSpecBuilder:
    def __init__(self, api_client):
        self.api = api_client
        self.system_endpoints_config = self._read_system_endpoints()
        self.system_endpoints_names = [e['friendly_name'] for e in self.system_endpoints_config.values()]
        self.default_location_attributes = self._load_default_attributes()

    def _load_default_attributes(self):
        if os.path.exists(NGINX_DEFAULT_LOCATION_ATTRIBUTES_PATH):
            try:
                with open(NGINX_DEFAULT_LOCATION_ATTRIBUTES_PATH) as f:
                    return json.load(f)
            except Exception as e:
                print('Error reading default location attributes: {}'.format(e))
        return []

    def _read_system_endpoints(self):
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

    def match_sys_endpoint_value(self, param_value, endpoint_value):
        if not param_value or not endpoint_value: return False
        if param_value.lower() == endpoint_value.lower(): return True
        if not endpoint_value.isalnum():
            # This way we can set envpoint value to boolean expressions, e.g. ">0"
            try:
                return eval(param_value + endpoint_value)
            except:
                return False
        return False

    def construct_additional_endpoints(self, run_details):
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
        # Method will group such parametes by <num> and construct from such group an endpoint. Also find the place that those commands are fit
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
             # Get a list of endpoints from SYSTEM_ENDPOINTS which match the run's parameters (param name and a value)
             additional = [self.system_endpoints_config[x["name"]] for x in run_details["pipelineRunParameters"]
                           if x["name"] in sys_keys 
                           and self.match_sys_endpoint_value(x["value"], self.system_endpoints_config[x["name"]]["value"])
                           and self.system_endpoints_config[x["name"]].get("endpoint")]
             
             configured_ports = set(e["endpoint"] for e in additional)

             # Append additional custom endpoint that are configured with run parameters
             for custom in self.construct_additional_endpoints(run_details):
                 # Filter out any endpoint if it matches with system ones
                 if custom["endpoint"] in configured_ports:
                     continue
                 additional.append(custom)
                 configured_ports.add(custom["endpoint"])

             if additional and len(tool_endpoints) == 1:
                 # If only a single endpoint is defined for the tool - we shall make sure it is set to default. 
                 # Otherwise "system endpoint" may become a default one. 
                 # If more then one endpoint is defined - we shall not make the changes, as it is up to the owner of the tool
                 t = json.loads(tool_endpoints[0])
                 t["isDefault"] = "true"
                 tool_endpoints[0] = json.dumps(t)

             # Append additional endpoints to the existing list
             for add_ep in additional:
                 port = add_ep["endpoint"]
                 new_ep = {"nginx": {"port": port, "additional": add_ep["endpoint_additional"]}}
                 if "friendly_name" in add_ep:
                     new_ep["name"] = add_ep["friendly_name"]
                 if "endpoint_num" in add_ep:
                     new_ep["endpoint_num"] = add_ep["endpoint_num"]

                 filtered_tool_endpoints = []
                 is_def, is_ssl, is_same = False, False, False
                 
                 for existing in tool_endpoints:
                      obj = json.loads(existing)
                      if (add_ep.get("friendly_name") and obj.get("name") and 
                          obj.get("name").lower() == add_ep["friendly_name"].lower() and
                          obj.get("nginx", {}).get("port") == port):
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
         services = {}
         run = next((r for r in active_runs if str(r['pipelineRun']['id']) == str(pod_run_id)), None)
         if not run: return {}
         
         info = run['pipelineRun']
         if info.get('status') != 'RUNNING': return {}

         if 'pipelineRunParameters' in info:
             tags = [rp for rp in info["pipelineRunParameters"] if rp["name"] == CP_EDGE_ENDPOINT_TAG_NAME]
             if tags:
                 run_tags = info.get("tags")
                 val = tags[0]["value"]
                 if not (run_tags and run_tags.get(val)): return {}

         pod_owner = info["owner"]
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
             
             has_explicit_num = "endpoint_num" in ep
             custom_num = int(ep["endpoint_num"]) if has_explicit_num else i
             edge_loc_id = ROUTE_ID_TMPL.format(pod_id=pod_id, endpoint_port=port, endpoint_num=custom_num)

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
