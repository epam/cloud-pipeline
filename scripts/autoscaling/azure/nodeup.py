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

from pipeline import TaskStatus
import argparse

from pipeline.autoscaling import azureprovider, kubeprovider, utils


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ins_key", type=str, required=True)
    parser.add_argument("--run_id", type=str, required=True)
    parser.add_argument("--cluster_name", type=str, required=False)
    parser.add_argument("--cluster_role", type=str, required=False)
    parser.add_argument("--ins_type", type=str, default='Standard_B2s')
    parser.add_argument("--ins_hdd", type=int, default=30)
    parser.add_argument("--ins_img", type=str, default='pipeline-azure-group/pipeline-base-image')
    parser.add_argument("--num_rep", type=int, default=100)
    parser.add_argument("--time_rep", type=int, default=3)
    parser.add_argument("--is_spot", type=bool, default=False)
    parser.add_argument("--bid_price", type=float, default=1.0)
    parser.add_argument("--kube_ip", type=str, required=True)
    parser.add_argument("--kubeadm_token", type=str, required=True)
    parser.add_argument("--kms_encyr_key_id", type=str, required=False)
    parser.add_argument("--region_id", type=str, default=None)

    args = parser.parse_args()
    ins_key_path = args.ins_key
    run_id = args.run_id
    ins_type = args.ins_type
    ins_hdd = args.ins_hdd
    ins_img = args.ins_img
    num_rep = args.num_rep
    time_rep = args.time_rep
    cluster_name = args.cluster_name
    cluster_role = args.cluster_role
    kube_ip = args.kube_ip
    kubeadm_token = args.kubeadm_token
    region_id = args.region_id
    kms_encyr_key_id = args.kms_encyr_key_id

    cloud_provider = azureprovider.AzureInstanceProvider(region_id)

    if not kube_ip or not kubeadm_token:
        raise RuntimeError('Kubernetes configuration is required to create a new node')

    utils.pipe_log_init(run_id)

    cloud_region = utils.get_cloud_region(region_id)
    utils.pipe_log('Started initialization of new calculation node in cloud region {}:\n'
                   '- RunID: {}\n'
                   '- Type: {}\n'
                   '- Disk: {}\n'
                   '- Image: {}\n'.format(cloud_region,
                                          run_id,
                                          ins_type,
                                          ins_hdd,
                                          ins_img))

    try:

        # Redefine default instance image if cloud metadata has specific rules for instance type
        allowed_instance = utils.get_allowed_instance_image(cloud_region, ins_type, ins_img)
        if allowed_instance and allowed_instance["instance_mask"]:
            utils.pipe_log('Found matching rule {instance_mask}/{ami} for requested instance type {instance_type}'
                           '\nImage {ami} will be used'.format(instance_mask=allowed_instance["instance_mask"],
                                                               ami=allowed_instance["instance_mask_ami"],
                                                               instance_type=ins_type))
            ins_img = allowed_instance["instance_mask_ami"]

        ins_id, ins_ip = cloud_provider.verify_run_id(run_id)

        if not ins_id:
            ins_id, ins_ip = cloud_provider.run_instance(ins_type, ins_hdd, ins_img, ins_key_path, run_id,
                                                         kms_encyr_key_id, kube_ip, kubeadm_token)

        kube_provider = kubeprovider.KubeProvider()

        nodename_full, nodename = cloud_provider.get_instance_names(ins_id)

        nodename = kube_provider.verify_regnode(ins_id, nodename, nodename_full, num_rep, time_rep)
        kube_provider.label_node(nodename, run_id, cluster_name, cluster_role, cloud_region)
        utils.pipe_log('Node created:\n'
                       '- {}\n'
                       '- {}'.format(ins_id, ins_ip))

        # External process relies on this output
        print(ins_id + "\t" + ins_ip + "\t" + nodename)
        utils.pipe_log('{} task finished'.format(utils.NODEUP_TASK), status=TaskStatus.SUCCESS)
    except Exception as e:
        utils.pipe_log('[ERROR] ' + str(e), status=TaskStatus.FAILURE)
        raise e


if __name__ == '__main__':
    main()
