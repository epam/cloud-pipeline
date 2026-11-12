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

# Covers the message of an event of scripts/blocked_users_cleanup.py (issue #4771): the Python 3
# change turned "self.message = message" into "str(self) = message", a SyntaxError, so the script
# did not start at all, and "event.message" into "str(event)", which puts the object repr into the
# email. Plain unittest, so it runs under Python 2 and 3 without extra packages - run with
# `python -m unittest test_blocked_users_cleanup` (or under pytest) from inside workflows/pipe-common/test.

import os
import sys
import types
import unittest

SCRIPTS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'scripts')
sys.path.insert(0, SCRIPTS_DIR)

# blocked_users_cleanup.py imports the pipeline package at module level, which this test does not
# exercise. The stubs are removed again right after the import: pytest imports every test module before
# it runs any, so a stub left in sys.modules would break "import pipeline.*" in the modules collected after
# this one. The script keeps the stub objects it has imported.
_STUBBED_MODULE_NAMES = ['pipeline', 'pipeline.api', 'pipeline.common', 'pipeline.log', 'pipeline.log.logger']
_ORIGINAL_MODULES = dict((name, sys.modules.get(name)) for name in _STUBBED_MODULE_NAMES)


def _stub_module(name, **attrs):
    module = types.ModuleType(name)
    for key, value in attrs.items():
        setattr(module, key, value)
    sys.modules[name] = module
    return module


_pipeline = _stub_module('pipeline')
_pipeline.api = _stub_module('pipeline.api', PipelineAPI=object)
_pipeline.common = _stub_module('pipeline.common')
_pipeline.log = _stub_module('pipeline.log')
_pipeline.log.logger = _stub_module('pipeline.log.logger', LocalLogger=object, RunLogger=object, TaskLogger=object,
                                    LevelLogger=object, ResilientLogger=object)

import blocked_users_cleanup  # noqa: E402

for _name, _module in _ORIGINAL_MODULES.items():
    if _module is None:
        sys.modules.pop(_name, None)
    else:
        sys.modules[_name] = _module


class _Api(object):
    api_url = 'https://cp.example.com/pipeline/restapi/'


class TestEvent(unittest.TestCase):

    def test_event_keeps_its_message(self):
        event = blocked_users_cleanup.Event(1, None, blocked_users_cleanup.RUN_TYPE, 'owner', 'ERROR',
                                            'Failed to terminate paused run: boom')

        self.assertEqual('Failed to terminate paused run: boom', event.message)


class TestNotifier(unittest.TestCase):

    def test_notification_text_shows_the_message_of_each_event(self):
        notifier = blocked_users_cleanup.Notifier(_Api(), blocked_users_cleanup.EMAIL_SUBJECT, 'Cloud Pipeline', ['admin'])
        notifier.add(blocked_users_cleanup.Event(1, None, blocked_users_cleanup.RUN_TYPE, 'owner', 'ERROR',
                                                 'Failed to terminate paused run: boom'))
        notifier.add(blocked_users_cleanup.Event(2, 'storage', blocked_users_cleanup.STORAGE_TYPE, 'owner',
                                                 'TO BE DELETED', 'Skipped due to dry run'))

        text = notifier.build_text()

        self.assertIn('<td>Failed to terminate paused run: boom</td>', text)
        self.assertIn('<td>Skipped due to dry run</td>', text)
        self.assertNotIn('Event object', text)


if __name__ == '__main__':
    unittest.main()
