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

import datetime
import os
import re
import time
import yaml
from kubernetes import client, config

NAMESPACE = "default"
CALICO_NETPOL_PLURAL = "networkpolicies"
CALICO_RESOURCES_VERSION = "v1"
CALICO_RESOURCES_GROUP = "crd.projectcalico.org"
K8S_OBJ_NAME_KEY = 'name'
K8S_LABELS_KEY = 'labels'
K8S_METADATA_KEY = 'metadata'
NETPOL_OWNER_PLACEHOLDER = '<OWNER>'
NETPOL_NAME_PREFIX_PLACEHOLDER = '<POLICY_NAME_PREFIX>'
OWNER_LABEL = 'owner'
PIPELINE_POD_LABEL_SELECTOR = 'type=pipeline'
PIPELINE_POD_PHASE_SELECTOR = 'status.phase={}'
SENSITIVE_LABEL = 'sensitive'
TRACKED_POD_PHASES = ['Pending', 'Running']
COMMON_NETPOL_TEMPLATE_PATH = '/policy-manager/common-run-policy-template.yaml'
SENSITIVE_NETPOL_TEMPLATE_PATH = '/policy-manager/sensitive-run-policy-template.yaml'

MONITORING_PERIOD_SEC = int(os.getenv('RUN_OWNER_POLICY_POLL_PERIOD_SEC', 5))


def log_message(message):
    print('[{}] {}'.format(datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"), message))


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


def create_policy(owner, is_sensitive):
    log_message('Creating policy for [{}{}]'.format(owner, '-sensitive' if is_sensitive else ''))
    api = get_custom_resource_api()
    api.create_namespaced_custom_object(group=CALICO_RESOURCES_GROUP,
                                        version=CALICO_RESOURCES_VERSION,
                                        namespace=NAMESPACE,
                                        plural=CALICO_NETPOL_PLURAL,
                                        body=create_policy_yaml_object(owner, is_sensitive))


def sanitize_name(name: str):
    return re.sub("[^A-a0-9]+", "-", name).lower()


def create_policy_yaml_object(owner, is_sensitive):
    with open(SENSITIVE_NETPOL_TEMPLATE_PATH if is_sensitive else COMMON_NETPOL_TEMPLATE_PATH, 'r') as file:
        new_policy_as_string = file.read()
        new_policy_as_string = new_policy_as_string.replace(NETPOL_OWNER_PLACEHOLDER, owner)
        new_policy_as_string = new_policy_as_string.replace(NETPOL_NAME_PREFIX_PLACEHOLDER, sanitize_name(owner))
    return yaml.load(new_policy_as_string, Loader=yaml.FullLoader) if new_policy_as_string else None


def delete_policy(policy_name):
    log_message('Removing policy [{}]'.format(policy_name))
    api = get_custom_resource_api()
    api.delete_namespaced_custom_object(group=CALICO_RESOURCES_GROUP,
                                        version=CALICO_RESOURCES_VERSION,
                                        namespace=NAMESPACE,
                                        plural=CALICO_NETPOL_PLURAL,
                                        name=policy_name)


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
    return SENSITIVE_LABEL in policy[K8S_METADATA_KEY][K8S_LABELS_KEY]


def is_sensitive_pod(pod):
    return SENSITIVE_LABEL in pod.metadata.labels


def get_pods_owners_set(pods):
    owners = set()
    for pod in pods:
        owners.add(pod.metadata.labels.get(OWNER_LABEL))
    return owners


def get_policies_owners_set(policies):
    owners = set()
    for policy in policies:
        owners.add(policy[K8S_METADATA_KEY][K8S_LABELS_KEY][OWNER_LABEL])
    return owners


def create_missed_policies(active_pods, active_policies, is_sensitive):
    pods_owners = get_pods_owners_set(active_pods)
    users_affected_by_policies = get_policies_owners_set(active_policies)
    for owner in pods_owners:
        if owner not in users_affected_by_policies:
            create_policy(owner, is_sensitive)


def drop_excess_policies(active_pods, active_policies):
    pods_owners = get_pods_owners_set(active_pods)
    for policy in active_policies:
        policy_metadata = policy[K8S_METADATA_KEY]
        user_affected_by_policy = policy_metadata[K8S_LABELS_KEY][OWNER_LABEL]
        if user_affected_by_policy not in pods_owners:
            delete_policy(policy_metadata[K8S_OBJ_NAME_KEY])


def main():
    log_message('===Starting run policies monitoring===')
    while True:
        active_policies = load_all_active_policies()
        sensitive_policies = []
        common_policies = []
        for policy in active_policies:
            sensitive_policies.append(policy) if is_sensitive_policy(policy) else common_policies.append(policy)

        active_pods = load_all_pipeline_pods()
        sensitive_pods = []
        common_pods = []
        for pod in active_pods:
            sensitive_pods.append(pod) if is_sensitive_pod(pod) else common_pods.append(pod)

        create_missed_policies(common_pods, common_policies, False)
        create_missed_policies(sensitive_pods, sensitive_policies, True)

        drop_excess_policies(common_pods, common_policies)
        drop_excess_policies(sensitive_pods, sensitive_policies)

        time.sleep(float(MONITORING_PERIOD_SEC))


if __name__ == '__main__':
    main()
