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

import urllib

from src.api.base import API
from src.model.docker_registry_model import ToolScanResultsModel


class Tool(API):

    def __init__(self):
        super(Tool, self).__init__()

    def find_tool_by_name(self, image):
        response_data = self.call('/tool/load?image=%s' % image, None)
        if 'payload' in response_data:
            return response_data['payload']
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            raise RuntimeError("Failed to load tool by name %s" % image)

    def load_settings(self, tool_id, version):
        response_data = self.call('/tool/%d/settings?version=%s' % (tool_id, version), None)
        return response_data['payload'] if 'payload' in response_data else None

    def load_tags(self, tool_id):
        response_data = self.call('/tool/%d/tags' % tool_id, None)
        if 'payload' in response_data:
            return response_data['payload'] or []
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            return []

    def load_vulnerabilities(self, registry, group, tool):
        tool_path = group + '/' + tool
        response_data = self.call('/tool/scan?registry=%s&tool=%s'
                                  % (urllib.quote(registry), urllib.quote(tool_path)), None)
        if 'payload' in response_data:
            return ToolScanResultsModel.load(response_data['payload'])
        if 'message' in response_data:
            raise RuntimeError(response_data['message'])
        else:
            return ToolScanResultsModel.load({})
