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

# Covers the message of an event of scripts/sync_ngs_project.py (issue #4771): the Python 3 change
# turned "self.message = message" into "str(self) = message", a SyntaxError, so the script did not
# start at all, and "event.message" into "str(event)", which puts the object repr into the email and
# into the Errors column of a machine run. Plain unittest, so it runs under Python 2 and 3 without
# extra packages - run with `python -m unittest test_sync_ngs_project` (or under pytest) from inside
# workflows/pipe-common/test.

import os
import sys
import types
import unittest

SCRIPTS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'scripts')
sys.path.insert(0, SCRIPTS_DIR)

# sync_ngs_project.py imports the pipeline package at module level, which this test does not
# exercise. The stub is removed again right after the import: pytest imports every test module before
# it runs any, so a stub left in sys.modules would break "import pipeline.*" in the modules collected after
# this one. The script keeps the stub objects it has imported.
_STUBBED_MODULE_NAMES = ['pipeline']
_ORIGINAL_MODULES = dict((name, sys.modules.get(name)) for name in _STUBBED_MODULE_NAMES)


class _Logger(object):

    @staticmethod
    def info(*args, **kwargs):
        pass

    @staticmethod
    def warn(*args, **kwargs):
        pass


_pipeline = types.ModuleType('pipeline')
_pipeline.Logger = _Logger
_pipeline.PipelineAPI = object
_pipeline.common = types.ModuleType('pipeline.common')
sys.modules['pipeline'] = _pipeline

import sync_ngs_project  # noqa: E402

for _name, _module in _ORIGINAL_MODULES.items():
    if _module is None:
        sys.modules.pop(_name, None)
    else:
        sys.modules[_name] = _module


class _Settings(object):
    project_id = 10
    run_id = 20
    deploy_name = 'Cloud Pipeline'
    machine_run_class = 'MachineRun'
    machine_run_class_id = 30
    cloud_path = 's3://bucket/runs'
    last_processed_column = 'LastProcessed'
    configuration_id = 40


class _Api(object):
    api_url = 'https://cp.example.com/pipeline/restapi/'

    def __init__(self):
        self.saved_entities = []
        self.registered = False

    def find_metadata_entity(self, project_id, entity_id, class_name):
        if not self.registered:
            return None
        return {'id': 50, 'data': {}}

    def save_metadata_entity(self, entity):
        self.registered = True
        self.saved_entities.append(dict(entity, data=dict(entity['data'])))
        return {'id': 50, 'data': {_Settings.last_processed_column: {}}}


class _Time(object):

    @staticmethod
    def sleep(seconds):
        pass


class TestEvent(unittest.TestCase):

    def test_event_keeps_its_message(self):
        event = sync_ngs_project.Event('run1', 'Analysis launch', sync_ngs_project.EVENT_FAILURE, message='boom')

        self.assertEqual('boom', event.message)

    def test_event_without_message_has_none(self):
        event = sync_ngs_project.Event('run1', 'Metadata created', sync_ngs_project.EVENT_SUCCESS)

        self.assertIsNone(event.message)


class TestNotificationText(unittest.TestCase):

    def test_notification_text_shows_the_message_of_each_event(self):
        sync = sync_ngs_project.NGSSync(_Api(), _Settings())
        notifications = [
            sync_ngs_project.Event('run1', 'Analysis launch', sync_ngs_project.EVENT_FAILURE, message='boom'),
            sync_ngs_project.Event('run2', 'Analysis launch', sync_ngs_project.EVENT_SUCCESS, message='launched')
        ]

        text = sync.build_notification_text(notifications)

        self.assertIn('<td>boom</td>', text)
        self.assertIn('<td>launched</td>', text)
        self.assertNotIn('Event object', text)


class TestRegisterMetadata(unittest.TestCase):

    def setUp(self):
        self._time = sync_ngs_project.time
        sync_ngs_project.time = _Time

    def tearDown(self):
        sync_ngs_project.time = self._time

    def test_failure_messages_are_written_into_the_errors_column(self):
        api = _Api()
        notifications = []
        machine_run = sync_ngs_project.MachineRun('/runs/run1', 'run1', _Settings(), api, notifications)
        machine_run.check_results = lambda run_folder: []
        machine_run.find_demultiplex_config = lambda run_folder, sample_sheet: (None, None)
        machine_run.parse_seq_date = lambda name: None
        machine_run.build_results = lambda name, results, demultiplex_config: {}

        def _run_analysis(metadata_entity, run_folder):
            notifications.append(sync_ngs_project.Event('run1', 'Analysis launch', sync_ngs_project.EVENT_FAILURE,
                                                        message='boom'))
        machine_run.run_analysis = _run_analysis

        machine_run.register_metadata('run1', '/runs/run1', [None], False)

        self.assertEqual(2, len(api.saved_entities))
        self.assertEqual({'type': 'string', 'value': 'Analysis launch:boom'}, api.saved_entities[-1]['data']['Errors'])


if __name__ == '__main__':
    unittest.main()
