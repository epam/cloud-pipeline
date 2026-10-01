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
import glob
import re
import subprocess
import json
from .config import (
    NGINX_SITES_PATH,
    NGINX_ROOT_CONFIG_PATH,
    NGINX_DOMAINS_PATH,
    EXTERNAL_APPS_DOMAINS_PATH,
    API_DOMAIN_PATH,
    NGINX_CUSTOM_DOMAIN_CONFIG_EXT,
    NGINX_CUSTOM_DOMAIN_LOC_SUFFIX,
    NGINX_CUSTOM_DOMAIN_LOC_TMPL,
    NGINX_LOC_MODULE_TEMPLATE,
    NGINX_SRV_MODULE_TEMPLATE,
    NGINX_SENSITIVE_LOC_MODULE_TEMPLATE,
    NGINX_LOC_MODULE_STUB_TEMPLATE,
    NGINX_SENSITIVE_ROUTES_CONFIG_PATH,
    STUB_LOCATION_CONFIG_EXTENSION,
    STUB_CUSTOM_DOMAIN_EXTENSION,
    PKI_SEARCH_PATH,
    PKI_SEARCH_SUFFIX_CERT,
    PKI_SEARCH_SUFFIX_KEY,
    PKI_DEFAULT_CERT,
    PKI_DEFAULT_CERT_KEY,
    EDGE_BEARER_COOKIE_EXTRA
)
from .logger import do_log

class NginxManager:
    def __init__(self, api_domain_name):
        self.api_domain_name = api_domain_name
        self._load_templates()

    def _load_templates(self):
        with open(NGINX_LOC_MODULE_TEMPLATE, 'r') as f:
            self.loc_template = f.read()
        with open(NGINX_SENSITIVE_LOC_MODULE_TEMPLATE, 'r') as f:
            self.sensitive_loc_template = f.read()
        with open(NGINX_SENSITIVE_ROUTES_CONFIG_PATH, 'r') as f:
            self.sensitive_routes = json.load(f)
        with open(NGINX_LOC_MODULE_STUB_TEMPLATE, 'r') as f:
            self.stub_template = f.read()
        with open(NGINX_SRV_MODULE_TEMPLATE, 'r') as f:
            self.srv_template = f.read()

    def get_domain_config_path(self, domain, is_external_app=False):
        if domain == self.api_domain_name:
            return API_DOMAIN_PATH
        else:
            domains_path = EXTERNAL_APPS_DOMAINS_PATH if is_external_app else NGINX_DOMAINS_PATH
            return os.path.join(domains_path, domain + NGINX_CUSTOM_DOMAIN_CONFIG_EXT)

    def search_custom_domain_cert(self, domain):
        domain_cert_list = [f for f in glob.glob(PKI_SEARCH_PATH + '/*' + PKI_SEARCH_SUFFIX_CERT)]
        domain_cert_candidates = []
        for cert_path in domain_cert_list:
            cert_name = os.path.basename(cert_path).replace(PKI_SEARCH_SUFFIX_CERT, '')
            if domain.endswith(cert_name):
                domain_cert_candidates.append(cert_name)

        cert_path = None
        key_path = None
        if domain_cert_candidates:
            domain_cert_candidates.sort(key=len, reverse=True)
            cert_name = domain_cert_candidates[0]
            cert_path = os.path.join(PKI_SEARCH_PATH, cert_name + PKI_SEARCH_SUFFIX_CERT)
            key_path = os.path.join(PKI_SEARCH_PATH, cert_name + PKI_SEARCH_SUFFIX_KEY)
            if not os.path.isfile(key_path):
                do_log(f'-> Certificate for {domain} is found at {cert_path}, but a key does not exist at {key_path}')
                key_path = None
        
        if not cert_path or not key_path:
            cert_path = PKI_DEFAULT_CERT
            key_path = PKI_DEFAULT_CERT_KEY

        do_log(f'-> Certificate:Key for {domain} will be used: {cert_path}:{key_path}')
        return cert_path, key_path

    def add_custom_domain(self, domain, location_block, is_external_app=False):
        if not os.path.isdir(NGINX_DOMAINS_PATH):
            os.mkdir(NGINX_DOMAINS_PATH)
        domain_path = self.get_domain_config_path(domain, is_external_app=is_external_app)
        
        if os.path.exists(domain_path):
            do_log(f'-> Adding new location block to existing configuration file at {domain_path}')
            with open(domain_path, 'r') as f:
                content = f.read()
        else:
            do_log(f'-> Creating new custom domain configuration file at {domain_path}')
            domain_cert = self.search_custom_domain_cert(domain)
            content = self.srv_template \
                .replace('{edge_route_server_name}', domain) \
                .replace('{edge_route_server_ssl_certificate}', domain_cert[0]) \
                .replace('{edge_route_server_ssl_certificate_key}', domain_cert[1])
        
        location_block_include = NGINX_CUSTOM_DOMAIN_LOC_TMPL.format(location_block)
        lines = content.splitlines()

        # Check if the location_block already added to the domain config
        if any(location_block_include in line for line in lines):
            do_log(f'-> Location block {location_block} already exists for domain {domain}')
            return

        # If it's a new location entry - add it to the domain config after the {edge_route_location_block} line
        insert_indices = [i for i, line in enumerate(lines) if '# {edge_route_location_block}' in line]
        if not insert_indices:
             do_log(f'-> Cannot find an insert location in the domain config {domain_path}')
             return
        lines.insert(insert_indices[-1] + 1, location_block_include)

        # Save the domain config back to file
        with open(domain_path, 'w') as f:
            f.write('\n'.join(lines))

    def remove_custom_domain(self, domain, location_block, is_external_app=False):
        location_block_include = NGINX_CUSTOM_DOMAIN_LOC_TMPL.format(location_block)
        domain_path = self.get_domain_config_path(domain, is_external_app=is_external_app)
        if not os.path.exists(domain_path):
            return False
        
        with open(domain_path, 'r') as f:
            lines = f.read().splitlines()

        found_indices = [i for i, line in enumerate(lines) if location_block_include in line]
        if not found_indices:
            return False
        
        del lines[found_indices[-1]]

        # If no more location block exist in the domain - delete the config file
        # Do not delete if this is an "external application", where the server block is managed externally
        if (not is_external_app and domain_path != API_DOMAIN_PATH and 
            sum(NGINX_CUSTOM_DOMAIN_LOC_SUFFIX in line for line in lines) == 0):
             do_log(f'-> No more location blocks are available for {domain}, deleting the config file: {domain_path}')
             os.remove(domain_path)
        else:
             # Save the domain config back to file
             with open(domain_path, 'w') as f:
                 f.write('\n'.join(lines))
        return True

    def remove_custom_domain_all(self, location_block):
        if self.api_domain_name:
            if self.remove_custom_domain(self.api_domain_name, location_block, is_external_app=False):
                do_log(f'-> Removed {location_block} location block from the API domain config {API_DOMAIN_PATH}')
        
        for domains_root_path in [NGINX_DOMAINS_PATH, EXTERNAL_APPS_DOMAINS_PATH]:
             domain_path_list = [f for f in glob.glob(domains_root_path + '/*' + NGINX_CUSTOM_DOMAIN_CONFIG_EXT)]
             for domain_path in domain_path_list:
                 custom_domain = os.path.basename(domain_path).replace(NGINX_CUSTOM_DOMAIN_CONFIG_EXT, '')
                 is_external = (domains_root_path == EXTERNAL_APPS_DOMAINS_PATH)
                 if self.remove_custom_domain(custom_domain, location_block, is_external_app=is_external):
                     do_log(f'-> Removed {location_block} location block from {custom_domain} domain config')

    def check_nginx_config(self):
        try:
            subprocess.check_output(f'nginx -c {NGINX_ROOT_CONFIG_PATH} -t', shell=True)
            do_log('Adding new route ... OK')
            return True
        except subprocess.CalledProcessError as e:
            do_log(f'Adding new route ... NOT OK ({e.returncode})')
            return False

    def reload_nginx_config(self):
        do_log('Reloading nginx...')
        subprocess.check_output('nginx -s reload', shell=True)

    def write_route_config(self, service_spec, service_hostname, has_custom_domain):
        service_location = f'/{service_spec.edge_location}/' if service_spec.edge_location else "/"
        # Replace the duplicated forward slashes with a single instance to workaround possible issue when the location is set to "/path//"
        service_location = re.sub('/+', '/', service_location)

        schema = 'https' if service_spec.is_ssl_backend else 'http'
        
        nginx_route_definition = self.loc_template \
                .replace('{edge_route_location}', service_location) \
                .replace('{edge_route_target}', service_spec.edge_target) \
                .replace('{edge_route_owner}', service_spec.pod_owner) \
                .replace('{run_id}', service_spec.run_id) \
                .replace('{edge_route_shared_users}', service_spec.shared_users_sids) \
                .replace('{edge_route_shared_groups}', service_spec.shared_groups_sids) \
                .replace('{edge_route_schema}', schema) \
                .replace('{additional}', service_spec.additional) \
                .replace('{edge_jwt_auth}', str(service_spec.edge_jwt_auth)) \
                .replace('{edge_pass_bearer}', str(service_spec.edge_pass_bearer)) \
                .replace('{bearer_cookie_extra}', EDGE_BEARER_COOKIE_EXTRA) \
                .replace('{edge_cookie_location}', service_spec.cookie_location if service_spec.cookie_location else service_location)

        nginx_sensitive_route_definitions = []
        if service_spec.sensitive:
             for sensitive_route in self.sensitive_routes:
                # proxy_pass cannot have trailing slash for regexp locations
                 edge_target = service_spec.edge_target
                 if edge_target.endswith("/"):
                     edge_target = edge_target[:-1]
                 
                 nginx_sensitive_route_definition = self.sensitive_loc_template \
                                .replace('{edge_route_location}', service_location + sensitive_route['route']) \
                                .replace('{edge_route_sensitive_methods}', '|'.join(sensitive_route['methods'])) \
                                .replace('{edge_route_target}', edge_target) \
                                .replace('{edge_route_owner}', service_spec.pod_owner) \
                                .replace('{run_id}', service_spec.run_id) \
                                .replace('{edge_route_shared_users}', service_spec.shared_users_sids) \
                                .replace('{edge_route_shared_groups}', service_spec.shared_groups_sids) \
                                .replace('{additional}', service_spec.additional) \
                                .replace('{edge_cookie_location}', service_spec.cookie_location if service_spec.cookie_location else service_location + sensitive_route['route'])
                 nginx_sensitive_route_definitions.append(nginx_sensitive_route_definition)

        path_to_route = os.path.join(NGINX_SITES_PATH, service_spec.edge_location_path + '.conf')
        with open(path_to_route, "w") as added_route_file:
            added_route_file.write(nginx_route_definition)
            for nginx_sensitive_route_definition in nginx_sensitive_route_definitions:
                added_route_file.write(nginx_sensitive_route_definition)
        
        do_log(f'Adding new {"sensitive " if service_spec.sensitive else ""}route {path_to_route}')

        if has_custom_domain:
            do_log(f'Adding new route {path_to_route} to server block {service_hostname}')
            self.add_custom_domain(service_hostname, path_to_route, is_external_app=service_spec.external_app)

        return path_to_route, service_location

    def write_stub_location_configuration(self, path_to_route, service_location, service_spec, has_custom_domain):
        nginx_route_definition = self.stub_template \
                .replace('{edge_route_location}', service_location) \
                .replace('{edge_route_owner}', service_spec.pod_owner) \
                .replace('{edge_route_shared_users}', service_spec.shared_users_sids) \
                .replace('{edge_route_shared_groups}', service_spec.shared_groups_sids)
        
        # Determine the correct extensions based on custom domain usage
        path_to_route_extension = ".conf" if has_custom_domain else ".loc.conf"
        stub_extension = STUB_CUSTOM_DOMAIN_EXTENSION if has_custom_domain else STUB_LOCATION_CONFIG_EXTENSION
        path_to_stub = path_to_route.replace(path_to_route_extension, stub_extension)

        with open(path_to_stub, "w") as f:
            f.write(nginx_route_definition)
        do_log(f'Adding new stub route {path_to_stub}')
        return path_to_stub

    def check_route(self, path_to_route, service_location, service_spec, has_custom_domain, service_hostname):
        if self.check_nginx_config():
            return

        do_log('Deleting invalid route...')
        os.remove(path_to_route)
        if has_custom_domain:
             do_log('Deleting invalid custom domain route...')
             self.remove_custom_domain_all(path_to_route)

        path_to_stub = self.write_stub_location_configuration(path_to_route, service_location, service_spec, has_custom_domain)

        if self.check_nginx_config():
            if has_custom_domain:
                do_log(f'Adding new stub route {path_to_stub} to server block {service_hostname}')
                self.add_custom_domain(service_hostname, path_to_stub, is_external_app=service_spec.external_app)
            return

        do_log('Deleting invalid stub route...')
        os.remove(path_to_stub)

