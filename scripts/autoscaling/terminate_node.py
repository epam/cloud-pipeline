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
    parser.add_argument("--internal_ip", "-ip", type=str, required=True)
    parser.add_argument("--node_name", "-n", type=str, required=True)
    parser.add_argument("--cloud", "-c", type=str, required=True)

    args = parser.parse_args()
    cloud = args.cloud

    kube_provider = kubeprovider.KubeProvider()
    cloud_region = kube_provider.get_cloud_region_by_node_name(args.node_name)
    cloud_provider = autoscaling.create_cloud_provider(cloud, cloud_region)

    kube_provider.delete_kubernetes_node_by_name(args.node_name)
    cloud_provider.terminate_instance_by_ip_or_name(args.internal_ip, args.node_name)


if __name__ == '__main__':
    main()
