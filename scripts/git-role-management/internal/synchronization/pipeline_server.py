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

from ..config import Config
from ..api.users_api import Users
from ..api.pipeline_api import Pipeline
from ..model.permission_model import PermissionModel
from .git_server import GitServer
from urlparse import urlparse
import sys
import traceback
from requests.exceptions import ConnectionError
from exceptions import KeyboardInterrupt


class PipelineServer(object):
    def __init__(self):
        self.__users_api__ = Users()
        self.__pipeline_api__ = Pipeline()
        self.__config__ = Config.instance()
        self.__users__ = []
        self.__groups__ = []
        self.__git_servers__ = {}

    def synchronize(self, pipeline_ids=[]):
        try:
            self.__users__ = self.__users_api__.list()
            self.__groups__ = PipelineServer.__create_group_users_map__(self.__users__)
            self.__git_servers__ = {}
            for pipeline in self.list_pipelines():
                if pipeline_ids is None or len(pipeline_ids) == 0 or pipeline.identifier in pipeline_ids:
                    self.synchronize_pipeline(pipeline)
                    print ''
        except RuntimeError as error:
            print error.message
        except KeyboardInterrupt:
            raise
        except:
            print 'Error: ', traceback.format_exc()

    def synchronize_pipeline(self, pipeline):
        try:
            print 'Processing pipeline #{} {}.'.format(pipeline.identifier, pipeline.name)
            parse_result = urlparse(pipeline.repository)
            server = '{}://{}'.format(parse_result.scheme, parse_result.netloc).lower()
            if server not in self.__git_servers__:
                git_server = GitServer(server, self)
                git_server.initialize()
                self.__git_servers__[server] = git_server
            git_server = self.__git_servers__[server]
            git_server.synchronize(pipeline)
        except RuntimeError as error:
            print error.message
        except KeyboardInterrupt:
            raise
        except:
            print 'Error: ', traceback.format_exc()

    def get_distinct_git_servers(self):
        try:
            server_paths = []
            for pipeline in self.list_pipelines():
                parse_result = urlparse(pipeline.repository)
                server = '{}://{}'.format(parse_result.scheme, parse_result.netloc).lower()
                if server not in server_paths:
                    print server
                    git_server = GitServer(server, self)
                    git_server.initialize()
                    server_paths.append(server)
                    yield git_server
        except RuntimeError as error:
            print error.message
        except KeyboardInterrupt:
            raise
        except:
            print 'Error: ', traceback.format_exc()

    def get_group_members(self, group):
        return self.__groups__[group] if group in self.__groups__ else []

    @classmethod
    def __create_group_users_map__(cls, users):
        groups = {}
        for user in users:
            for role in user.roles:
                if role.name.encode('utf8') not in groups:
                    groups[role.name.encode('utf8')] = [user.username.encode('utf8')]
                else:
                    groups[role.name.encode('utf8')].append(user.username.encode('utf8'))
            for group in user.groups:
                if group.encode('utf8') not in groups:
                    groups[group.encode('utf8')] = [user.username.encode('utf8')]
                else:
                    groups[group.encode('utf8')].append(user.username.encode('utf8'))
        return groups

    def find_user_by_username(self, username):
        matches = [x for x in self.__users__ if x.username is not None and  x.username.lower() == username.lower()]
        if len(matches) == 0:
            return None
        return matches[0]

    def find_user_by_email(self, email):
        matches = [x for x in self.__users__ if x.email is not None and x.email.lower() == email.lower()]
        if len(matches) == 0:
            return None
        return matches[0]

    def list_pipelines(self):
        page = 0
        page_size = 150
        try:
            total = page_size + 1
            while total > page * page_size:
                (pipelines, total_count) = self.__pipeline_api__.list(page + 1, page_size)
                total = total_count
                for pipeline in pipelines:
                    admin_permissions = PermissionModel()
                    admin_permissions.name = self.__config__.admins_group_name
                    admin_permissions.principal = False
                    admin_permissions.access_level = 40
                    pipeline.permissions.append(admin_permissions)
                    yield pipeline
                page += 1
        except RuntimeError as error:
            print error.message
        except ConnectionError as error:
            print error.message
        except:
            print 'Error: ', traceback.format_exc()
