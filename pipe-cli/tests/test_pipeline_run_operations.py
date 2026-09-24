# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import pytest
from unittest.mock import patch

from src.utilities.pipeline_run_operations import PipelineRunOperations

TOOL_ID = 1
IMAGE = 'library/ubuntu'
TAGS = ['latest', '18.04']


class _Stop(Exception):
    pass


class TestGetImageTag:

    @pytest.mark.parametrize('docker_image, expected_tag', [
        ('library/ubuntu', 'latest'),
        ('library/ubuntu:18.04', '18.04'),
        ('registry:5000/library/ubuntu', 'latest'),
        ('registry:5000/library/ubuntu:18.04', '18.04'),
    ])
    def test_get_image_tag(self, docker_image, expected_tag):
        assert PipelineRunOperations._get_image_tag(docker_image) == expected_tag


@patch('src.utilities.pipeline_run_operations.Tool')
class TestCheckDockerImageTag:

    @staticmethod
    def _mocks(tool_mock):
        return tool_mock.return_value.find_tool_by_name, tool_mock.return_value.load_tags

    def test_existing_tag_passes(self, tool_mock):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.return_value = {'id': TOOL_ID}
        tags_mock.return_value = TAGS
        PipelineRunOperations._check_docker_image_tag(IMAGE + ':18.04', False)
        tags_mock.assert_called_once_with(TOOL_ID)

    def test_missing_tag_defaults_to_latest(self, tool_mock):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.return_value = {'id': TOOL_ID}
        tags_mock.return_value = TAGS
        PipelineRunOperations._check_docker_image_tag(IMAGE, False)

    def test_unknown_tag_exits(self, tool_mock, capsys):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.return_value = {'id': TOOL_ID}
        tags_mock.return_value = TAGS
        with pytest.raises(SystemExit) as exc_info:
            PipelineRunOperations._check_docker_image_tag(IMAGE + ':latet', False)
        assert exc_info.value.code == 1
        _, err = capsys.readouterr()
        assert '"latet"' in err
        assert '18.04, latest' in err

    def test_unknown_tag_exits_in_quiet_mode(self, tool_mock):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.return_value = {'id': TOOL_ID}
        tags_mock.return_value = TAGS
        with pytest.raises(SystemExit) as exc_info:
            PipelineRunOperations._check_docker_image_tag(IMAGE + ':latet', True)
        assert exc_info.value.code == 1

    def test_empty_tags_are_not_checked(self, tool_mock):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.return_value = {'id': TOOL_ID}
        tags_mock.return_value = []
        PipelineRunOperations._check_docker_image_tag(IMAGE + ':latet', False)

    def test_tool_without_id_is_not_checked(self, tool_mock):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.return_value = {}
        PipelineRunOperations._check_docker_image_tag(IMAGE + ':latet', False)
        tags_mock.assert_not_called()

    def test_tool_load_failure_warns_and_continues(self, tool_mock, capsys):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.side_effect = RuntimeError('Access is denied')
        PipelineRunOperations._check_docker_image_tag(IMAGE + ':latet', False)
        _, err = capsys.readouterr()
        assert 'Access is denied' in err
        tags_mock.assert_not_called()

    def test_tags_load_failure_warns_and_continues(self, tool_mock, capsys):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.return_value = {'id': TOOL_ID}
        tags_mock.side_effect = RuntimeError('Registry is unavailable')
        PipelineRunOperations._check_docker_image_tag(IMAGE + ':latet', False)
        _, err = capsys.readouterr()
        assert 'Registry is unavailable' in err

    def test_load_failure_is_silent_in_quiet_mode(self, tool_mock, capsys):
        find_mock, tags_mock = self._mocks(tool_mock)
        find_mock.side_effect = RuntimeError('Access is denied')
        PipelineRunOperations._check_docker_image_tag(IMAGE + ':latet', True)
        _, err = capsys.readouterr()
        assert err == ''


@patch('src.utilities.pipeline_run_operations.Pipeline.get', side_effect=_Stop)
@patch('src.utilities.pipeline_run_operations.UserOperationsManager')
@patch.object(PipelineRunOperations, '_check_docker_image_tag', side_effect=_Stop)
class TestRunChecksDockerImageTag:

    @staticmethod
    def _run(pipeline, parameters, docker_image):
        with pytest.raises(_Stop):
            PipelineRunOperations.run(pipeline, None, parameters, True, [], None, None, docker_image,
                                      None, None, False, None, None, False)

    def _mock_admin(self, user_manager_mock):
        user_manager_mock.return_value.get_all_user_roles.return_value = ['ROLE_ADMIN']

    def test_tool_run_checks_tag(self, check_mock, user_manager_mock, pipeline_mock):
        self._mock_admin(user_manager_mock)
        self._run(None, False, IMAGE + ':latet')
        check_mock.assert_called_once_with(IMAGE + ':latet', False)

    def test_pipeline_run_with_docker_image_checks_tag(self, check_mock, user_manager_mock, pipeline_mock):
        self._mock_admin(user_manager_mock)
        self._run('pipeline', False, IMAGE + ':latet')
        check_mock.assert_called_once_with(IMAGE + ':latet', False)
        pipeline_mock.assert_not_called()

    def test_pipeline_parameters_listing_skips_check(self, check_mock, user_manager_mock, pipeline_mock):
        self._mock_admin(user_manager_mock)
        self._run('pipeline', True, IMAGE + ':latet')
        check_mock.assert_not_called()

    def test_pipeline_run_without_docker_image_skips_check(self, check_mock, user_manager_mock, pipeline_mock):
        self._mock_admin(user_manager_mock)
        self._run('pipeline', False, None)
        check_mock.assert_not_called()
