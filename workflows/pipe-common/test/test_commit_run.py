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

import os
import runpy
import sys

import pytest
from mock import MagicMock, patch

_SCRIPTS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'scripts')
sys.path.insert(0, _SCRIPTS_DIR)

import commit_run
from pipeline.api import Tool

REGISTRY = 'registry:5000'
RUN_ID = '1'
INITIAL_TOOL_ID = 1
NEW_TOOL_ID = 2
SETTINGS = [{'name': 'CP_PARAM', 'value': 'value'}]


def tool(tool_id, image='library/centos', default_version=None):
    result = Tool(image, '1', '1Gi', REGISTRY, 1, 1)
    result.tool_id = tool_id
    result.default_version = default_version
    return result


@pytest.fixture
def api():
    mock_api = MagicMock()
    with patch.object(commit_run, 'api', mock_api, create=True):
        yield mock_api


def run_main(api, *args):
    with patch.dict(os.environ, {'API': 'https://api/'}), \
            patch('pipeline.PipelineAPI', return_value=api), \
            patch.object(sys, 'argv', ['commit_run.py'] + list(args)):
        try:
            runpy.run_path(os.path.join(_SCRIPTS_DIR, 'commit_run.py'), run_name='__main__')
        except SystemExit:
            pass


def test_default_version_is_set_for_tool_with_default_version(api):
    api.load_tool.return_value = tool(NEW_TOOL_ID, default_version='v2')

    assert commit_run.tool_default_version_set(REGISTRY + '/library/centos:v3')
    api.load_tool.assert_called_once_with(image=REGISTRY + '/library/centos:v3', registry='')


def test_default_version_is_not_set_for_tool_without_default_version(api):
    api.load_tool.return_value = tool(NEW_TOOL_ID)

    assert not commit_run.tool_default_version_set(REGISTRY + '/library/centos:v3')


def test_default_version_check_retries_tool_load(api):
    api.load_tool.side_effect = [RuntimeError('API is not available'), tool(NEW_TOOL_ID, default_version='v2')]

    assert commit_run.tool_default_version_set(REGISTRY + '/library/centos:v3')
    assert api.load_tool.call_count == 2


def test_image_tag_is_used_if_image_has_tag():
    assert commit_run.get_image_name_and_tag('library/centos:v3', 'v2') == ('library/centos', 'v3')


def test_default_tag_is_used_if_image_has_no_tag():
    assert commit_run.get_image_name_and_tag('library/centos', 'v2') == ('library/centos', 'v2')


def test_latest_tag_is_used_if_image_has_no_tag_and_no_default_tag():
    assert commit_run.get_image_name_and_tag('library/centos') == ('library/centos', 'latest')


def test_idvs_command_prints_whether_default_version_is_set(capsys):
    api = MagicMock()
    api.load_tool.return_value = tool(NEW_TOOL_ID, default_version='v2')

    run_main(api, 'idvs', RUN_ID, REGISTRY + '/library/centos:v3')

    assert capsys.readouterr().out.strip() == 'True'


def test_evs_command_copies_settings_of_default_version_of_initial_tool():
    api = commit_api(initial_default_version='v2')

    run_main(api, 'evs', RUN_ID, REGISTRY, 'library/centos-new:v3')

    api.load_tool_version_settings.assert_called_once_with(INITIAL_TOOL_ID, 'v2')
    api.create_setting_for_tool_version.assert_called_once_with(NEW_TOOL_ID, 'v3', SETTINGS)


def test_evs_command_copies_settings_of_latest_version_of_initial_tool_without_default_version():
    api = commit_api(initial_default_version=None)

    run_main(api, 'evs', RUN_ID, REGISTRY, 'library/centos-new:v3')

    api.load_tool_version_settings.assert_called_once_with(INITIAL_TOOL_ID, 'latest')
    api.create_setting_for_tool_version.assert_called_once_with(NEW_TOOL_ID, 'v3', SETTINGS)


def test_evs_command_skips_commit_into_default_version_of_same_tool():
    api = commit_api(initial_default_version='v2', new_tool_id=INITIAL_TOOL_ID)

    run_main(api, 'evs', RUN_ID, REGISTRY, 'library/centos:v2')

    api.load_tool_version_settings.assert_not_called()
    api.create_setting_for_tool_version.assert_not_called()


def commit_api(initial_default_version, new_tool_id=NEW_TOOL_ID):
    initial_tool = tool(INITIAL_TOOL_ID, default_version=initial_default_version)
    new_tool = tool(new_tool_id, image='library/centos-new')

    def load_tool(image, registry):
        return initial_tool if image == REGISTRY + '/library/centos' else new_tool

    api = MagicMock()
    api.load_run.return_value = {'dockerImage': REGISTRY + '/library/centos'}
    api.load_tool.side_effect = load_tool
    api.load_tool_version_settings.return_value = [{'settings': SETTINGS}]
    return api
