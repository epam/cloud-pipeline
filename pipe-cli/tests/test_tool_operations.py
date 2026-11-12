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

from types import SimpleNamespace
from unittest.mock import patch

from src.utilities import tool_operations
from src.utilities.tool_operations import ToolOperations


def test_view_version_shows_the_size_in_whole_megabytes(capsys):
    tool = SimpleNamespace(id=1, image='library/tool')
    registry = SimpleNamespace(path='registry:443')
    with patch.object(tool_operations.DockerRegistry, 'load_tree', return_value=[]), \
            patch.object(ToolOperations, 'find_registry', return_value=registry), \
            patch.object(ToolOperations, 'find_tool_group', return_value=SimpleNamespace()), \
            patch.object(ToolOperations, 'find_tool', return_value=tool), \
            patch.object(tool_operations, 'Tool') as tool_api:
        tool_api.return_value.load_tags.return_value = ['latest']
        tool_api.return_value.load_settings.return_value = [{'size': 5 * tool_operations.MB + 1}]
        tool_api.return_value.load_vulnerabilities.return_value = None
        ToolOperations.view_version('library', 'tool', 'latest')

    size_rows = [line for line in capsys.readouterr().out.splitlines() if 'Size:' in line]
    assert len(size_rows) == 1
    assert '5 MB' in size_rows[0]
    assert '5.0' not in size_rows[0]
