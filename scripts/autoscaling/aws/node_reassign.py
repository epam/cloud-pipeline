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

from pipeline.autoscaling import awsprovider, kubeprovider


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--old_id", "-kid", type=str, required=True)
    parser.add_argument("--new_id", "-nid", type=str, required=True)
    args = parser.parse_args()
    old_id = args.old_id
    new_id = args.new_id

    kube_provider = kubeprovider.KubeProvider()
    cloud_region = kube_provider.get_cloud_region_by_node_name(args.node_name)
    cloud_provider = awsprovider.AWSCloudProvider(cloud_region)

    ins_id = cloud_provider.find_and_tag_instance(old_id, new_id)
    nodename, nodename_full = cloud_provider.get_instance_names(ins_id)
    nodename = kube_provider.verify_node_exists(ins_id, nodename, nodename_full)
    kube_provider.change_label(nodename, new_id, cloud_region)


if __name__ == '__main__':
    main()
