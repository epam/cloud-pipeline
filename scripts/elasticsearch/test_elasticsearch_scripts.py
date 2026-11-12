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

# The search scripts on Python 3, with the platform API mocked (no network):
# `python3 -m unittest test_elasticsearch_scripts` from inside scripts/elasticsearch.

import importlib.util
import io
import os
import sys
import types
import unittest
from contextlib import redirect_stdout
from unittest import mock

SCRIPTS_DIR = os.path.dirname(os.path.abspath(__file__))


def load_script(name, file_name, pipeline_api):
    # The scripts import PipelineAPI of the pipe-common package: a stub module stands in for it
    pipeline_module = types.ModuleType('pipeline')
    pipeline_module.PipelineAPI = pipeline_api
    with mock.patch.dict(sys.modules, {'pipeline': pipeline_module}):
        spec = importlib.util.spec_from_file_location(name, os.path.join(SCRIPTS_DIR, file_name))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
    return module


class AddRepositoryHookTest(unittest.TestCase):

    def test_adds_a_hook_to_every_pipeline(self):
        api = mock.MagicMock()
        api.load_all_pipelines.return_value = [{'id': 1, 'name': 'first'}, {'id': 2, 'name': 'second'}]
        api.add_pipeline_repository_hook.return_value = {'id': 1}
        script = load_script('add_repository_hook', 'add_repository_hook.py', mock.MagicMock(return_value=api))
        output = io.StringIO()

        with mock.patch.dict(os.environ, {'API': 'https://pipeline.example.com/restapi', 'API_TOKEN': 'token'}), \
                redirect_stdout(output):
            script.main()

        self.assertEqual([mock.call(1), mock.call(2)], api.add_pipeline_repository_hook.call_args_list)
        self.assertIn('Pipeline repository hook was added for pipeline first', output.getvalue())
        self.assertIn('Pipeline repository hook was added for pipeline second', output.getvalue())


class SearchReindexTest(unittest.TestCase):

    def test_touch_item_prints_the_error_of_a_failed_request(self):
        api = mock.MagicMock()
        api.api_url = 'https://pipeline.example.com/restapi'
        api.execute_request.side_effect = RuntimeError('Access is denied')
        script = load_script('search_reindex', 'search-reindex.py', mock.MagicMock(return_value=api))
        output = io.StringIO()

        with redirect_stdout(output):
            script.touch_item(api, 7, 'PIPELINE')

        self.assertIn('Processing 7 [PIPELINE]', output.getvalue())
        self.assertIn('Access is denied', output.getvalue())


if __name__ == '__main__':
    unittest.main()
