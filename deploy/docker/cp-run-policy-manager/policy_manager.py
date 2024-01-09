# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import ipaddress
import os
import re
import traceback
import uuid

import datetime
import requests
import time
import urllib3
import yaml
from kubernetes import client, config

NAMESPACE = 'default'
CALICO_NETPOL_PLURAL = 'networkpolicies'
CALICO_RESOURCES_VERSION = 'v1'
CALICO_RESOURCES_GROUP = 'crd.projectcalico.org'
K8S_OBJ_NAME_KEY = 'name'
K8S_LABELS_KEY = 'labels'
K8S_METADATA_KEY = 'metadata'
K8S_METADATA_NAME_FIELD_SELECTOR = 'metadata.name={}'
K8S_SPEC_KEY = 'spec'
K8S_EGRESS_KEY = 'egress'
NETPOL_OWNER_PLACEHOLDER = '<OWNER>'
NETPOL_NAME_PREFIX_PLACEHOLDER = '<POLICY_NAME_PREFIX>'
OWNER_LABEL = 'owner'
PIPELINE_POD_LABEL_SELECTOR = 'type=pipeline'
PIPELINE_POD_PHASE_SELECTOR = 'status.phase={}'
SENSITIVE_LABEL = 'sensitive'
POLICY_TYPE_COMMON = 'common'
POLICY_TYPE_ISOLATE = 'isolate'
POLICY_TYPE_SENSITIVE = 'sensitive'
TRACKED_POD_PHASES = ['Pending', 'Running']

COMMON_NETPOL_TEMPLATE_PATH = os.getenv('CP_RUN_POLICY_MANAGER_COMMON_POLICY_PATH',
                                        '/policy-manager/templates/common-run-policy-template.yaml')
SENSITIVE_NETPOL_TEMPLATE_PATH = os.getenv('CP_RUN_POLICY_MANAGER_SENSITIVE_POLICY_PATH',
                                           '/policy-manager/templates/sensitive-run-policy-template.yaml')
ISOLATE_NETPOL_TEMPLATE_PATH = os.getenv('CP_RUN_POLICY_MANAGER_ISOLATE_POLICY_PATH',
                                         '/policy-manager/templates/isolate-run-policy-template.yaml')
MONITORING_PERIOD_SEC = int(os.getenv('CP_RUN_POLICY_MANAGER_POLL_PERIOD_SEC', 5))

def log_message(message):
    print('[{}] {}'.format(datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S'), message))

# todo: Replace with pipe common usage
def cp_get(api_method, access_key=None):
    urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
    access_key = access_key or os.getenv('CP_API_JWT_ADMIN')
    api_host = os.getenv('CP_API_SRV_INTERNAL_HOST')
    api_port = os.getenv('CP_API_SRV_INTERNAL_PORT')
    if not access_key or not api_host or not api_port:
        log_message('CP_API_JWT_ADMIN or API internal host/port is not set, Cloud Pipeline API call cancelled')
        return None
    api_url = 'https://{}:{}/pipeline/restapi/{}'.format(api_host, api_port, api_method)
    try:
        response = requests.get(api_url, verify=False, headers={'Authorization': 'Bearer {}'.format(access_key)}).json()
        if 'payload' in response:
            return response['payload']
        else:
            return None
    except Exception:
        log_message('[ERROR] An error occurred while calling Cloud Pipeline API:\n{}'
                    .format(str(traceback.format_exc())))
        return None

def get_permissive_roles_ids():
    role_names = os.getenv('CP_RUN_POLICY_MANAGER_PERMISSIVE_ROLES', 'ROLE_ALLOW_ALL_POLICY')
    role_names = role_names.split(',')
    if len(role_names) == 0:
        return []
    
    roles_list = cp_get('role/loadAll?loadUsers=false')
    if not roles_list:
        log_message('Empty response for the roles listing request')
        return []

    return [x for x in roles_list if x['name'] in role_names]

def get_permissive_users():
    users_list = set()
    for role in get_permissive_roles_ids():
        role_details = cp_get('role/{}'.format(role['id']))
        if not role_details:
            continue
        if 'users' in role_details:
            users_list.update([x['userName'] for x in role_details['users']])
    return list(users_list)

def permissive_checks_enabled():
    return os.getenv('CP_RUN_POLICY_MANAGER_PERMISSIVE_ENABLED', 'false') == 'true'

def get_custom_resource_api():
    config.load_kube_config()
    return client.CustomObjectsApi()


def load_all_active_policies():
    api = get_custom_resource_api()
    policies_response = api.list_namespaced_custom_object(group=CALICO_RESOURCES_GROUP,
                                                          version=CALICO_RESOURCES_VERSION,
                                                          namespace=NAMESPACE,
                                                          plural=CALICO_NETPOL_PLURAL)
    return policies_response['items']


def create_policy(owner, policy_type):
    log_message('Creating policy [{}-{}]...'.format(owner, policy_type))
    api = get_custom_resource_api()
    policy_yaml = create_policy_yaml_object(owner, policy_type)
    policy_name_template = policy_yaml[K8S_METADATA_KEY][K8S_OBJ_NAME_KEY]
    sanitized_owner_name = sanitize_name(owner)
    policy_name_candidate = policy_name_template.replace(NETPOL_NAME_PREFIX_PLACEHOLDER, sanitized_owner_name)
    while True:
        existing_policy_response = api.list_namespaced_custom_object(group=CALICO_RESOURCES_GROUP,
                                                                     version=CALICO_RESOURCES_VERSION,
                                                                     namespace=NAMESPACE,
                                                                     plural=CALICO_NETPOL_PLURAL,
                                                                     field_selector=K8S_METADATA_NAME_FIELD_SELECTOR
                                                                     .format(policy_name_candidate))
        if len(existing_policy_response.get('items')) > 0:
            log_message('Policy with name [{}] exists already: generating suffix for the current one.'
                        .format(policy_name_candidate))
            policy_name_candidate = policy_name_template.replace(NETPOL_NAME_PREFIX_PLACEHOLDER,
                                                                 sanitized_owner_name + '-' + str(uuid.uuid4())[:8])
        else:
            policy_yaml[K8S_METADATA_KEY][K8S_OBJ_NAME_KEY] = policy_name_candidate
            api.create_namespaced_custom_object(group=CALICO_RESOURCES_GROUP,
                                                version=CALICO_RESOURCES_VERSION,
                                                namespace=NAMESPACE,
                                                plural=CALICO_NETPOL_PLURAL,
                                                body=policy_yaml)
            log_message('Policy [{}] created successfully'.format(policy_name_candidate))
            break


def sanitize_name(name: str):
    return re.sub('[^A-Za-z0-9]+', '-', name).lower()


def create_policy_yaml_object(owner, policy_type):
    if policy_type == POLICY_TYPE_SENSITIVE:
        policy_template_path = SENSITIVE_NETPOL_TEMPLATE_PATH
    elif policy_type == POLICY_TYPE_ISOLATE:
        policy_template_path = ISOLATE_NETPOL_TEMPLATE_PATH
    else:
        policy_template_path = COMMON_NETPOL_TEMPLATE_PATH
    with open(policy_template_path, 'r') as file:
        new_policy_as_string = file.read()
        new_policy_as_string = new_policy_as_string.replace(NETPOL_OWNER_PLACEHOLDER, owner)
    if not new_policy_as_string:
        return None
    policy_yaml = yaml.load(new_policy_as_string, Loader=yaml.FullLoader)
    if policy_type in [POLICY_TYPE_ISOLATE, POLICY_TYPE_SENSITIVE]:
        owner_file_share_ips = get_available_file_share_ips(owner)
        policy_yaml[K8S_SPEC_KEY][K8S_EGRESS_KEY].append({
            'action': 'Allow',
            'destination': {
                'nets': [ip + '/32' for ip in owner_file_share_ips]
            }
        })
    return policy_yaml


def get_user_token(user_name):
    log_message('Collecting token for {}...'.format(user_name))
    token_wrapper = cp_get('user/token?name=' + user_name) or {}
    return token_wrapper.get('token')


def get_available_file_share_ips(user_name):
    user_token = get_user_token(user_name)
    if not user_token:
        log_message('Access token has not been found for {} '.format(user_name))
        return
    log_message('Collecting available file share ips for {}...'.format(user_name))
    for file_share in get_available_file_shares(user_token):
        file_share_mount_root = file_share.get('mountRoot') or ''
        file_share_ip = file_share_mount_root.split(':')[0]
        if not file_share_ip:
            log_message('Skipping empty file share ip...')
            continue
        if not is_valid_ip_address(file_share_ip):
            log_message('Skipping invalid file share ip {}...'.format(file_share_ip))
            continue
        log_message('Collected file share ip {}...'.format(file_share_ip))
        yield file_share_ip


def get_available_file_shares(token):
    storages_with_file_shares = cp_get('datastorage/availableWithMounts', access_key=token) or []
    for storage_with_file_share in storages_with_file_shares:
        file_share = storage_with_file_share.get('shareMount', {})
        if file_share:
            yield file_share


def is_valid_ip_address(ip):
    try:
        ipaddress.ip_address(ip)
        return True
    except ValueError:
        return False


def delete_policy(policy_name):
    log_message('Deleting policy [{}]...'.format(policy_name))
    api = get_custom_resource_api()
    api.delete_namespaced_custom_object(group=CALICO_RESOURCES_GROUP,
                                        version=CALICO_RESOURCES_VERSION,
                                        namespace=NAMESPACE,
                                        plural=CALICO_NETPOL_PLURAL,
                                        name=policy_name)
    log_message('Policy [{}] deleted successfully'.format(policy_name))


def load_all_pipeline_pods():
    config.load_kube_config()
    active_tracked_pods = []
    for phase in TRACKED_POD_PHASES:
        pods_response = client.CoreV1Api().list_namespaced_pod(namespace=NAMESPACE,
                                                               label_selector=PIPELINE_POD_LABEL_SELECTOR,
                                                               field_selector=PIPELINE_POD_PHASE_SELECTOR.format(phase))
        active_tracked_pods.extend(pods_response.items)
    return active_tracked_pods


def is_sensitive_policy(policy):
    return policy[K8S_METADATA_KEY][K8S_LABELS_KEY].get('network_policy_type') == POLICY_TYPE_SENSITIVE \
        or SENSITIVE_LABEL in policy[K8S_METADATA_KEY][K8S_LABELS_KEY]


def is_sensitive_pod(pod):
    return pod.metadata.labels.get('network_policy_type') == POLICY_TYPE_SENSITIVE \
        or SENSITIVE_LABEL in pod.metadata.labels


def is_isolate_policy(policy):
    return policy[K8S_METADATA_KEY][K8S_LABELS_KEY].get('network_policy_type') == POLICY_TYPE_ISOLATE


def is_isolate_pod(pod):
    return pod.metadata.labels.get('network_policy_type') == POLICY_TYPE_ISOLATE


def get_pods_owners_set(pods):
    owners = set()
    for pod in pods:
        pod_owner = pod.metadata.labels.get(OWNER_LABEL)
        if pod_owner:
            owners.add(pod_owner)
    return owners


def get_policies_owners_set(policies):
    owners = set()
    for policy in policies:
        owners.add(policy[K8S_METADATA_KEY][K8S_LABELS_KEY][OWNER_LABEL])
    return owners


def create_missed_policies(active_pods, active_policies, policy_type):
    pods_owners = get_pods_owners_set(active_pods)
    users_affected_by_policies = get_policies_owners_set(active_policies)
    for owner in pods_owners:
        if owner not in users_affected_by_policies:
            try:
                create_policy(owner, policy_type)
            except Exception:
                log_message('[ERROR] Error occurred while CREATING policy [{}-{}]:\n{}'
                            .format(owner, policy_type, traceback.format_exc()))


def drop_excess_policies(active_pods, active_policies):
    pods_owners = get_pods_owners_set(active_pods)
    for policy in active_policies:
        policy_metadata = policy[K8S_METADATA_KEY]
        user_affected_by_policy = policy_metadata[K8S_LABELS_KEY][OWNER_LABEL]
        if user_affected_by_policy not in pods_owners:
            try:
                delete_policy(policy_metadata[K8S_OBJ_NAME_KEY])
            except Exception:
                log_message('[ERROR] Error occurred while DELETING policy [{}]:\n{}'
                            .format(policy_metadata[K8S_OBJ_NAME_KEY], traceback.format_exc()))


def main():
    log_message('===Starting run policies monitoring===')
    while True:
        try:
            permissive_users = []
            if permissive_checks_enabled():
                try:
                    permissive_users = get_permissive_users()
                except Exception:
                    log_message('[ERROR] Error occurred while getting a list of permissive users:\n{}'
                                .format(traceback.format_exc()))

            active_policies = load_all_active_policies()
            sensitive_policies = []
            isolate_policies = []
            common_policies = []
            for policy in active_policies:
                if is_sensitive_policy(policy):
                    sensitive_policies.append(policy)
                elif is_isolate_policy(policy):
                    isolate_policies.append(policy)
                else:
                    common_policies.append(policy)

            active_pods = load_all_pipeline_pods()
            sensitive_pods = []
            isolate_pods = []
            common_pods = []
            for pod in active_pods:
                pod_owner = pod.metadata.labels.get(OWNER_LABEL)
                if not is_sensitive_pod(pod) and pod_owner in permissive_users:
                    continue
                if is_sensitive_pod(pod):
                    sensitive_pods.append(pod)
                elif is_isolate_pod(pod):
                    isolate_pods.append(pod)
                else:
                    common_pods.append(pod)

            try:
                create_missed_policies(common_pods, common_policies, POLICY_TYPE_COMMON)
                create_missed_policies(isolate_pods, isolate_policies, POLICY_TYPE_ISOLATE)
                create_missed_policies(sensitive_pods, sensitive_policies, POLICY_TYPE_SENSITIVE)
            except Exception:
                log_message('[ERROR] Error occurred while CREATING new policies:\n{}'
                            .format(traceback.format_exc()))

            # todo: Update existing policies

            try:
                drop_excess_policies(common_pods, common_policies)
                drop_excess_policies(isolate_pods, isolate_policies)
                drop_excess_policies(sensitive_pods, sensitive_policies)
            except Exception:
                log_message('[ERROR] Error occurred while DELETING policies:\n{}'
                            .format(traceback.format_exc()))
            
        except Exception:
            log_message('[ERROR] General error occurred:\n{}'
                        .format(traceback.format_exc()))

        time.sleep(float(MONITORING_PERIOD_SEC))


if __name__ == '__main__':
    main()
