# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

from datetime import datetime
import os
import json
import requests
import urllib3
import re
from subprocess import check_output
from time import sleep

NUMBER_OF_RETRIES = 10
SECS_TO_WAIT_BEFORE_RETRY = 15
DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"

MAINTENANCE_PREFERENCE_NAME = 'system.blocking.maintenance.mode'
DEFAULT_FLAG_FILE_PATH = '/etc/maintenance/active'
NGINX_CONF_FILE_PATH = '/etc/nginx/nginx.conf'
NGINX_DEFAULT_PORT = 8181
NGINX_MAINTENANCE_PORT = 8383
NGINX_REVERSE_PROXY_RE = r'(upstream reverse_proxy\s+{\s+server 127\.0\.0\.1:)([\d]+)(;)'

urllib3.disable_warnings()
api_url = os.environ.get('API')
api_token = os.environ.get('API_TOKEN')
if not api_url or not api_token:
    print('API url or API token are not set. Exiting')
    exit(1)
edge_service_external_schema = os.environ.get('EDGE_EXTERNAL_SCHEMA', 'https')

api_headers = {'Content-Type': 'application/json',
               'Authorization': 'Bearer {}'.format(api_token)}


def do_log(msg):
    print('[{}] {}'.format(datetime.now().strftime("%Y-%m-%d %H:%M:%S"), msg))


def call_api(method_url, data=None):
    result = None
    for n in range(NUMBER_OF_RETRIES):
        try:
            do_log('Calling API {}'.format(method_url))
            response = None
            if data:
                response = requests.post(method_url, verify=False, data=data, headers=api_headers)
            else:
                response = requests.get(method_url, verify=False, headers=api_headers)
            response_data = json.loads(response.text)
            if response_data['status'] == 'OK':
                do_log('API call status OK')
                result = response_data
            else:
                err_msg = 'No error message available'
                if 'message' in response_data:
                    err_msg = response_data['message']
                do_log('Error occurred while calling API ({})\n{}'.format(method_url, err_msg))
                do_log('As the API technically succeeded, it will not be retried')
            break
        except Exception as api_exception:
            do_log('Error occurred while calling API ({})\n{}'.format(method_url, str(api_exception)))

        if n < NUMBER_OF_RETRIES - 1:
            do_log('Sleep for {} sec and perform API call again ({}/{})'.format(SECS_TO_WAIT_BEFORE_RETRY, n + 2,
                                                                                NUMBER_OF_RETRIES))
            sleep(SECS_TO_WAIT_BEFORE_RETRY)
        else:
            do_log('All attempts failed. API call failed')
    return result


def find_preference(api_preference_query, preference_name):
    load_method = os.path.join(api_url, api_preference_query)
    response = call_api(load_method)
    if response and "payload" in response and "name" in response["payload"] \
        and response["payload"]["name"] == preference_name and "value" in response["payload"]:
        return response["payload"]["value"]
    return None


def create_flag_file(flag_file=DEFAULT_FLAG_FILE_PATH):
    parent = os.path.dirname(flag_file)
    if not os.path.exists(parent):
        try:
            os.makedirs(parent)
        except IOError as e:
            do_log(e.message)
            pass
    if not os.path.exists(flag_file):
        do_log("Creating the maintenance flag file")
        with open(flag_file, "w") as f:
            f.write("maintenance\n")
    return True


def remove_flag_file(flag_file=DEFAULT_FLAG_FILE_PATH):
    if os.path.exists(flag_file):
        do_log("Removing the maintenance flag file")
        os.remove(flag_file)
    return True


def correct_nginx_conf(maintenance=False):
    try:
        if os.path.exists(NGINX_CONF_FILE_PATH):
            nginx_conf = ''
            with open(NGINX_CONF_FILE_PATH, "r") as f:
                nginx_conf = f.read()
            port = NGINX_MAINTENANCE_PORT if maintenance else NGINX_DEFAULT_PORT
            skip_test = re.search(NGINX_REVERSE_PROXY_RE, nginx_conf, re.IGNORECASE)
            skip = skip_test.group(2) == str(port) if skip_test is not None else False
            if skip:
                return
            def replacer(match_obj):
                return match_obj.group(1) + str(port) + match_obj.group(3)
            nginx_conf = re.sub(NGINX_REVERSE_PROXY_RE, replacer, nginx_conf, count=0)
            do_log('Updating {}: setting reverse_proxy port to {}'.format(NGINX_CONF_FILE_PATH, str(port)))
            with open(NGINX_CONF_FILE_PATH, "w") as f:
                f.write(nginx_conf)
            check_output('nginx -s reload', shell=True)
    except IOError as e:
        do_log(e.message)
        pass

def check_maintenance_mode(flag_file=DEFAULT_FLAG_FILE_PATH):
    mode = find_preference('preferences/{}'.format(MAINTENANCE_PREFERENCE_NAME), MAINTENANCE_PREFERENCE_NAME) == 'true'
    if mode:
        create_flag_file(flag_file)
    else:
        remove_flag_file(flag_file)
    correct_nginx_conf(mode)
    return mode


check_maintenance_mode()
