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

from base_api import API

API_GET_FULL_REGISTRIES_HIERARCHY = 'dockerRegistry/loadTree'
API_TOOL_GROUP = 'toolGroup'
API_TOOL_DELETE = 'tool/delete'
API_TOOL_SEARCH = 'tool/load'
API_TOOL_UPDATE = 'tool/update'
API_TOOL_REGISTER = 'tool/register'
API_TOOL_SYMLINK = 'tool/symlink'
API_TOOL_SETTINGS = 'tool/{tool_id}/settings'
API_TOOL_ICON = 'tool/{tool_id}/icon'
API_ALLOWED_INSTANCE_INFO = 'cluster/instance/allowed'


class ReadOnlyToolSyncAPI(API):
    def __init__(self, api_path, access_key):
        super(ReadOnlyToolSyncAPI, self).__init__(api_path, access_key)

    def load_registries_hierarchy(self):
        data = self.call(API_GET_FULL_REGISTRIES_HIERARCHY, http_method='GET')
        registries = []
        if 'payload' in data and data['payload']:
            registries = data['payload']
        return registries

    def search_tool_by_name(self, image_name):
        tool = None
        try:
            response = self.call(API_TOOL_SEARCH, params={"image": image_name}, http_method='GET')
            if 'payload' in response and response['payload']:
                tool = response['payload']
        except RuntimeError as e:
            print('Tool [{image_name}] is not found.'.format(image_name=image_name))
        return tool

    def load_tool_settings(self, tool_id, tool_version=None):
        response = self.call(API_TOOL_SETTINGS.format(tool_id=tool_id), params={'version': tool_version},
                             http_method='GET')
        tool_settings = []
        if 'payload' in response and response['payload']:
            tool_settings = response['payload']
        return tool_settings

    def load_icon(self, tool_id):
        return self.call(API_TOOL_ICON.format(tool_id=tool_id), http_method='GET')

    def load_allowed_instances_info(self):
        response = self.call(API_ALLOWED_INSTANCE_INFO, http_method='GET')
        instance_info = {}
        if 'payload' in response and response['payload']:
            instance_info = response['payload']
        return instance_info


class ToolSyncAPI(ReadOnlyToolSyncAPI):
    def __init__(self, api_path, access_key):
        super(ToolSyncAPI, self).__init__(api_path, access_key)

    def create_tool_group(self, tool_group):
        response = self.call(API_TOOL_GROUP, data=API.to_json(tool_group), http_method='POST')
        registered_group = None
        if 'payload' in response and response['payload']:
            registered_group = response['payload']
        return registered_group

    def put_tool_settings(self, tool_id, version, settings):
        return self.call(API_TOOL_SETTINGS.format(tool_id=tool_id), params={'version': version},
                         data=API.to_json(settings),
                         http_method='POST')

    def delete_tool(self, tool_image, version):
        return self.call(API_TOOL_DELETE, params={'image': tool_image, 'hard': False}, http_method='DELETE')

    def create_symlink(self, tool_id, group_id):
        return self.call(API_TOOL_SYMLINK, data=API.to_json({'toolId': tool_id, 'groupId': group_id}),
                         http_method='POST')

    def upload_icon(self, tool_id):
        with open('icon_{}.png'.format(tool_id), 'rb') as icon_file:
            return self.call(API_TOOL_ICON.format(tool_id=tool_id), http_method='POST', files={'icon': icon_file})

    def update_tool(self, tool):
        response = self.call(API_TOOL_UPDATE, data=API.to_json(tool), http_method='POST')
        updated_tool = None
        if 'payload' in response and response['payload']:
            updated_tool = response['payload']
        return updated_tool

    def create_tool(self, tool):
        response = self.call(API_TOOL_REGISTER, data=API.to_json(tool), http_method='POST')
        new_tool = None
        if 'payload' in response and response['payload']:
            new_tool = response['payload']
        return new_tool
