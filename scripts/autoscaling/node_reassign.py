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

import argparse

import pipeline.autoscaling as autoscaling
from pipeline.autoscaling import *


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--old_id", "-kid", type=str, required=True)
    parser.add_argument("--new_id", "-nid", type=str, required=True)
    parser.add_argument("--cloud", "-c", type=str, required=True)
    args = parser.parse_args()
    old_id = args.old_id
    new_id = args.new_id
    cloud = args.cloud

    kube_provider = kubeprovider.KubeProvider()
    cloud_region = kube_provider.get_cloud_region(old_id)
    cloud_provider = autoscaling.create_cloud_provider(cloud, cloud_region)

    ins_id = cloud_provider.find_and_tag_instance(old_id, new_id)
    nodename, nodename_full = cloud_provider.get_instance_names(ins_id)
    nodename = kube_provider.verify_node_exists(nodename, nodename_full, ins_id)
    kube_provider.change_label(nodename, new_id, cloud_region)


if __name__ == '__main__':
    main()
