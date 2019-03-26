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

from pipeline.autoscaling import gcpprovider, kubeprovider, utils


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run_id", "-kid", type=str, required=True)
    parser.add_argument("--ins_id", "-id", type=str, required=False)  # do we need?
    args = parser.parse_args()
    run_id = args.run_id

    kube_provider = kubeprovider.KubeProvider()

    cloud_region = kube_provider.get_cloud_region_by_node_name(args.node_name)
    kube_provider.delete_kubernetes_node_by_name(args.node_name)

    cloud_provider = gcpprovider.GCPInstanceProvider(cloud_region)
    try:
        instance = cloud_provider.find_instance(run_id)
    except Exception:
        instance = None
    if instance is None:
        kube_provider.delete_kube_node(None, run_id)
    else:
        try:
            nodename, nodename_full = cloud_provider.get_instance_names(instance['name'])
        except Exception:
            nodename = None

        kube_provider.delete_kube_node(nodename, run_id)
        cloud_provider.terminate_instance(instance['name'])


if __name__ == '__main__':
    main()
