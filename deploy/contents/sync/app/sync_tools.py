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

import copy
import os
import subprocess
import sys

from api.users_api import UserSyncAPI
from api.tool_api import ReadOnlyToolSyncAPI, ToolSyncAPI
from sync_users import sync_metadata_for_entity_class
from multiprocessing.pool import ThreadPool
from print_utils import print_info_message, print_warn_message

ALLOWED_PRICE_TYPES = 'cluster.allowed.price.types'
INSTANCE_TYPES = 'cluster.allowed.instance.types'
IMAGE_FULL_NAME_PATTERN = '{registry_url}/{image_name}:{tag}'
DEFAULT_REGISTRY_PATH_PREFIX = 'cp-docker-registry.default.svc.cluster.local:'
DOCKER_CERTS_DIR = '/etc/docker/certs.d'
DOCKER_LOG_IN_REGISTRY_CMD = ' login {server} -u {username} -p "{token}"'
DOCKER_LOG_OUT_REGISTRY_CMD = ' logout {server}'
DOCKER_PULL_CMD = ' pull {image}'
DOCKER_TAG_CMD = ' tag {source_image} {target_image}'
DOCKER_PUSH_CMD = ' push {image}'
DOCKER_REMOVE_IMAGE_CMD = ' rmi {images_list}'
MEMORY = 'memory'
CPU = 'vcpu'
GPU = 'gpu'
MEM_DIFFERENCE_TOLERANCE = 0.2

metadata_keys_to_ignore = os.getenv('CP_SYNC_TOOLS_METADATA_SKIP_KEYS', '').split(',')
tool_image_transfer_pool_size = os.getenv('CP_SYNC_TOOLS_TRANSFER_POOL_SIZE', 1)
tool_properties_to_override = ['locked', 'cpu', 'ram', 'instanceType',
                               'disk', 'description', 'shortDescription',
                               'defaultCommand', 'allowSensitive', 'endpoints']


def execute_shell_cmd(cmd, return_output=False):
    process = subprocess.Popen(cmd, shell=True, stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    output = process.communicate()
    if return_output:
        return process.returncode, output
    else:
        return process.returncode


class ToolSynchronizer(object):
    def __init__(self, source_api_path, source_access_key, target_api_path, target_access_key, docker_cmd):
        self.source_access_key = source_access_key
        self.target_access_key = target_access_key
        self.api_source = ReadOnlyToolSyncAPI(source_api_path, source_access_key)
        self.api_target = ToolSyncAPI(target_api_path, target_access_key)
        self.api_source_users = UserSyncAPI(source_api_path, source_access_key)
        self.api_target_users = UserSyncAPI(target_api_path, target_access_key)
        self.target_current_user = self.api_target_users.get_current_user_name()
        self.source_current_user = self.api_source_users.get_current_user_name()
        self.source_registries_dict = {}
        self.tool_groups_ids_mapping = {}
        self.tool_ids_mapping = {}
        self.target_default_registry = None
        self.target_default_registry_url = None
        self.source_groups_dict = None
        self.source_tools_dict = None
        self.target_allowed_instance_types = {}
        self.source_allowed_instance_types = {}
        self.allowed_instance_price_types = []
        self.default_instance_type = None
        self.docker_cmd = docker_cmd

    def pull_image(self, image):
        return execute_shell_cmd(self.docker_cmd + DOCKER_PULL_CMD.format(image=image))

    def tag_image(self, source_image, target_image):
        return execute_shell_cmd(self.docker_cmd
                                 + DOCKER_TAG_CMD.format(source_image=source_image, target_image=target_image))

    def push_image(self, image):
        return execute_shell_cmd(self.docker_cmd + DOCKER_PUSH_CMD.format(image=image))

    def delete_images(self, images):
        return execute_shell_cmd(self.docker_cmd + DOCKER_REMOVE_IMAGE_CMD.format(images_list=' '.join(images)))

    def transfer_tool(self, tool):
        registry_id = tool['registryId']
        image_name = tool['image']
        if registry_id not in self.source_registries_dict:
            print_warn_message('No registry with id=[{registry_id}] available in source environment, '
                               'skipping [{tool}] tool transfer'.format(registry_id=registry_id, tool=image_name))
        else:
            tool_id = tool['id']
            tool_settings = self.api_source.load_tool_settings(tool_id=tool_id)
            versions = {}
            for setting in tool_settings:
                configs = []
                if 'settings' in setting and setting['settings']:
                    configs = setting['settings']
                versions[setting['version']] = configs
            self.transfer_tool_images(image_name=image_name,
                                      versions=versions.keys(),
                                      source_registry_url=self.source_registries_dict[registry_id],
                                      target_registry_url=self.target_default_registry_url)
            self.load_allowed_instances_parameters()
            target_tool = self.transfer_description(tool=tool)
            self.transfer_settings(versions, image_name, target_tool['id'])
            self.tool_ids_mapping[tool_id] = target_tool['id']
            if 'hasIcon' in tool and tool['hasIcon'] is True and 'iconId' in tool and tool['iconId'] != 0:
                print_info_message('Transferring icon of [{}]'.format(image_name))
                self.transfer_icon(tool['id'], target_tool['id'])
            print_info_message('Versions\' settings for [{}] are synchronized'.format(image_name))

    def search_corresponding_instance_type(self, source_environment_instance_type):
        if source_environment_instance_type in self.source_allowed_instance_types:
            instance_type_details = self.source_allowed_instance_types[source_environment_instance_type]
            gpu_size = instance_type_details[GPU]
            cpu_size = instance_type_details[CPU]
            mem_size = instance_type_details[MEMORY]
            for instance_type, details in self.target_allowed_instance_types.items():
                target_gpu_size = details[GPU]
                target_cpu_size = details[CPU]
                target_mem_size = details[MEMORY]
                if gpu_size == target_gpu_size \
                        and cpu_size == target_cpu_size \
                        and abs(mem_size - target_mem_size) / max(mem_size, target_mem_size) < MEM_DIFFERENCE_TOLERANCE:
                    return instance_type
        return self.default_instance_type

    def load_allowed_instances_parameters(self):
        source_instance_info = self.api_source.load_allowed_instances_info()
        if INSTANCE_TYPES in source_instance_info and source_instance_info[INSTANCE_TYPES]:
            self.source_allowed_instance_types = \
                {instance_type['name']: instance_type for instance_type in source_instance_info[INSTANCE_TYPES]}

        target_instance_info = self.api_target.load_allowed_instances_info()
        if INSTANCE_TYPES in target_instance_info and target_instance_info[INSTANCE_TYPES]:
            self.target_allowed_instance_types = \
                {instance_type['name']: instance_type for instance_type in target_instance_info[INSTANCE_TYPES]}
            compact_instance = min(self.target_allowed_instance_types.values(), key=lambda x: x['vcpu'])
            if compact_instance and 'name' in compact_instance and compact_instance['name']:
                self.default_instance_type = compact_instance['name']
        if ALLOWED_PRICE_TYPES in target_instance_info and target_instance_info[ALLOWED_PRICE_TYPES]:
            self.allowed_instance_price_types = target_instance_info[ALLOWED_PRICE_TYPES]

    def transfer_icon(self, src_tool_id, target_tool_id):
        content = self.api_source.load_icon(src_tool_id)
        icon_filename = 'icon_{}.png'.format(target_tool_id)
        with open(icon_filename, 'wb') as f:
            f.write(content)
        self.api_target.upload_icon(target_tool_id)
        os.remove(icon_filename)

    def verify_tool_instance_type(self, tool_description):
        if 'instanceType' in tool_description and tool_description['instanceType']:
            instance_type = tool_description['instanceType']
            if instance_type not in self.target_allowed_instance_types:
                corresponding_instance_type = self.search_corresponding_instance_type(instance_type)
                print_warn_message('[{}] instance type for [{}] is not presented in the target environment, '
                                   'override this value with [{}]'.format(instance_type,
                                                                          tool_description['image'],
                                                                          corresponding_instance_type))
                tool_description['instanceType'] = corresponding_instance_type
        return tool_description

    def transfer_description(self, tool):
        image_name = tool['image']
        target_tool = self.api_target.search_tool_by_name(image_name)
        tool_copy = copy.deepcopy(tool)
        group_name = self.extract_group_from_tool_name(image_name)
        source_tool_group_id = self.source_groups_dict[group_name]['id']
        if target_tool is None:
            tool_copy['id'] = None
            tool_copy['registryId'] = self.target_default_registry['id']
            tool_copy['registry'] = None
            tool_copy['toolGroupId'] = self.tool_groups_ids_mapping[source_tool_group_id]
            tool_copy['toolGroup'] = None
            tool_copy['parent'] = None
            tool_copy['hasIcon'] = False
            tool_copy['iconId'] = None
            tool_copy['instanceType'] = None
            tool_copy = self.verify_tool_instance_type(tool_copy)
            target_tool = self.api_target.create_tool(tool=tool_copy)
        else:
            for field in tool_properties_to_override:
                if field in tool_copy and tool_copy[field]:
                    target_tool[field] = tool_copy[field]
            target_tool = self.verify_tool_instance_type(target_tool)
            target_tool = self.api_target.update_tool(target_tool)
        return target_tool

    def verify_tool_settings(self, settings, name):
        for entry in settings:
            if 'configuration' in entry and entry['configuration']:
                configuration = entry['configuration']
                if 'instance_size' in configuration and configuration['instance_size']:
                    instance_type = configuration['instance_size']
                    if instance_type not in self.target_allowed_instance_types:
                        corresponding_instance_type = self.search_corresponding_instance_type(instance_type)
                        print_info_message('[{}] instance type for [{}] is not presented in the target environment, '
                                           'override this value with [{}]'.format(instance_type,
                                                                                  name,
                                                                                  corresponding_instance_type))
                        configuration['instance_size'] = corresponding_instance_type
                if 'is_spot' in configuration and configuration['is_spot'] is not None:
                    is_spot = configuration['is_spot']
                    if len(self.allowed_instance_price_types) > 0:
                        if is_spot is True and 'spot' not in self.allowed_instance_price_types:
                            print_info_message('Spot instances are not allowed in the target environment, '
                                               'set [on_demand] for [{}]'.format(name))
                            configuration['is_spot'] = False
                        if is_spot is False and 'on_demand' not in self.allowed_instance_price_types:
                            print_info_message('On-demand instances are not allowed in the target environment, '
                                               'set [spot] for [{}]'.format(name))
                            configuration['is_spot'] = True
                    else:
                        configuration['is_spot'] = None
        return settings

    def transfer_settings(self, versions_dict, image_name, target_tool_id):
        for version, settings in versions_dict.items():
            full_name = '{}:{}'.format(image_name, version)
            print_info_message('Transferring settings [{}]'.format(full_name))
            settings = self.verify_tool_settings(settings, full_name)
            self.api_target.put_tool_settings(target_tool_id, version, settings)

    def sync_tools_routine(self):
        print_info_message('===Prepare common information===')
        source_deployment_tree = self.api_source.load_registries_hierarchy()
        target_deployment_tree = self.api_target.load_registries_hierarchy()
        self.target_default_registry = self.get_default_registry(target_deployment_tree)
        self.target_default_registry_url = self.extract_registry_url(self.target_default_registry)
        target_users = [user['userName'] for user in self.api_target_users.load_all_users()]
        print_info_message('===Preparation is finished===')

        print_info_message('===Start syncing tool groups===')
        self.sync_tool_groups(source_deployment_tree, target_deployment_tree, target_users)
        print_info_message('===Tool groups sync is finished===')

        print_info_message('===Start syncing tools===')
        self.sync_tools(source_deployment_tree)
        print_info_message('===Tool sync is finished===')

        print_info_message('===Start metadata sync===')
        print_info_message('Syncing tool groups\' metadata')
        sync_metadata_for_entity_class(self.api_source, self.api_target, ids_mapping=self.tool_groups_ids_mapping,
                                       entity_class='TOOL_GROUP')
        print_info_message('Syncing tools\' metadata')
        sync_metadata_for_entity_class(self.api_source, self.api_target, ids_mapping=self.tool_ids_mapping,
                                       entity_class='TOOL')
        print_info_message('===Metadata sync is finished===')

    def sync_tool_groups(self, source_deployment_tree, target_deployment_tree, target_users):
        source_groups_dict, source_tools_dict = self.create_groups_dict(source_deployment_tree)
        target_groups_dict, target_tools_dict = self.create_groups_dict(target_deployment_tree)
        tool_groups_ids_mapping = {}
        for group_name, group in source_groups_dict.items():
            if group_name not in target_groups_dict:
                print_info_message('No [{}] is found in the target deployment, try creating'.format(group_name))
                group['registryId'] = self.target_default_registry['id']
                group['tools'] = None
                if group['owner'] not in target_users:
                    print_info_message('No [{}] user (owner of [{}] group) is presented in the target environment '
                                       'set current[{}] user as owner.'.format(group['owner'],
                                                                               group_name, self.target_current_user))
                    group['owner'] = self.target_current_user
                new_group = self.api_target.create_tool_group(group)
                print_info_message('[{}] created in the target deployment'.format(group_name))
                target_groups_dict[new_group['name']] = new_group
                tool_groups_ids_mapping[group['id']] = new_group['id']
            else:
                print_info_message('[{}] is presented in the target deployment already'.format(group_name))
                tool_groups_ids_mapping[group['id']] = target_groups_dict[group_name]['id']
        self.tool_groups_ids_mapping = tool_groups_ids_mapping
        self.source_groups_dict = source_groups_dict
        self.source_tools_dict = source_tools_dict

    def load_source_tools_details(self):
        for tool_name, tool in self.source_tools_dict.items():
            print_info_message('Loading details for [{}]'.format(tool_name))
            detailed_tool = self.api_source.search_tool_by_name(tool_name)
            if detailed_tool is None:
                print_info_message('Can\'t load detailed description for [{}]'.format(tool_name))
            else:
                self.source_tools_dict[tool_name] = detailed_tool

    def pull_certificate_for_registry(self, registry_url, registry_id, from_source=True):
        if from_source is True:
            api = self.api_source
        else:
            api = self.api_target
        certificate = api.load_registry_certificate(registry_id)
        if self.create_dir_if_not_exists(DOCKER_CERTS_DIR) is False:
            print_warn_message('Unable to create Docker registries certs dir!')
            return
        registry_cert_folder = '{}/{}'.format(DOCKER_CERTS_DIR, registry_url)
        if self.create_dir_if_not_exists(registry_cert_folder) is False:
            print_warn_message('Unable to create certs dir for [{}] registry!'.format(registry_url))
            return
        with open('{}/ca.crt'.format(registry_cert_folder), 'w') as cert_file:
            cert_file.write(certificate)

    def create_dir_if_not_exists(self, dirname):
        if os.path.exists(dirname) is False:
            try:
                os.mkdir(dirname)
            except OSError:
                return False
        return True

    def sync_tools(self, source_deployment_tree):
        self.load_source_tools_details()
        print_info_message('{} tools to be synced'.format(len(self.source_tools_dict)))
        symlinks = []
        authenticated_registries = []
        if self.target_default_registry['pipelineAuth'] is True:
            self.pull_certificate_for_registry(self.target_default_registry_url, self.target_default_registry['id'],
                                               from_source=False)
            op_code, _ = self.log_in_registry(self.target_default_registry_url, username=self.target_current_user,
                                              access_token=self.target_access_key)
            if op_code != 0:
                raise RuntimeError('Can\'t log into target registry [{}], aborting sync process'
                                   .format(self.target_default_registry_url))
            else:
                print_info_message('Logged into [{}]'.format(self.target_default_registry_url))
            authenticated_registries.append(self.target_default_registry_url)

        for registry in source_deployment_tree['registries']:
            registry_url = self.extract_registry_url(registry)
            self.source_registries_dict[registry['id']] = registry_url
            if registry['pipelineAuth'] is True:
                self.pull_certificate_for_registry(registry_url, registry['id'])
                op_code, output = self.log_in_registry(registry_url, username=self.source_current_user,
                                                       access_token=self.source_access_key)
                if op_code != 0:
                    print_warn_message('Can\'t log into source registry [{}], its tools will be skipped'
                                       .format(registry_url))
                    continue
                else:
                    print_info_message('Logged into [{}]'.format(registry_url))
                authenticated_registries.append(registry_url)

        tool_transfer_pool = ThreadPool(tool_image_transfer_pool_size)
        for tool in self.source_tools_dict.values():
            if 'link' in tool and tool['link']:
                symlinks.append(tool)
            else:
                tool_transfer_pool.apply_async(self.transfer_tool, [tool])
        tool_transfer_pool.close()
        tool_transfer_pool.join()
        print_info_message('Syncing symlinks')
        for symlink in symlinks:
            self.create_symlink_in_target(symlink)
        for registry_url in authenticated_registries:
            op_code, output = self.log_out_registry(registry_url)
            if op_code != 0:
                print_warn_message('Error during logging out from [{}]'.format(registry_url))
            else:
                print_info_message('Logged out from [{}] successfully'.format(registry_url))

    def create_symlink_in_target(self, symlink):
        group_name = self.extract_group_from_tool_name(symlink['image'])
        source_group_id = self.source_groups_dict[group_name]['id']
        self.api_target.create_symlink(self.tool_ids_mapping[symlink['link']],
                                       self.tool_groups_ids_mapping[source_group_id])

    @staticmethod
    def get_default_registry(target_deployment_tree):
        if 'registries' in target_deployment_tree and target_deployment_tree['registries']:
            if len(target_deployment_tree['registries']) > 0:
                return target_deployment_tree['registries'][0]
        raise RuntimeError('No registries enabled!')

    @staticmethod
    def extract_registry_url(registry):
        registry_url = registry['path']
        if 'externalUrl' in registry and registry['externalUrl']:
            registry_url = registry['externalUrl']
        return registry_url

    @staticmethod
    def create_groups_dict(tool_registry_tree):
        groups_dict = {}
        tools_dict = {}
        for registry in tool_registry_tree['registries']:
            if 'groups' in registry:
                for group in registry['groups']:
                    if 'name' in group:
                        groups_dict[group['name']] = group
                        if 'tools' in group:
                            for tool in group['tools']:
                                tools_dict[tool['image']] = tool
        return groups_dict, tools_dict

    def log_in_registry(self, server, username, access_token):
        docker_login_cmd = self.docker_cmd \
                           + DOCKER_LOG_IN_REGISTRY_CMD.format(server=server, username=username, token=access_token)
        res, out = execute_shell_cmd(docker_login_cmd, return_output=True)
        return res, out

    def log_out_registry(self, server):
        docker_logout_cmd = self.docker_cmd + DOCKER_LOG_OUT_REGISTRY_CMD.format(server=server)
        res, out = execute_shell_cmd(docker_logout_cmd, return_output=True)
        return res, out

    def transfer_tool_images(self, image_name, versions, source_registry_url, target_registry_url):
        images = []
        for version in versions:
            src_image = IMAGE_FULL_NAME_PATTERN.format(registry_url=source_registry_url, image_name=image_name,
                                                       tag=version)
            print_info_message('Pulling {}'.format(src_image))
            self.pull_image(src_image)
            target_image = IMAGE_FULL_NAME_PATTERN.format(registry_url=target_registry_url, image_name=image_name,
                                                          tag=version)
            print_info_message('Tagging {} as {}'.format(src_image, target_image))
            self.tag_image(src_image, target_image)
            print_info_message('Pushing {}'.format(target_image))
            self.push_image(target_image)
            images.append(src_image)
            images.append(target_image)
        print_info_message('Images are transferred for tool [{}], removing them'.format(image_name))
        self.delete_images(images)

    @staticmethod
    def extract_group_from_tool_name(tool_name):
        return tool_name.split('/')[0]


if __name__ == '__main__':
    if len(sys.argv[1:]) != 5:
        raise RuntimeError('Invalid count of an arguments for sync process!')
    else:
        source_api_host_url = sys.argv[1]
        source_api_token = sys.argv[2]
        target_api_host_url = sys.argv[3]
        target_api_token = sys.argv[4]
        docker_cmd = sys.argv[5]

    synchronizer = ToolSynchronizer(source_api_host_url, source_api_token, target_api_host_url, target_api_token,
                                    docker_cmd)
    synchronizer.sync_tools_routine()
