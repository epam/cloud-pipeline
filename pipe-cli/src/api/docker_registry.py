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

from src.api.base import API
from src.model.docker_registry_model import DockerRegistryModel


class DockerRegistry(API):

    def __init__(self):
        super(DockerRegistry, self).__init__()

    @classmethod
    def load_tree(cls):
        api = cls.instance()
        response_data = api.call('/dockerRegistry/loadTree', None)
        if 'payload' in response_data:
            payload = response_data['payload']
            if 'registries' in payload:
                registries = payload['registries']
                for registry_json in registries:
                    yield DockerRegistryModel.load(registry_json)
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
