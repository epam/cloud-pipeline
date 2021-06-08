# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
from distutils.dir_util import copy_tree

_PIPE_WEB_DAV_CONFIG_DIR = '.pipe-webdav-client'


def move_configuration(cloud_data_config_parent_dir, user_default_home_dir):
    user_home_dir = os.getenv('HOME', user_default_home_dir)
    web_dav_config_dir = os.path.join(user_home_dir, _PIPE_WEB_DAV_CONFIG_DIR)
    cloud_data_config_dir = os.path.join(cloud_data_config_parent_dir, _PIPE_WEB_DAV_CONFIG_DIR)
    copy_tree(cloud_data_config_dir, web_dav_config_dir)
