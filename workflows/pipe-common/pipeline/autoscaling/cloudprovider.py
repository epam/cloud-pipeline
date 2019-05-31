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

LIMIT_EXCEEDED_ERROR_MASSAGE = 'Instance limit exceeded. A new one will be launched as soon as free space will be available.'
LIMIT_EXCEEDED_EXIT_CODE = 6


class AbstractInstanceProvider(object):

    def run_instance(self, is_spot, bid_price, ins_type, ins_hdd, ins_img, ins_key, run_id, kms_encyr_key_id,
                     num_rep, time_rep, kube_ip, kubeadm_token):
        pass

    def find_and_tag_instance(self, old_id, new_id):
        pass

    def verify_run_id(self, run_id):
        pass

    def check_instance(self, ins_id, run_id, num_rep, time_rep):
        pass

    def get_instance_names(self, ins_id):
        pass

    def find_instance(self, run_id):
        pass

    def terminate_instance(self, ins_id):
        pass

    def terminate_instance_by_ip(self, node_internal_ip):
        pass
