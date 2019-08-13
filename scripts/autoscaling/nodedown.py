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
    parser.add_argument("--run_id", "-kid", type=str, required=True)
    parser.add_argument("--cloud", "-c", type=str, required=True)
    args = parser.parse_args()
    run_id = args.run_id
    cloud = args.cloud

    kube_provider = kubeprovider.KubeProvider()
    cloud_region = kube_provider.get_cloud_region(run_id)
    cloud_provider = autoscaling.create_cloud_provider(cloud, cloud_region)

    try:
        ins_id = cloud_provider.find_instance(run_id)
    except Exception:
        ins_id = None
    if ins_id is None:
        kube_provider.delete_kube_node(None, run_id)
    else:
        try:
            nodename, nodename_full = cloud_provider.get_instance_names(ins_id)
            nodename = kube_provider.find_node(nodename, nodename_full)
            if not nodename:
                raise RuntimeError("Failed to find Node {}".format(ins_id))
        except Exception as e:
            print e
            nodename = None

        kube_provider.delete_kube_node(nodename, run_id)
        cloud_provider.terminate_instance(ins_id)


if __name__ == '__main__':
    main()
