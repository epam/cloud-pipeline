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
    """Provides a wrapper for a syncnfs command configuration"""

    def __init__(self, safe_initialization=False):
        self.api = os.environ.get('API')
        self.access_key = os.environ.get('API_TOKEN')
        self.users_root = None
        self.nfs_root = None

        config_file = Config.config_path()
        if os.path.exists(config_file):
            with open(config_file, 'r') as config_file_stream:
                data = json.load(config_file_stream)
                if 'api' in data:
                    self.api = data['api']
                if 'access_key' in data:
                    self.access_key = data['access_key']
                if 'users_root' in data:
                    self.users_root = data['users_root']
                if 'nfs_root' in data:
                    self.nfs_root = data['nfs_root']
        elif not safe_initialization:
            raise ConfigNotFoundError()

    @classmethod
    def store(cls, access_key, api, users_root, nfs_root):
        current_config = Config.safe_instance()
        config = {
            'api': api if api is not None else current_config.api,
            'access_key': access_key if access_key is not None else current_config.access_key,
            'users_root': users_root if users_root is not None else current_config.users_root,
            'nfs_root': nfs_root if nfs_root is not None else current_config.nfs_root
        }
        config_file = cls.config_path()

        with open(config_file, 'w+') as config_file_stream:
            json.dump(config, config_file_stream)

    @classmethod
    def config_path(cls):
        home = os.path.expanduser("~")
        config_folder = os.path.join(home, '.syncnfs')
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
