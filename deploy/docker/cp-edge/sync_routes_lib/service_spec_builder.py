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
from .models import RouteSpec

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
                print(f'Error reading default location attributes: {e}')
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
        return ",".join([
            shared_sid["name"] for shared_sid in run_sids if shared_sid["isPrincipal"] == is_principal
            ])

    def parse_pretty_url(self, pretty):
        try:
            pretty_obj = json.loads(pretty)
            if not pretty_obj: 
                return None
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
    # Method will group such parameters by <num> and construct from such group an endpoint.
    def construct_additional_endpoints_from_run_parameters(self, run_details):
        def extract_endpoint_num_from_run_parameter(name):
             match = re.search(fr'{CP_CAP_CUSTOM_ENDPOINT_PREFIX}(\d+).*', name)
             return match.group(1) if match else None

        run_parameters = [rp for rp in run_details["pipelineRunParameters"] if rp["name"].startswith(CP_CAP_CUSTOM_ENDPOINT_PREFIX)]
        if not run_parameters: return []
        
        custom_endpoint_nums = set([CP_CAP_CUSTOM_ENDPOINT_PREFIX + extract_endpoint_num_from_run_parameter(rp["name"]) for rp in run_parameters])
        custom_endpoints_groups = {id: {rp["name"]: rp["value"] for rp in run_parameters if rp["name"].startswith(id)} for id in custom_endpoint_nums}
        
        do_log(f'Detected {len(custom_endpoints_groups)} custom endpoints groups')

        endpoints = []
        for e_id, e in custom_endpoints_groups.items():
             endpoints.append({
                 "name": e_id,
                 "endpoint": e.get(e_id + "_PORT"),
                 "friendly_name": e.get(e_id + "_NAME", f"pipeline-{run_details['id']}-{e.get(e_id + '_PORT')}"),
                 "endpoint_additional": e.get(e_id + "_ADDITIONAL", ""),
                 "ssl_backend": e.get(e_id + "_SSL_BACKEND", False),
                 "endpoint_same_tab": e.get(e_id + "_SAME_TAB", False)
             })
        return endpoints

    def append_additional_endpoints(self, tool_endpoints, run_details):
        # Append additional endpoints to the existing list
        if not tool_endpoints: 
            tool_endpoints = []
        
        if not (run_details and "pipelineRunParameters" in run_details):
            return tool_endpoints, 0

        sys_endpoints = self.system_endpoints_config
        # Get a list of endpoints from SYSTEM_ENDPOINTS which match the run's parameters (param name and a value)
        additional = []
        for param in run_details["pipelineRunParameters"]:
            name = param["name"]
            if name in sys_endpoints:
                conf = sys_endpoints[name]
                if (self.match_sys_endpoint_value(param["value"], conf["value"]) and 
                    conf.get("endpoint")):
                    additional.append(conf)

        configured_ports = {e["endpoint"] for e in additional}
        for custom in self.construct_additional_endpoints_from_run_parameters(run_details):
            if custom["endpoint"] not in configured_ports:
                additional.append(custom)
                configured_ports.add(custom["endpoint"])

        if additional and len(tool_endpoints) == 1:
            # If only a single endpoint is defined for the tool - we shall make sure it is set to default. Otherwise "system endpoint" may become a default one
            # If more then one endpoint is defined - we shall not make the changes, as it is up to the owner of the tool
            try:
                t = json.loads(tool_endpoints[0])
                t["isDefault"] = "true"
                tool_endpoints[0] = json.dumps(t)
            except json.JSONDecodeError:
                pass 

        overridden_count = 0

        for add_ep in additional:
            port = add_ep["endpoint"]
            friendly = add_ep.get("friendly_name")
            
            # Filter out any endpoint if it matches with system ones
            # Helper to check if an existing endpoint conflicts with the new one
            def is_conflict(existing_json):
                try:
                    obj = json.loads(existing_json)
                    return (friendly and obj.get("name") and 
                            obj.get("name").lower() == friendly.lower() and
                            obj.get("nginx", {}).get("port") == port)
                except:
                    return False

            conflicting = [e for e in tool_endpoints if is_conflict(e)]
            non_conflicting = [e for e in tool_endpoints if not is_conflict(e)]
            
            overridden_count += len(conflicting)
            tool_endpoints = non_conflicting

            is_def, is_ssl, is_same = False, False, False
            for c in conflicting:
                try:
                    obj = json.loads(c)
                    is_def = is_def or obj.get("isDefault", False)
                    is_ssl = is_ssl or obj.get("sslBackend", False)
                    is_same = is_same or obj.get("sameTab", False)
                except:
                    pass

            # Construct new endpoint JSON
            new_ep = {
                "nginx": {
                    "port": port, 
                    "additional": add_ep.get("endpoint_additional", "")
                },
                "isDefault": str(is_def).lower(),
                "sslBackend": add_ep.get("ssl_backend") or is_ssl,
                "sameTab": add_ep.get("endpoint_same_tab") or is_same
            }
            if friendly:
                new_ep["name"] = friendly
            if "endpoint_num" in add_ep:
                new_ep["endpoint_num"] = add_ep["endpoint_num"]

            tool_endpoints.append(json.dumps(new_ep))

        return tool_endpoints, overridden_count

    def get_service_list(self, runs_with_endpoints, pod_id, pod_run_id, pod_ip):
        services_list = {}
        run = next((r for r in runs_with_endpoints if str(r['pipelineRun']['id']) == str(pod_run_id)), None)
        if not run or run['pipelineRun'].get('status') != 'RUNNING':
            return {}
        
        info = run['pipelineRun']
        if not self._check_endpoint_tag_permission(info):
            return {}

        base_info = self._extract_base_info(info)
        endpoints_data, _ = self.append_additional_endpoints(run.get('tool', {}).get('endpoints') or [], info)
        if not endpoints_data:
            return {}

        count = len(endpoints_data)
        for i, ep_str in enumerate(endpoints_data):
            try:
                ep = json.loads(ep_str)
            except:
                continue
            
            spec_pair = self._build_route_spec(ep, i, count, pod_id, pod_ip, base_info)
            if spec_pair:
                edge_id, details = spec_pair
                services_list[edge_id] = details
        
        return services_list

    def _check_endpoint_tag_permission(self, info):
        if 'pipelineRunParameters' in info:
            tags = [rp for rp in info["pipelineRunParameters"] if rp["name"] == CP_EDGE_ENDPOINT_TAG_NAME]
            if tags:
                run_tags = info.get("tags")
                val = tags[0]["value"]
                if not (run_tags and run_tags.get(val)):
                    return False
        return True

    def _extract_base_info(self, info):
        return {
            'owner': info["owner"],
            'pretty': self.parse_pretty_url(info.get("prettyUrl")),
            'sensitive': info.get("sensitive") or False,
            'cloud_region_id': info.get("instance", {}).get("cloudRegionId"),
            'instance_ip': info.get("instance", {}).get("nodeIP"),
            'run_sids': info.get("runSids"),
            'run_id': str(info['id']),
            'info': info
        }

    def _build_route_spec(self, ep, index, count, pod_id, pod_ip, base):
        nginx = ep.get("nginx")
        if not nginx:
            return None
            
        port = nginx["port"]
        path = nginx.get("path", "")
        name = ep.get("name", "Default")
        is_def = ep.get("isDefault", False)
        
        has_explicit_num = "endpoint_num" in ep
        custom_num = int(ep["endpoint_num"]) if has_explicit_num else index
        edge_id = ROUTE_ID_TMPL.format(pod_id=pod_id, endpoint_port=port, endpoint_num=custom_num)

        is_sys = ep.get("name") in self.system_endpoints_names
        edge_location = self._resolve_edge_location(
            base['pretty'], is_def, is_sys, has_explicit_num, edge_id, name, custom_num, count
        )
        
        create_dns = ep.get("customDNS", False)
        usage_custom_domain = (base['pretty'] and base['pretty']['domain']) or create_dns
        loc_path = edge_id + ('.inc' if usage_custom_domain else '.loc')
        
        additional = nginx.get("additional", "")
        target_ip = base['instance_ip'] if EDGE_INSTANCE_IP in additional else pod_ip
        if EDGE_INSTANCE_IP in additional:
            additional = additional.replace(EDGE_INSTANCE_IP, "")

        edge_target = self._build_edge_target(target_ip, port, path, additional)
        
        flags = self._parse_additional_flags(additional)
        additional = flags['cleaned_additional']
        
        # Apply default attributes
        for da in self.default_location_attributes:
            if da['search_pattern'].lower() not in additional.lower():
                additional += da['value']

        spec = RouteSpec(
            edge_location_path=loc_path,
            pod_id=pod_id,
            pod_ip=target_ip,
            pod_owner=base['owner'],
            shared_users_sids=self.run_sids_to_str(base['run_sids'], True),
            shared_groups_sids=self.run_sids_to_str(base['run_sids'], False),
            service_name=name,
            is_default_endpoint=is_def,
            is_ssl_backend=ep.get("sslBackend", False),
            is_same_tab=ep.get("sameTab", False),
            edge_num=index,
            edge_location=edge_location,
            custom_domain=base['pretty'].get('domain') if base['pretty'] else None,
            edge_target=edge_target,
            run_id=base['run_id'],
            additional=additional,
            sensitive=base['sensitive'],
            create_dns_record=create_dns,
            cloudRegionId=base['cloud_region_id'],
            external_app=flags['external_app'],
            cookie_location=flags['cookie_location'],
            edge_jwt_auth=flags['jwt_auth'],
            edge_pass_bearer=flags['pass_bearer']
        )
        return edge_id, spec

    def _resolve_edge_location(self, pretty, is_def, is_sys, has_explicit_num, edge_id, name, custom_num, count):
        if not pretty or (has_explicit_num and not is_sys):
            return edge_id
        
        pretty_path = pretty["path"]
        if count == 1 or (str(is_def).lower() == "true" and EDGE_DISABLE_NAME_SUFFIX_FOR_DEFAULT_ENDPOINT):
             return pretty_path
        
        suffix = name if name else str(custom_num)
        return f"{pretty_path}-{suffix}" if pretty_path else suffix

    def _build_edge_target(self, target_ip, port, path, additional):
        target = EDGE_ROUTE_TARGET_PATH_TMPL.format(pod_ip=target_ip, endpoint_port=port, endpoint_path=path) \
            if path else EDGE_ROUTE_TARGET_TMPL.format(pod_ip=target_ip, endpoint_port=port)
        
        if EDGE_ROUTE_NO_PATH_CROP in additional:
            pass
        else:
            target += "/"
        return target

    def _parse_additional_flags(self, additional):
        flags = {
            'cookie_location': None,
            'jwt_auth': True,
            'pass_bearer': False,
            'external_app': False,
            'cleaned_additional': additional
        }
        
        # If CP_EDGE_NO_PATH_CROP is present (any place) in the "additional" section of the route config
        # then trailing "/" is not added to the proxy pass target. This will allow to forward original requests trailing path
        if EDGE_ROUTE_NO_PATH_CROP in flags['cleaned_additional']:
            flags['cleaned_additional'] = flags['cleaned_additional'].replace(EDGE_ROUTE_NO_PATH_CROP, "")

        if EDGE_COOKIE_NO_REPLACE in flags['cleaned_additional']:
            flags['cleaned_additional'] = flags['cleaned_additional'].replace(EDGE_COOKIE_NO_REPLACE, "")
            flags['cookie_location'] = "/"

        #######################################################
        # These parameters will be passed to the respective lua auth script
        # Only applied for the non-sensitive jobs    
        if EDGE_JWT_NO_AUTH in flags['cleaned_additional']:
            flags['cleaned_additional'] = flags['cleaned_additional'].replace(EDGE_JWT_NO_AUTH, "")
            flags['jwt_auth'] = False
            
        if EDGE_PASS_BEARER in flags['cleaned_additional']:
            flags['cleaned_additional'] = flags['cleaned_additional'].replace(EDGE_PASS_BEARER, "")
            flags['pass_bearer'] = True

        #######################################################    
            
        if EDGE_EXTERNAL_APP in flags['cleaned_additional']:
            flags['cleaned_additional'] = flags['cleaned_additional'].replace(EDGE_EXTERNAL_APP, "")
            flags['external_app'] = True
            
        return flags
