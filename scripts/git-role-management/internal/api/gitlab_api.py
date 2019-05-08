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
import requests
import urllib3

from ..model.git_user import GitUser
from ..model.git_group import GitGroup
from ..model.git_project import GitProject
from ..config import Config


class GitLabException(Exception):
    def __init__(self, status, message):
        super(GitLabException, self).__init__('Git error: {} (code {})'.format(message, status))


class GitLab(object):
    def __init__(self, server, token):
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)
        self.server = server
        self.__config__ = Config.instance()
        self.__headers__ = {'Content-Type': 'application/json',
                            'Private-Token': token}

    @classmethod
    def default_error_loader(cls, text):
        try:
            text_json = json.loads(text)
            if 'message' in text_json:
                print text_json['message']
        except RuntimeError:
            print text
        except:
            print text
        return None

    def call(self,
             method,
             data,
             success_loader=None,
             error_loader=None,
             http_method=None):
        expected_status_codes = [200]
        url = '{}/api/v3/{}'.format(self.server, method)
        if not http_method:
            if data:
                expected_status_codes = [201]
                response = requests.post(url, json=data, headers=self.__headers__, verify=False)
            else:
                response = requests.get(url, headers=self.__headers__, verify=False)
        else:
            if http_method.lower() == 'get':
                response = requests.get(url, headers=self.__headers__, verify=False)
            elif http_method.lower() == 'post':
                expected_status_codes = [201]
                response = requests.post(url, json=data, headers=self.__headers__, verify=False)
            elif http_method.lower() == 'delete':
                expected_status_codes = [200, 204]
                if data:
                    response = requests.delete(url, data=data, headers=self.__headers__, verify=False)
                else:
                    response = requests.delete(url, headers=self.__headers__, verify=False)
            else:
                if data:
                    expected_status_codes = [201]
                    response = requests.post(url, json=data, headers=self.__headers__, verify=False)
                else:
                    response = requests.get(url, headers=self.__headers__, verify=False)
        if response.status_code in expected_status_codes:
            if success_loader:
                return success_loader(response.text)
            return None
        else:
            raise GitLabException(response.status_code, response.text)

    def get_iteratively(self, method, success_item_loader=lambda x: x):
        result = []
        current_page = 1
        page_size = 20

        method_url_contains_query_parameters = False
        try:
            method.index('?')
            method_url_contains_query_parameters = True
        except ValueError:
            method_url_contains_query_parameters = False

        def page_call(page, per_page, result_container):
            expected_status_codes = [200]
            if method_url_contains_query_parameters:
                url = '{}/api/v3/{}&page={}&per_page={}'.format(self.server, method, page, per_page)
            else:
                url = '{}/api/v3/{}?page={}&per_page={}'.format(self.server, method, page, per_page)
            response = requests.get(
                url,
                headers=self.__headers__,
                verify=False
            )
            if response.status_code in expected_status_codes:
                json_result = json.loads(response.text)
                for item in json_result:
                    result_container.append(success_item_loader(item))
                return int(response.headers['X-Total']) > page * per_page
            else:
                raise GitLabException(response.status_code, response.text)

        while page_call(current_page, page_size, result):
            current_page += 1
        return result

    def list_users(self):
        return self.get_iteratively(
            'users',
            GitUser.load
        )

    def list_groups(self):
        return self.get_iteratively(
            'groups?statistics=true',
            GitGroup.load
        )

    def create_user(self, userName, name, email, password):
        payload = {
            'name': name.encode('utf8'),
            'username': userName.encode('utf8').replace(' ', '_'),
            'email': email.encode('utf8'),
            'password': password,
            'confirm': 'no'
        }
        return self.call(
            'users',
            payload,
            http_method='POST',
            success_loader=lambda text: GitUser.load(json.loads(text))
        )

    def create_group(self, name):
        payload = {
            'name': name,
            'path': name.replace(' ', '-').encode(encoding='UTF-8', errors='strict')
        }
        return self.call(
            'groups',
            payload,
            http_method='POST',
            success_loader=lambda text: GitGroup.load(json.loads(text))
        )

    def get_group_members(self, group_id):
        return self.get_iteratively(
            'groups/{}/members'.format(group_id),
            GitUser.load
        )

    def get_project_members(self, project):
        return self.get_iteratively(
            'projects/{}/members'.format(project),
            GitUser.load
        )

    def append_user_to_group(self, group_id, user_id, access_level):
        self.call('groups/{}/members'.format(group_id), {'user_id': user_id, 'access_level': access_level}, http_method='POST')

    def remove_user_from_group(self, group_id, user_id):
        self.call('groups/{}/members/{}'.format(group_id, user_id), None, http_method='DELETE')

    def get_project(self, project):
        return self.call('projects/{}'.format(project), None, success_loader=lambda text: GitProject.load(json.loads(text)))

    def add_user_to_project(self, project, user_id, access_level):
        self.call('projects/{}/members'.format(project), {'user_id': user_id, 'access_level': access_level}, http_method='POST')

    def add_group_to_project(self, project, group_id, access_level):
        self.call('projects/{}/share'.format(project), {'group_id': group_id, 'group_access': access_level}, http_method='POST')

    def remove_user_from_project(self, project, user_id):
        self.call('projects/{}/members/{}'.format(project, user_id), None, http_method='DELETE')

    def remove_group_from_project(self, project, group_id):
        self.call('projects/{}/share/{}'.format(project, group_id), None, http_method='DELETE')

    def remove_group(self, group_id):
        return self.call('groups/{}'.format(group_id), None, http_method='DELETE')

    def remove_user(self, user_id):
        return self.call('users/{}'.format(user_id), None, http_method='DELETE')

    def add_user_ssh_key(self, user_id, ssh_pub):
        return self.call('users/{}/keys'.format(user_id), {'title': 'Cloud Pipeline', 'key': ssh_pub}, http_method='POST')
