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

import os
import fnmatch
import logging
import json
import math
from pipeline import Logger, TaskStatus, PipelineAPI, pack_script_contents, pack_powershell_script_contents
import jwt

NETWORKS_PARAM = "cluster.networks.config"
NODE_WAIT_TIME_SEC = "cluster.nodeup.wait.sec"
NODEUP_TASK = "InitializeNode"
MIN_SWAP_DEVICE_SIZE = 5

DEFAULT_FS_TYPE = 'btrfs'
SUPPORTED_FS_TYPES = [DEFAULT_FS_TYPE, 'ext4']

current_run_id = 0
api_url = None
api_token = None
script_path = None


def is_run_id_numerical(run_id):
    try:
        int(run_id)
        return True
    except ValueError:
        return False


def is_api_logging_enabled():
    global api_token
    global api_url
    global current_run_id
    return is_run_id_numerical(current_run_id) and api_url and api_token


def pipe_log_init(run_id):
    global api_user
    global api_token
    global api_url
    global current_run_id
    current_run_id = run_id

    api_url = os.environ["API"]
    api_token = os.environ["API_TOKEN"]
    api_user = os.environ["API_USER"]

    if not is_api_logging_enabled():
        logging.basicConfig(filename='nodeup.log', level=logging.INFO, format='%(asctime)s %(message)s')


def pipe_log(message, status=TaskStatus.RUNNING):
    global api_token
    global api_url
    global script_path
    global current_run_id

    if is_api_logging_enabled():
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


def pipe_log_warn(message):
    global api_token
    global api_url
    global script_path
    global current_run_id

    if is_api_logging_enabled():
        Logger.warn('[{}] {}'.format(current_run_id, message),
                    task_name=NODEUP_TASK,
                    run_id=current_run_id,
                    api_url=api_url,
                    log_dir=script_path,
                    omit_console=True)
    else:
        logging.warn(message)


__CLOUD_METADATA__ = None
__CLOUD_TAGS__ = None

def get_autoscale_preference(preference_name):
    pipe_api = PipelineAPI(api_url, None)
    try:
        preference = pipe_api.get_preference(preference_name)
        if 'value' in preference:
            return preference['value']
        else:
            return None
    except:
        pipe_log('An error occured while getting preference {}, empty value is going to be used'.format(preference_name))
        return None

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
    full_settings, _ = load_cloud_config()
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


def get_access_config(cloud_region):
    return get_cloud_config_section(cloud_region, "access_config")


def get_instance_images_config(cloud_region):
    return get_cloud_config_section(cloud_region, "amis")


def get_network_tags(cloud_region):
    return get_cloud_config_section(cloud_region, "network_tags")


def get_allowed_zones(cloud_region):
    return list(get_networks_config(cloud_region).keys())


def get_region_tags(cloud_region):
    return get_cloud_config_section(cloud_region, "tags")


def get_security_groups(cloud_region):
    config = get_cloud_config_section(cloud_region, "security_group_ids")
    if not config:
        raise RuntimeError('Security group setting is required to run an instance')
    return config


def get_proxies(cloud_region):
    return get_cloud_config_section(cloud_region, "proxies")


def get_well_known_hosts(cloud_region):
    return get_cloud_config_section(cloud_region, "well_known_hosts")


def get_allowed_instance_image(cloud_region, instance_type, instance_platform, default_image):
    default_init_script = os.path.dirname(os.path.abspath(__file__)) + '/init.sh'
    default_embedded_scripts = { "fsautoscale": os.path.dirname(os.path.abspath(__file__)) + '/fsautoscale.sh' }
    default_object = { "instance_mask_ami": default_image, "instance_mask": None, "init_script": default_init_script,
        "embedded_scripts": default_embedded_scripts, "fs_type": DEFAULT_FS_TYPE}

    instance_images_config = get_instance_images_config(cloud_region)
    if not instance_images_config:
        return default_object

    for image_config in instance_images_config:
        image_platform = image_config["platform"]
        instance_mask = image_config["instance_mask"]
        instance_mask_ami = image_config["ami"]
        init_script = image_config.get("init_script", default_object["init_script"])
        embedded_scripts = image_config.get("embedded_scripts", default_object["embedded_scripts"])
        fs_type = image_config.get("fs_type", DEFAULT_FS_TYPE)
        if image_platform == instance_platform and fnmatch.fnmatch(instance_type, instance_mask):
            return { "instance_mask_ami": instance_mask_ami, "instance_mask": instance_mask, "init_script": init_script,
            "embedded_scripts": embedded_scripts, "fs_type": fs_type}

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


def replace_swap(swap_size, init_script):
    if swap_size is not None:
        return init_script.replace('@swap_size@', str(swap_size))
    return init_script


def replace_docker_images(pre_pull_images, user_data_script):
    global api_token
    payload = jwt.decode(api_token, verify=False)
    if 'sub' in payload:
        subject = payload['sub']
        user_data_script = user_data_script \
            .replace("@PRE_PULL_DOCKERS@", ",".join(pre_pull_images)) \
            .replace("@API_USER@", subject)
        return user_data_script
    else:
        raise RuntimeError("Pre-pulled docker initialization failed: unable to parse JWT token for docker auth.")


def get_user_data_script(cloud_region, ins_type, ins_img, ins_platform, kube_ip,
                         kubeadm_token, kubeadm_cert_hash, kube_node_token,
                         global_distribution_url, swap_size, pre_pull_images=[]):
    allowed_instance = get_allowed_instance_image(cloud_region, ins_type, ins_platform, ins_img)
    if allowed_instance and allowed_instance["init_script"]:
        init_script = open(allowed_instance["init_script"], 'r')
        user_data_script = init_script.read()
        certs_string = get_certs_string()
        well_known_string = get_well_known_hosts_string(cloud_region)
        fs_type = allowed_instance.get('fs_type', DEFAULT_FS_TYPE)
        if fs_type not in SUPPORTED_FS_TYPES:
            pipe_log_warn('Unsupported filesystem type is specified: %s. Falling back to default value %s.' %
                          (fs_type, DEFAULT_FS_TYPE))
            fs_type = DEFAULT_FS_TYPE
        init_script.close()
        user_data_script = replace_proxies(cloud_region, user_data_script)
        user_data_script = replace_swap(swap_size, user_data_script)
        if pre_pull_images:
            user_data_script = replace_docker_images(pre_pull_images, user_data_script)
        user_data_script = user_data_script.replace('@DOCKER_CERTS@', certs_string) \
                                           .replace('@WELL_KNOWN_HOSTS@', well_known_string) \
                                           .replace('@KUBE_IP@', kube_ip) \
                                           .replace('@KUBE_TOKEN@', kubeadm_token) \
                                           .replace('@KUBE_CERT_HASH@', kubeadm_cert_hash) \
                                           .replace('@KUBE_NODE_TOKEN@', kube_node_token) \
                                           .replace('@API_URL@', api_url) \
                                           .replace('@API_TOKEN@', api_token) \
                                           .replace('@API_USER@', api_user) \
                                           .replace('@FS_TYPE@', fs_type) \
                                           .replace('@GLOBAL_DISTRIBUTION_URL@', global_distribution_url)
        embedded_scripts = {}
        if allowed_instance["embedded_scripts"]:
            for embedded_name, embedded_path in allowed_instance["embedded_scripts"].items():
                embedded_scripts[embedded_name] = open(embedded_path, 'r').read()
        if ins_platform == 'windows':
            return pack_powershell_script_contents(user_data_script, embedded_scripts)
        else:
            return pack_script_contents(user_data_script, embedded_scripts)
    else:
        raise RuntimeError('Unable to get init.sh path')


def get_cloud_region(region_id):
    if region_id is not None:
        return region_id
    regions, _ = load_cloud_config()
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
    key_parts = content[0].strip().split()
    # <protocol> <key> - without user name if it is exists
    ins_key = '{} {}'.format(key_parts[0], key_parts[1])
    return ins_key


def poll_instance(sock, timeout, ip, port):
    result = -1
    sock.settimeout(float(timeout))
    try:
        result = sock.connect_ex((ip, port))
    except Exception as e:
        print(e)
    sock.settimeout(None)
    return result


def get_swap_size(cloud_region, ins_type, is_spot, provider):
    pipe_log('Configuring swap settings for an instance in {} region'.format(cloud_region))
    swap_params = get_cloud_config_section(cloud_region, "swap")
    if swap_params is None:
        return None
    swap_ratio = get_swap_ratio(swap_params)
    if swap_ratio is None:
        pipe_log("Swap ratio is not configured. Swap configuration will be skipped.")
        return None
    ram = get_instance_ram(cloud_region, ins_type, is_spot, provider)
    if ram is None:
        pipe_log("Failed to determine instance RAM. Swap configuration will be skipped.")
        return None
    swap_size = int(math.ceil(swap_ratio * ram))
    if swap_size >= MIN_SWAP_DEVICE_SIZE:
        pipe_log("Swap device will be configured with size %d." % swap_size)
        return swap_size
    return None


def get_swap_ratio(swap_params):
    for swap_param in swap_params:
        if not 'name' in swap_param or not 'path' in swap_param:
            continue
        item_name = swap_param['name']
        if item_name == 'swap_ratio':
            item_value = swap_param['path']
            if item_value:
                try:
                    return float(item_value)
                except ValueError:
                    pipe_log("Unexpected swap_ratio value: {}".format(item_value))
    return None


def get_instance_ram(cloud_region, ins_type, is_spot, provider):
    api = PipelineAPI(api_url, None)
    region_id = get_region_id(cloud_region, provider, api)
    if region_id is None:
        return None
    instance_types = api.get_allowed_instance_types(region_id, spot=is_spot)
    ram = get_ram_from_group(instance_types, 'cluster.allowed.instance.types', ins_type)
    if ram is None:
        ram = get_ram_from_group(instance_types, 'cluster.allowed.instance.types.docker', ins_type)
    return ram


def get_ram_from_group(instance_types, group, instance_type):
    if group in instance_types:
        for current_type in instance_types[group]:
            if current_type['name'] == instance_type:
                return current_type['memory']
    return None


def get_region_id(cloud_region, provider, api):
    regions = api.get_regions()
    if regions is None:
        return None
    for region in regions:
        if region.provider == provider and region.region_id == cloud_region:
            return region.id
    return None
