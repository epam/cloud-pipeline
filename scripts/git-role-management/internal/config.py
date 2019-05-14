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

import json
import os


class ConfigNotFoundError(Exception):
    def __init__(self):
        super(ConfigNotFoundError, self).__init__('Unable to locate configuration or it is incomplete.')


class Config(object):
    """Provides a wrapper for a syncgit command configuration"""

    def __init__(self, safe_initialization=False):
        self.api = os.environ.get('API')
        self.access_key = os.environ.get('API_TOKEN')
        self.proxy = None
        self.email_attribute_name = 'Email'
        self.name_attribute_name = 'Name'
        self.ssh_pub_metadata_name = 'ssh_pub'
        self.ssh_prv_metadata_name = 'ssh_prv'
        self.admins_group_name = 'ROLE_ADMIN'
        self.git_group_prefix = 'PIPELINE-'
        self.git_ssh_title = 'Cloud Pipeline'
        if self.api and self.access_key:
            return

        config_file = Config.config_path()
        if os.path.exists(config_file):
            with open(config_file, 'r') as config_file_stream:
                data = json.load(config_file_stream)
                if 'api' in data:
                    self.api = data['api']
                if 'access_key' in data:
                    self.access_key = data['access_key']
                if 'proxy' in data:
                    self.proxy = data['proxy']
                if 'email-attribute-name' in data:
                    self.email_attribute_name = data['email-attribute-name']
                if 'name-attribute-name' in data:
                    self.name_attribute_name = data['name-attribute-name']
                if 'ssh-pub-metadata-name' in data:
                    self.ssh_pub_metadata_name = data['ssh-pub-metadata-name']
                if 'ssh-prv-metadata-name' in data:
                    self.ssh_prv_metadata_name = data['ssh-prv-metadata-name']
                if 'admins-group-name' in data:
                    self.admins_group_name = data['admins-group-name']
                if 'git-group-prefix' in data:
                    self.git_group_prefix = data['git-group-prefix']
        elif not safe_initialization:
            raise ConfigNotFoundError()

    @classmethod
    def store(cls, access_key, api, proxy, email_attribute_name, name_attribute_name, ssh_pub_attribute_name,
              ssh_prv_attribute_name, admins_group, git_group_prefix):
        current_config = Config.safe_instance()
        config = {
            'api': api if api is not None else current_config.api,
            'access_key': access_key if access_key is not None else current_config.access_key,
            'proxy': proxy,
            'email-attribute-name': email_attribute_name if email_attribute_name is not None else current_config.email_attribute_name,
            'name-attribute-name': name_attribute_name if name_attribute_name is not None else current_config.name_attribute_name,
            'ssh-pub-metadata-name': ssh_pub_attribute_name if ssh_pub_attribute_name is not None else current_config.ssh_pub_metadata_name,
            'ssh-prv-metadata-name': ssh_prv_attribute_name if ssh_prv_attribute_name is not None else current_config.ssh_prv_metadata_name,
            'admins-group-name': admins_group if admins_group is not None else current_config.admins_group_name,
            'git-group-prefix': git_group_prefix if git_group_prefix is not None else current_config.git_group_prefix
        }
        config_file = cls.config_path()

        with open(config_file, 'w+') as config_file_stream:
            json.dump(config, config_file_stream)

    @classmethod
    def config_path(cls):
        home = os.path.expanduser("~")
        config_folder = os.path.join(home, '.syncgit')
        if not os.path.exists(config_folder):
            os.makedirs(config_folder)
        config_file = os.path.join(config_folder, 'config.json')
        return config_file

    @classmethod
    def instance(cls):
        return cls()

    @classmethod
    def safe_instance(cls):
        return cls(safe_initialization=True)
