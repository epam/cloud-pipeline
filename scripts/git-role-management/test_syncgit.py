# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Exercises the full sync flow (users/pipelines/preferences/gitlab all mocked, no network) under
# whichever interpreter runs it: `python2 -m unittest test_syncgit` and `python3 -m unittest
# test_syncgit` should both pass. This is the runner gap named by issue #4589's Phase 3 - there
# was no test in this package before.

import os
import sys
import unittest

os.environ['API'] = 'https://pipeline.example.com/restapi'
os.environ['API_TOKEN'] = 'test-token'

try:
    from unittest import mock
except ImportError:
    import mock

from internal.model.user_model import UserModel
from internal.model.pipeline_model import PipelineModel
from internal.model.preference_model import PreferenceModel
from internal.model.git_user import GitUser
from internal.model.git_group import GitGroup
from internal.model.git_project import GitProject
from internal.api.gitlab_api import GitLab
from internal.synchronization.pipeline_server import PipelineServer
from internal.synchronization.git_server import GitServer
import syncgit


class SyncgitFlowTest(unittest.TestCase):

    def setUp(self):
        self.user = UserModel.load({
            'id': 1,
            'userName': 'jdoe',
            'email': 'jdoe@example.com',
            'attributes': {'Name': 'John Doe'},
            'roles': [{'id': 1, 'name': 'ROLE_USER', 'predefined': True}],
            'groups': ['ANALYSTS']
        })
        self.pipeline = PipelineModel.load({
            'id': 42,
            'name': 'demo-pipeline',
            'repository': 'https://git.example.com/research/demo-pipeline.git',
            'permissions': [
                {'sid': {'name': 'jdoe', 'principal': True}, 'mask': 5},
                {'sid': {'name': 'ANALYSTS', 'principal': False}, 'mask': 5}
            ]
        })
        self.preferences = [
            PreferenceModel.load({'name': 'git.token', 'value': 'glpat-xxx'}),
            PreferenceModel.load({'name': 'git.user.name', 'value': 'root'}),
            PreferenceModel.load({'name': 'git.gitlab.api.version', 'value': 'v4'}),
        ]
        self.git_user = GitUser.load({
            'id': 100, 'username': 'jdoe', 'name': 'John Doe',
            'email': 'jdoe@example.com', 'access_level': 30
        })
        self.stale_git_user = GitUser.load({
            'id': 200, 'username': 'stale_guy', 'name': 'Stale Guy',
            'email': 'stale@example.com', 'access_level': 30
        })
        self.git_project = GitProject.load({
            'id': 7, 'name': 'demo-pipeline', 'path_with_namespace': 'research/demo-pipeline',
            'shared_with_groups': []
        })

    def test_synchronize_end_to_end(self):
        with mock.patch('internal.api.users_api.Users.list', return_value=[self.user]), \
             mock.patch('internal.api.pipeline_api.Pipeline.list', return_value=([self.pipeline], 1)), \
             mock.patch('internal.api.preference_api.Preference.list', return_value=self.preferences), \
             mock.patch('internal.api.metadata_api.Metadata.load', return_value={}), \
             mock.patch('internal.api.metadata_api.Metadata.update_keys'), \
             mock.patch.object(GitLab, 'list_users', return_value=[]), \
             mock.patch.object(GitLab, 'list_groups', return_value=[]), \
             mock.patch.object(GitLab, 'get_project', return_value=self.git_project), \
             mock.patch.object(GitLab, 'get_project_members', return_value=[]), \
             mock.patch.object(GitLab, 'get_user_ssh_keys', return_value=[]), \
             mock.patch.object(GitLab, 'create_user', return_value=self.git_user) as create_user, \
             mock.patch.object(
                 GitLab, 'create_group',
                 side_effect=lambda name: GitGroup.load({'id': hash(name) % 1000, 'name': name})
             ) as create_group, \
             mock.patch.object(GitLab, 'add_user_ssh_key') as add_user_ssh_key, \
             mock.patch.object(GitLab, 'append_user_to_group') as append_user_to_group, \
             mock.patch.object(GitLab, 'add_user_to_project') as add_user_to_project, \
             mock.patch.object(GitLab, 'add_group_to_project') as add_group_to_project, \
             mock.patch.object(GitLab, 'get_group_members', return_value=[]):

            server = PipelineServer()
            server.synchronize()

            # ANALYSTS (from the fixture permission) plus ROLE_ADMIN (added automatically by
            # PipelineServer.list_pipelines() for every pipeline).
            create_user.assert_called_once()
            self.assertEqual(create_group.call_count, 2)
            add_user_ssh_key.assert_called()
            append_user_to_group.assert_called_once()
            add_user_to_project.assert_called_once()
            self.assertEqual(add_group_to_project.call_count, 2)

        members = server.get_group_members('ANALYSTS')
        self.assertEqual(members, ['jdoe' if sys.version_info[0] >= 3 else b'jdoe'])

    def test_sync_command_does_not_crash_with_no_extra_pipeline_ids(self):
        # Regression test: syncgit.py wrapped its pipeline_ids in a bare map(...), which has no
        # __len__ under Python 3. PipelineServer.synchronize() evaluates len(pipeline_ids) inside
        # its own try/except (pipeline_server.py's bare `except: print(...)`), so the resulting
        # TypeError is swallowed rather than propagating - a naive "call it and see if it raises"
        # test would pass whether or not the bug is present. Assert on the actually-observable
        # effect instead: with the bug, the TypeError fires before synchronize_pipeline() is ever
        # reached, so it is never called; fixed, it is called once for the one listed pipeline.
        with mock.patch('internal.api.users_api.Users.list', return_value=[]), \
             mock.patch('internal.api.pipeline_api.Pipeline.list', return_value=([self.pipeline], 1)), \
             mock.patch.object(PipelineServer, 'synchronize_pipeline') as synchronize_pipeline:
            syncgit.main(['sync'])

            synchronize_pipeline.assert_called_once_with(self.pipeline)

    def test_synchronize_group_removes_a_stale_member_no_longer_in_the_group(self):
        # Regression test: unused_users was a bare map(filter(...)) never wrapped in list(...).
        # A single-use Python 3 iterator gets partially drained by the "is this member already
        # in the group" check inside add_user_to_group, so by the time the stale-member sweep
        # (`for user_id in unused_users`) runs afterwards, it silently finds nothing left to
        # remove - stale_git_user below would never be dropped from the group under the bug,
        # even though it is not one of the currently-desired `members`.
        pipeline_server = mock.MagicMock()
        pipeline_server.find_user_by_username.return_value = self.user
        pipeline_server.user_skipped.return_value = False
        pipeline_server.get_user_keys.return_value = (None, None)

        with mock.patch.object(GitLab, 'list_users', return_value=[self.git_user, self.stale_git_user]), \
             mock.patch.object(GitLab, 'list_groups', return_value=[]), \
             mock.patch('internal.synchronization.git_server.Preference.list', return_value=self.preferences), \
             mock.patch.object(GitLab, 'get_group_members', return_value=[self.git_user, self.stale_git_user]), \
             mock.patch.object(GitLab, 'get_user_ssh_keys', return_value=[]), \
             mock.patch.object(GitLab, 'add_user_ssh_key'), \
             mock.patch.object(GitLab, 'remove_user_from_group') as remove_user_from_group, \
             mock.patch.object(
                 GitLab, 'create_group',
                 side_effect=lambda name: GitGroup.load({'id': 5, 'name': name})
             ):
            git_server = GitServer('https://git.example.com', pipeline_server)
            git_server.initialize()
            # Only 'jdoe' is still a desired member; stale_git_user (id 200) is not in this
            # list, so it must be swept out of the group.
            git_server.synchronize_group('ANALYSTS', ['jdoe'])

            remove_user_from_group.assert_called_once_with(5, 200)

    def test_get_distinct_git_servers_is_a_generator_not_a_broken_iterator(self):
        with mock.patch('internal.api.pipeline_api.Pipeline.list', return_value=([self.pipeline], 1)), \
             mock.patch('internal.api.preference_api.Preference.list', return_value=self.preferences), \
             mock.patch.object(GitLab, 'list_users', return_value=[]), \
             mock.patch.object(GitLab, 'list_groups', return_value=[]):
            server = PipelineServer()
            server.__users__ = [self.user]
            servers = list(server.get_distinct_git_servers())
            self.assertEqual(len(servers), 1)
            self.assertTrue(servers[0].initialized)


if __name__ == '__main__':
    unittest.main()
