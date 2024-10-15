# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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


def prepare_cloud_path(input_path, cloud_scheme='s3'):
    cloud_prefix = cloud_scheme + '://'
    if input_path.startswith(cloud_prefix):
        return os.path.join('/cloud-data', input_path[len(cloud_prefix):])
    if not os.path.exists(input_path):
        raise RuntimeError('No such file [{}] available!'.format(input_path))
    return input_path


def get_required_field(json_data, field_name):
    field_value = json_data.get(field_name)
    if field_value is None:
        raise RuntimeError("Field '%s' is required" % field_name)
    return field_value
