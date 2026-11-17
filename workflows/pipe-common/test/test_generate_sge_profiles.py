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

# Covers the grid engine profiles that "sge create" generates (scripts/generate_sge_profiles.py): the default queue
# and the additional ones, set by the CP_CAP_SGE_QUEUE_NAME_<index> environment variables.

import os
import unittest

from mock import Mock, patch

from scripts.generate_sge_profiles import _generate_profiles


class GenerateProfilesTest(unittest.TestCase):

    def _generate(self, environment):
        with patch.dict(os.environ, environment, clear=True):
            return _generate_profiles(default_queue_disabled=False, logger=Mock())

    def test_additional_queue_gets_its_name_and_hostlist(self):
        profiles = self._generate({'CP_CAP_SGE_QUEUE_NAME_1730000000': 'testprofile.q'})

        self.assertEqual('main.q', profiles['common']['CP_CAP_SGE_QUEUE_NAME'])
        self.assertEqual('@allhosts', profiles['common']['CP_CAP_SGE_HOSTLIST_NAME'])
        self.assertEqual('testprofile.q', profiles['1730000000']['CP_CAP_SGE_QUEUE_NAME'])
        self.assertEqual('@testprofile.q', profiles['1730000000']['CP_CAP_SGE_HOSTLIST_NAME'])
        self.assertEqual('false', profiles['1730000000']['CP_CAP_AUTOSCALE'])

    def test_additional_queues_with_the_same_name_get_different_names(self):
        profiles = self._generate({'CP_CAP_SGE_QUEUE_NAME_1': 'testprofile.q',
                                   'CP_CAP_SGE_QUEUE_NAME_2': 'testprofile.q'})

        queue_names = [profiles[index]['CP_CAP_SGE_QUEUE_NAME'] for index in ['1', '2']]
        self.assertEqual(2, len(set(queue_names)))
        for index in ['1', '2']:
            self.assertEqual('@' + profiles[index]['CP_CAP_SGE_QUEUE_NAME'],
                             profiles[index]['CP_CAP_SGE_HOSTLIST_NAME'])


if __name__ == '__main__':
    unittest.main()
