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
from ..api.preference_api import Preference
from ..api.gitlab_api import GitLab, GitLabException
import random
from urlparse import urlparse
from urllib import quote_plus
from requests.exceptions import ConnectionError
from exceptions import KeyboardInterrupt


class GitServer(object):
    def __init__(self, server, pipeline_server):
        self.__config__ = Config.instance()
        self.__preferences__ = []
        self.__access_token__ = ''
        self.__root_user_name__ = ''
        self.__api__ = None
        self.__server__ = server
        self.__users__ = []
        self.__groups__ = []
        self.synchronized_groups = []
        self.synchronized_users = []
        self.__pipeline_server_ = pipeline_server
        self.initialized = False

    def initialize(self):
        try:
            print 'Loading users and groups for server {}...'.format(self.__server__)
            preference_api = Preference()
            self.__preferences__ = preference_api.list()
            self.__access_token__ = self.__find_preference_value__('git.token')
            self.__root_user_name__ = self.__find_preference_value__('git.user.name')
            if self.__root_user_name__ is not None:
                self.__root_user_name__ = self.__root_user_name__.lower()
            self.__api__ = GitLab(self.__server__, self.__access_token__)
            self.__users__ = self.__api__.list_users()
            self.__groups__ = self.__api__.list_groups()
            self.initialized = True
            print 'Done.'
        except GitLabException as error:
            print 'Initialization error: {}'.format(error.message)
            print 'Pipelines with git server {} will be skipped'.format(self.__server__)
        except ConnectionError as error:
            print 'Initialization error: {}'.format(error.message)
            print 'Pipelines with git server {} will be skipped'.format(self.__server__)
        except KeyboardInterrupt:
            raise
        except:
            print 'Initialization error. Pipelines with git server {} will be skipped'.format(self.__server__)

    def synchronize(self, pipeline):
        if not self.initialized:
            print 'Skipping pipeline {} (#{})'.format(
                pipeline.name,
                pipeline.identifier
            )
            return
        users_to_synchronize = []
        groups_to_synchronize = []
        for permission in pipeline.permissions:
            if permission.principal and permission.name not in self.synchronized_users:
                users_to_synchronize.append(permission.name)
            if not permission.principal and permission.name not in self.synchronized_groups:
                groups_to_synchronize.append(permission.name)

        if len(users_to_synchronize) > 0 or len(groups_to_synchronize) > 0:
            print 'Synchronizing users and groups for git server {}'.format(self.__server__)
        for user in users_to_synchronize:
            self.synchronize_user(user)
        for group in groups_to_synchronize:
            self.synchronize_group(group, self.__pipeline_server_.get_group_members(group))

        self.synchronized_groups.extend(groups_to_synchronize)
        self.synchronized_users.extend(users_to_synchronize)

        parse_result = urlparse(pipeline.repository)
        project = quote_plus(parse_result.path.encode('utf-8')[1:])
        if project.lower().endswith('.git'):
            project = project[:-4]
        project_info = self.__api__.get_project(project)
        project_membres = self.__api__.get_project_members(project)

        def find_shared_group(id):
            matches = [x for x in project_info.shared_with_groups if x.group_id == id]
            if len(matches) == 0:
                return None
            return matches[0]

        def find_member(id):
            matches = [x for x in project_membres if x.id == id]
            if len(matches) == 0:
                return None
            return matches[0]

        def find_group_permission(group_name):
            matches = [x for x in pipeline.permissions if
                       not x.principal and '{}{}'.format(
                           self.__config__.git_group_prefix,
                           x.name
                       ).lower() == group_name.lower()]
            if len(matches) == 0:
                return None
            return matches[0]

        def find_user_permission(username):
            matches = [x for x in pipeline.permissions if x.principal and x.name.lower() == username.lower()]
            if len(matches) == 0:
                return None
            return matches[0]

        print 'Managing users and groups for project {}'.format(project)
        for member in project_membres:
            if member.name.lower() == self.__root_user_name__:
                continue
            git_user = self.find_user_by_id(member.id)
            if git_user is not None:
                pipeline_user = self.__pipeline_server_.find_user_by_email(git_user.email)
                if pipeline_user is not None:
                    permission = find_user_permission(pipeline_user.username)
                    if permission is None:
                        self.__api__.remove_user_from_project(project, member.id)
                        print 'User {} ({}) removed from project'.format(
                            pipeline_user.friendly_name,
                            git_user.email
                        )

        if project_info is not None:
            for shared_group in project_info.shared_with_groups:
                permission = find_group_permission(shared_group.group_name)
                if permission is None:
                    self.__api__.remove_group_from_project(project, shared_group.group_id)
                    print 'Shared group {} removed from project'.format(
                        shared_group.group_name
                    )
            for permission in pipeline.permissions:
                if not permission.principal:
                    git_group = self.find_git_group(permission.name)
                    if git_group is not None:
                        shared_group = find_shared_group(git_group.id)
                        if shared_group is not None and shared_group.group_access_level != permission.access_level:
                            self.__api__.remove_group_from_project(project, git_group.id)
                            self.__api__.add_group_to_project(project, git_group.id, permission.access_level)
                            print 'Shared group {} ({}) changed access level from {} ({}) to {} ({})'.format(
                                permission.name,
                                git_group.name,
                                shared_group.group_access_level,
                                GitServer.access_level_description(shared_group.group_access_level).lower(),
                                permission.access_level,
                                GitServer.access_level_description(permission.access_level).lower()
                            )
                        elif shared_group is None:
                            self.__api__.add_group_to_project(project, git_group.id, permission.access_level)
                            print 'Shared group {} ({}) added to project with access level {} ({})'.format(
                                permission.name,
                                git_group.name,
                                permission.access_level,
                                GitServer.access_level_description(permission.access_level).lower()
                            )
                        else:
                            print 'Group {} ({}) already shared with access level {} ({})'.format(
                                permission.name,
                                git_group.name,
                                permission.access_level,
                                GitServer.access_level_description(permission.access_level).lower()
                            )
                else:
                    pipeline_user = self.__pipeline_server_.find_user_by_username(permission.name)
                    if pipeline_user is not None:
                        git_user = self.find_user_by_email(pipeline_user.email)
                        if git_user is not None:
                            member = find_member(git_user.id)
                            if member is not None and member.access_level != permission.access_level:
                                self.__api__.remove_user_from_project(project, member.id)
                                self.__api__.add_user_to_project(project, member.id, permission.access_level)
                                print 'User {} ({}) changed access level from {} ({}) to {} ({})'.format(
                                    pipeline_user.friendly_name,
                                    git_user.email,
                                    member.access_level,
                                    GitServer.access_level_description(member.access_level).lower(),
                                    permission.access_level,
                                    GitServer.access_level_description(permission.access_level).lower()
                                )
                            elif member is None:
                                self.__api__.add_user_to_project(project, git_user.id, permission.access_level)
                                print 'User {} ({}) added to project with access level {} ({})'.format(
                                    pipeline_user.friendly_name,
                                    git_user.email,
                                    permission.access_level,
                                    GitServer.access_level_description(permission.access_level).lower()
                                )
                            else:
                                print 'User {} ({}) already belongs to project with access level {} ({})'.format(
                                    pipeline_user.friendly_name,
                                    git_user.email,
                                    permission.access_level,
                                    GitServer.access_level_description(permission.access_level).lower()
                                )

    def __find_preference_value__(self, key):
        matches = [x for x in self.__preferences__ if x.name == key]
        if len(matches) == 0:
            return None
        return matches[0].value

    @classmethod
    def generate_password(cls, length):
        alphabet = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
        password = ''
        for i in range(0, length):
            password += random.choice(alphabet)
        return password

    def find_user_by_email(self, email):
        if email is None:
            return None
        matches = [x for x in self.__users__ if x.email is not None and x.email.lower() == email.lower()]
        if len(matches) == 0:
            return None
        return matches[0]

    def find_user_by_id(self, id):
        matches = [x for x in self.__users__ if x.id == id]
        if len(matches) == 0:
            return None
        return matches[0]

    def find_git_group(self, name):
        matches = [x for x in self.__groups__ if x.name.lower() == '{}{}'.format(
            self.__config__.git_group_prefix,
            name
        ).lower()]
        if len(matches) == 0:
            return None
        return matches[0]

    def synchronize_user(self, username):
        pipeline_user = self.__pipeline_server_.find_user_by_username(username)
        if pipeline_user is None:
            print 'Unknown user {}'.format(username)
            return None
        if pipeline_user.email is None:
            print 'User {} has no email set and wouldn\'t be created'.format(username)
            return None
        git_user = self.find_user_by_email(pipeline_user.email)
        if git_user is not None:
            print 'User {} ({}) already exists at git'.format(git_user.name, git_user.email)
            return git_user
        if pipeline_user.friendly_name is not None:
            print 'Creating user {} ({}).'.format(pipeline_user.friendly_name, pipeline_user.email)
            result = self.__api__.create_user(
                pipeline_user.git_username,
                pipeline_user.friendly_name,
                pipeline_user.email,
                GitServer.generate_password(100)
            )
            self.__users__.append(result)
            return result
        return None

    def synchronize_group(self, group, members):
        git_group = self.find_git_group(group)
        if git_group is None:
            print 'Creating group {}.'.format(group)
            git_group = self.__api__.create_group('{}{}'.format(
                self.__config__.git_group_prefix,
                group
            ))
            self.__groups__.append(git_group)
        if git_group is not None:
            print 'Appending users to group {}'.format(group)
            group_users = self.__api__.get_group_members(git_group.id)
            unused_users = map(lambda x: x.id,
                               filter(lambda x: x.username is not None and x.username.lower() != self.__root_user_name__,
                                      group_users))
            for user in members:
                user_id = self.add_user_to_group(git_group, user, unused_users, group)
                if user_id in unused_users:
                    unused_users.remove(user_id)
            for user_id in unused_users:
                git_user = self.find_user_by_id(user_id)
                if git_user is not None:
                    print 'Removing user {} from group {}'.format(git_user.name, group)
                    self.__api__.remove_user_from_group(git_group.id, user_id)

    def add_user_to_group(self, group, username, group_members_ids, group_friendly_name):
        pipeline_user = self.__pipeline_server_.find_user_by_username(username)
        if pipeline_user is None:
            print 'Unknown user {}'.format(username)
            return None
        if pipeline_user.email is None:
            print 'User {} has no email set'.format(username)
            return None
        git_user = self.find_user_by_email(pipeline_user.email)
        if git_user is None:
            git_user = self.synchronize_user(username)
        if git_user is not None:
            if git_user.id not in group_members_ids:
                print 'Appending user {} ({}) to group {} ({})'.format(
                    pipeline_user.friendly_name,
                    git_user.email,
                    group_friendly_name,
                    group.name
                )
                self.__api__.append_user_to_group(group.id, git_user.id, 40)
            else:
                print 'User {} ({}) already belongs to group {} ({})'.format(
                    pipeline_user.friendly_name,
                    git_user.email,
                    group_friendly_name,
                    group.name
                )
            return git_user.id
        return None

    @classmethod
    def access_level_description(cls, access_level):
        if access_level == 10:
            return 'Guest access (read only)'
        elif access_level == 20:
            return 'Reporter access (read only)'
        elif access_level == 30:
            return 'Developer access'
        elif access_level == 40:
            return 'Master access'
        elif access_level == 50:
            return 'Owner'
        return ''

    def clear_users_and_groups(self):
        if not self.initialized:
            print 'Skipping server {}'.format(self.__server__)
        else:
            for user in self.__users__:
                if user.username != self.__root_user_name__:
                    try:
                        self.__api__.remove_user(user.id)
                        print 'User {} ({}) removed.'.format(user.name, user.username)
                    except GitLabException as error:
                        print 'Error removing user {} ({}): {}'.format(user.username, self.__server__, error.message)
            for group in self.__groups__:
                try:
                    self.__api__.remove_group(group.id)
                    print 'Group #{} {} removed.'.format(group.id, group.name)
                except GitLabException as error:
                    print 'Error removing group {} ({}): {}'.format(group.name, self.__server__, error.message)
