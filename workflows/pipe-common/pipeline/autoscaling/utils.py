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
import fnmatch
import logging
import json
from pipeline import Logger, TaskStatus, PipelineAPI

NETWORKS_PARAM = "cluster.networks.config"
NODEUP_TASK = "InitializeNode"

current_run_id = 0
api_url = None
api_token = None
script_path = None


def pipe_log_init(run_id):
    global api_token
    global api_url
    global current_run_id
    current_run_id = run_id

    api_url = os.environ["API"]
    api_token = os.environ["API_TOKEN"]

    if not api_url or not api_token:
        logging.basicConfig(filename='nodeup.log', level=logging.INFO, format='%(asctime)s %(message)s')


def pipe_log(message, status=TaskStatus.RUNNING):
    global api_token
    global api_url
    global script_path
    global current_run_id

    if api_url and api_token:
        Logger.log_task_event(NODEUP_TASK,
                              '[{}] {}'.format(current_run_id, message),
                              run_id=current_run_id,
                              instance=str(current_run_id),
                              log_dir=script_path,
                              api_url=api_url,
                              status=status,
                              omit_console=True)
    else:
        # Log as always
        logging.info(message)


__CLOUD_METADATA__ = None
__CLOUD_TAGS__ = None


def load_cloud_config():
    global __CLOUD_METADATA__
    global __CLOUD_TAGS__

    if not __CLOUD_METADATA__:
        pipe_api = PipelineAPI(api_url, None)
        preference = pipe_api.get_preference(NETWORKS_PARAM)
        if preference:
            data = json.loads(preference['value'])
            if 'regions' not in data:
                pipe_log('Malformed networks config file: missing "regions" section. Update config file.')
                raise RuntimeError('Malformed networks config file: missing "regions" section. Update config file.')
            __CLOUD_METADATA__ = data['regions']
            if 'tags' in data:
                __CLOUD_TAGS__ = data['tags']
    return __CLOUD_METADATA__, __CLOUD_TAGS__


def get_region_settings(cloud_region):
    full_settings, tags = load_cloud_config()
    for region_settings in full_settings:
        if 'name' in region_settings and region_settings['name'] == cloud_region:
            return region_settings
    pipe_log('Failed to find networks settings for region: %s.' % cloud_region)
    return None


def get_cloud_config_section(cloud_region, section_name):
    cloud_metadata = get_region_settings(cloud_region)
    if cloud_metadata and section_name in cloud_metadata and len(cloud_metadata[section_name]) > 0:
        return cloud_metadata[section_name]
    else:
        return None


def get_networks_config(cloud_region):
    return get_cloud_config_section(cloud_region, "networks")


def get_instance_images_config(cloud_region):
    return get_cloud_config_section(cloud_region, "amis")


def get_allowed_zones(cloud_region):
    return list(get_networks_config(cloud_region).keys())


def get_security_groups(cloud_region):
    config = get_cloud_config_section(cloud_region, "security_group_ids")
    if not config:
        raise RuntimeError('Security group setting is required to run an instance')
    return config


def get_proxies(cloud_region):
    return get_cloud_config_section(cloud_region, "proxies")


def get_well_known_hosts(cloud_region):
    return get_cloud_config_section(cloud_region, "well_known_hosts")


def get_allowed_instance_image(cloud_region, instance_type, default_image):
    default_init_script = os.path.dirname(os.path.abspath(__file__)) + '/init.sh'
    default_object = {"instance_mask_ami": default_image, "instance_mask": None, "init_script": default_init_script}

    instance_images_config = get_instance_images_config(cloud_region)
    if not instance_images_config:
        return default_object

    for image_config in instance_images_config:
        instance_mask = image_config["instance_mask"]
        instance_mask_ami = image_config["ami"]
        init_script = None
        if "init_script" in image_config:
            init_script = image_config["init_script"]
        else:
            init_script = default_object["init_script"]
        if fnmatch.fnmatch(instance_type, instance_mask):
            return {"instance_mask_ami": instance_mask_ami, "instance_mask": instance_mask, "init_script": init_script}

    return default_object


def get_certs_string():
    global api_token
    global api_url
    command_pattern = 'mkdir -p /etc/docker/certs.d/{url} && echo "{cert}" >> /etc/docker/certs.d/{url}/ca.crt'
    if api_url and api_token:
        pipe_api = PipelineAPI(api_url, None)
        result = pipe_api.load_certificates()
        if not result:
            return ""
        else:
            entries = []
            for url, cert in result.iteritems():
                entries.append(command_pattern.format(url=url, cert=cert))
            return " && ".join(entries)
    return ""


def get_well_known_hosts_string(cloud_region):
    pipe_log('Setting well-known hosts an instance in {} region'.format(cloud_region))
    command_pattern = 'echo {well_known_ip} {well_known_host} >> /etc/hosts'
    well_known_list = get_well_known_hosts(cloud_region)
    if not well_known_list or len(well_known_list) == 0:
        return ''

    entries = []
    for well_known_item in well_known_list:
        if not 'ip' in well_known_item or not 'host' in well_known_item:
            continue

        well_known_ip = well_known_item['ip']
        well_known_host = well_known_item['host']
        if not well_known_ip or not well_known_host:
            continue

        entries.append(command_pattern.format(well_known_ip=well_known_ip, well_known_host=well_known_host))
        pipe_log('-> {}={}'.format(well_known_ip, well_known_host))

    if len(entries) == 0:
        return ''
    return ' && '.join(entries)


def replace_proxies(aws_region, init_script):
    pipe_log('Setting proxy settings for an instance in {} region'.format(aws_region))
    proxies_list = get_proxies(aws_region)
    if not proxies_list:
        return init_script

    for proxy_item in proxies_list:
        if not 'name' in proxy_item or not 'path' in proxy_item:
            continue
        proxy_name = proxy_item['name']
        proxy_path = proxy_item['path']
        if not proxy_name:
            continue
        if proxy_path == None:
            proxy_path = ''
        init_script = init_script.replace('@' + proxy_name + '@', proxy_path)
        pipe_log('-> {}={}'.format(proxy_name, proxy_path))

    return init_script


def get_user_data_script(cloud_region, ins_type, ins_img, kube_ip, kubeadm_token):
    allowed_instance = get_allowed_instance_image(cloud_region, ins_type, ins_img)
    if allowed_instance and allowed_instance["init_script"]:
        init_script = open(allowed_instance["init_script"], 'r')
        user_data_script = init_script.read()
        certs_string = get_certs_string()
        well_known_string = get_well_known_hosts_string(cloud_region)
        init_script.close()
        user_data_script = replace_proxies(cloud_region, user_data_script)
        return user_data_script\
            .replace('@DOCKER_CERTS@', certs_string) \
            .replace('@WELL_KNOWN_HOSTS@', well_known_string) \
            .replace('@KUBE_IP@', kube_ip) \
            .replace('@KUBE_TOKEN@', kubeadm_token)
    else:
        raise RuntimeError('Unable to get init.sh path')


def get_cloud_region(region_id):
    if region_id is not None:
        return region_id
    regions, tags = load_cloud_config()
    for region in regions:
        if 'default' in region and region['default']:
            return region['name']
    pipe_log('Failed to determine region for Azure instance')
    raise RuntimeError('Failed to determine region for Azure instance')


def increment_or_fail(num_rep, rep, error_message):
    rep = rep + 1
    if rep > num_rep:
        raise RuntimeError(error_message)
    return rep


def read_ssh_key(ssh_pub_key):
    with open(ssh_pub_key) as f:
        content = f.readlines()
        if len(content) != 1 and not content[0].startswith("ssh-rsa"):
            raise RuntimeError("Wrong format of ssh pub key!")
    ins_key = content[0]
    return ins_key


def poll_instance(sock, timeout, ip, port):
    result = -1
    sock.settimeout(float(timeout))
    try:
        result = sock.connect_ex((ip, port))
    except Exception as e:
        pass
    sock.settimeout(None)
    return result


def resource_tags():
    tags = {}
    config_regions, config_tags = load_cloud_config()
    if config_tags is None:
        return tags
    for key, value in config_tags.iteritems():
        tags.update({key: value})
    return tags


def run_id_tag(run_id):
    return {
        'Name': run_id,
    }


def get_tags(run_id):
    tags = run_id_tag(run_id)
    res_tags = resource_tags()
    if res_tags:
        tags.update(res_tags)
    return tags
