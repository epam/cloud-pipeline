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

# Exercises the full sync flow (users/pipelines/preferences/gitlab all mocked, no network):
# `python2 -m unittest test_syncgit` (requires the `mock` package).

import os
import shutil
import tempfile
import unittest

os.environ['API'] = 'https://pipeline.example.com/restapi'
os.environ['API_TOKEN'] = 'test-token'

import mock

from internal.config import Config
from internal.model.user_model import UserModel
from internal.model.pipeline_model import PipelineModel
from internal.model.preference_model import PreferenceModel
from internal.model.git_user import GitUser
from internal.model.git_group import GitGroup
from internal.model.git_project import GitProject
from internal.api.gitlab_api import GitLab
from internal.synchronization.pipeline_server import PipelineServer
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
        self.git_project = GitProject.load({
            'id': 7, 'name': 'demo-pipeline', 'path_with_namespace': 'research/demo-pipeline',
            'shared_with_groups': []
        })

    def test_synchronize_end_to_end(self):
        # Has no entry in the pipeline permissions, only the role
        reader = UserModel.load({
            'id': 2,
            'userName': 'rreader',
            'email': 'rreader@example.com',
            'attributes': {'Name': 'Rita Reader'},
            'roles': [{'id': 2, 'name': 'ROLE_PIPELINE_READER', 'predefined': True}],
            'groups': []
        })
        git_reader = GitUser.load({
            'id': 101, 'username': 'rreader', 'name': 'Rita Reader',
            'email': 'rreader@example.com', 'access_level': 30
        })
        git_users = {'jdoe@example.com': self.git_user, 'rreader@example.com': git_reader}
        with mock.patch('internal.api.users_api.Users.list', return_value=[self.user, reader]), \
             mock.patch('internal.api.pipeline_api.Pipeline.list', return_value=([self.pipeline], 1)), \
             mock.patch('internal.api.preference_api.Preference.list', return_value=self.preferences), \
             mock.patch('internal.api.metadata_api.Metadata.load', return_value={}), \
             mock.patch('internal.api.metadata_api.Metadata.update_keys'), \
             mock.patch.object(GitLab, 'list_users', return_value=[]), \
             mock.patch.object(GitLab, 'list_groups', return_value=[]), \
             mock.patch.object(GitLab, 'get_project', return_value=self.git_project), \
             mock.patch.object(GitLab, 'get_project_members', return_value=[]), \
             mock.patch.object(GitLab, 'get_user_ssh_keys', return_value=[]), \
             mock.patch.object(
                 GitLab, 'create_user',
                 side_effect=lambda username, name, email, password: git_users[email]
             ) as create_user, \
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

            # ANALYSTS (from the fixture permission) plus ROLE_ADMIN, ROLE_PIPELINE_ADMIN and
            # ROLE_PIPELINE_READER (added automatically by PipelineServer.list_pipelines() for every
            # pipeline). The reader gets a GitLab account and joins the ROLE_PIPELINE_READER group.
            self.assertEqual(create_user.call_count, 2)
            self.assertEqual(create_group.call_count, 4)
            add_user_ssh_key.assert_called()
            self.assertEqual(append_user_to_group.call_count, 2)
            append_user_to_group.assert_any_call(hash('PIPELINE-ROLE_PIPELINE_READER') % 1000, 101, 40)
            add_user_to_project.assert_called_once()
            self.assertEqual(add_group_to_project.call_count, 4)
            add_group_to_project.assert_any_call(
                'research%2Fdemo-pipeline', hash('PIPELINE-ROLE_PIPELINE_ADMIN') % 1000, 40)
            add_group_to_project.assert_any_call(
                'research%2Fdemo-pipeline', hash('PIPELINE-ROLE_PIPELINE_READER') % 1000, 20)

        members = server.get_group_members('ANALYSTS')
        self.assertEqual(members, ['jdoe'])
        readers = server.get_group_members('ROLE_PIPELINE_READER')
        self.assertEqual(readers, ['rreader'])

    def test_list_pipelines_shares_every_pipeline_with_the_admin_and_pipeline_role_groups(self):
        with mock.patch('internal.api.pipeline_api.Pipeline.list', return_value=([self.pipeline], 1)):
            pipelines = list(PipelineServer().list_pipelines())

        self.assertEqual(len(pipelines), 1)
        self.assertEqual(self._group_permissions(pipelines[0]), [
            ('ANALYSTS', 40),
            ('ROLE_ADMIN', 40),
            ('ROLE_PIPELINE_ADMIN', 40),
            ('ROLE_PIPELINE_READER', 20)
        ])

    def test_list_pipelines_keeps_one_entry_with_the_higher_level_for_a_role_set_in_the_acl(self):
        pipeline = PipelineModel.load({
            'id': 43,
            'name': 'shared-pipeline',
            'repository': 'https://git.example.com/research/shared-pipeline.git',
            'permissions': [
                # READ only (reporter) is raised to the maintainer level of the role
                {'sid': {'name': 'ROLE_PIPELINE_ADMIN', 'principal': False}, 'mask': 1},
                # READ and WRITE (maintainer) is kept over the reporter level of the role
                {'sid': {'name': 'ROLE_PIPELINE_READER', 'principal': False}, 'mask': 5}
            ]
        })
        with mock.patch('internal.api.pipeline_api.Pipeline.list', return_value=([pipeline], 1)):
            pipelines = list(PipelineServer().list_pipelines())

        self.assertEqual(self._group_permissions(pipelines[0]), [
            ('ROLE_ADMIN', 40),
            ('ROLE_PIPELINE_ADMIN', 40),
            ('ROLE_PIPELINE_READER', 40)
        ])

    def test_add_group_permission_merges_only_a_group_entry_of_the_same_name(self):
        pipeline = PipelineModel.load({
            'id': 44,
            'name': 'named-pipeline',
            'permissions': [
                {'sid': {'name': 'readers', 'principal': False}, 'mask': 1},
                {'sid': {'name': 'ROLE_PIPELINE_ADMIN', 'principal': True}, 'mask': 1}
            ]
        })

        pipeline.add_group_permission('READERS', 40)
        pipeline.add_group_permission('ROLE_PIPELINE_ADMIN', 40)

        self.assertEqual(
            sorted((x.name, x.principal, x.access_level) for x in pipeline.permissions),
            [('ROLE_PIPELINE_ADMIN', False, 40), ('ROLE_PIPELINE_ADMIN', True, 20), ('readers', False, 40)]
        )

    def test_configure_stores_the_pipeline_role_group_names(self):
        config_dir = tempfile.mkdtemp()
        try:
            with mock.patch.object(Config, 'config_path', return_value=os.path.join(config_dir, 'config.json')):
                with self.assertRaises(SystemExit) as configure:
                    syncgit.main(['configure', '--pipeline-admins-group=PIPELINE_ADMINS',
                                  '--pipeline-readers-group=PIPELINE_READERS'])
                self.assertEqual(configure.exception.code, 0)
                # The configuration file is read only when API and API_TOKEN are not in the environment
                with mock.patch.dict(os.environ):
                    del os.environ['API']
                    config = Config.instance()
        finally:
            shutil.rmtree(config_dir)

        self.assertEqual(config.pipeline_admins_group_name, 'PIPELINE_ADMINS')
        self.assertEqual(config.pipeline_readers_group_name, 'PIPELINE_READERS')
        self.assertEqual(config.admins_group_name, 'ROLE_ADMIN')

    @staticmethod
    def _group_permissions(pipeline):
        return sorted((x.name, x.access_level) for x in pipeline.permissions if not x.principal)


if __name__ == '__main__':
    unittest.main()
