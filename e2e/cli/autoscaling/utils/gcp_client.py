# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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
import time
from googleapiclient import discovery

from autoscaling.utils.cloud_client import CloudClient


class GCPClient(CloudClient):

    name = "GCP"

    def __init__(self):
        self.cloud_region = os.environ['CP_TEST_REGION']
        self.project_id = os.environ["GOOGLE_PROJECT_ID"]
        self.client = discovery.build('compute', 'v1')

    def describe_instance(self, run_id):
        instance = self.__find_instance(run_id)
        if instance:
            return instance
        return None

    def get_private_ip(self, instance):
        if 'networkInterfaces' not in instance or 'networkIP' not in instance['networkInterfaces'][0]:
            return None
        return instance['networkInterfaces'][0]['networkIP']

    def terminate_instance(self, run_id):
        ins = self.__find_instance(run_id)
        if not ins or 'name' not in ins:
            raise RuntimeError('Failed to find instance for run ID: %s' % str(run_id))
        ins_id = ins['name']
        delete = self.client.instances().delete(
            project=self.project_id,
            zone=self.cloud_region,
            instance=ins_id).execute()

        self.__wait_for_operation(delete['name'])

    def node_price_type_should_be(self, run_id, spot):
        ins = self.describe_instance(run_id)
        if not ins or 'scheduling' not in ins or 'preemptible' not in ins['scheduling']:
            raise RuntimeError('Failed to determine instance lifecycle for run ID: %s' % str(run_id))
        actual_lifecycle_type = ins['scheduling']['preemptible']

        assert actual_lifecycle_type == spot, \
            'Price type differs.\n Expected preemptible flag is: %s.\n Actual: %s.' \
            % (spot, actual_lifecycle_type)

    def __find_instance(self, run_id):
        items = self.__filter_instances('labels.name="{}"'.format(run_id))
        if items:
            filtered = [ins for ins in items if 'labels' in ins and ins['labels']['name'] == str(run_id)]
            if filtered and len(filtered) == 1:
                return filtered[0]
        return None

    def __filter_instances(self, filter):
        result = self.client.instances().list(
            project=self.project_id,
            zone=self.cloud_region,
            filter=filter
        ).execute()
        if 'items' in result:
            return result['items']
        else:
            return None

    def __wait_for_operation(self, operation):
        while True:
            result = self.client.zoneOperations().get(
                project=self.project_id,
                zone=self.cloud_region,
                operation=operation).execute()

            if result['status'] == 'DONE':
                if 'error' in result:
                    raise Exception(result['error'])
                return result

            time.sleep(1)
