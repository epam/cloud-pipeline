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

# Covers the SLURM queue profiles (scripts/generate_slurm_profiles.py): the default queue and the additional ones,
# set by the CP_CAP_SLURM_QUEUE_NAME_<index> and CP_CAP_AUTOSCALE_INSTANCE_TYPE_<index> environment variables.

import os
import unittest

from mock import Mock, patch

from scripts.generate_slurm_profiles import _generate_profiles


class GenerateProfilesTest(unittest.TestCase):

    def _generate(self, environment, default_queue_disabled=False):
        with patch.dict(os.environ, dict({'instance_size': 'm5.large'}, **environment), clear=True):
            return _generate_profiles(default_queue_disabled=default_queue_disabled, logger=Mock())

    def test_additional_queue_gets_its_name_and_hostlist(self):
        profiles = self._generate({'CP_CAP_SLURM_QUEUE_NAME_1730000000': 'testprofile.q'})

        self.assertEqual('main.q', profiles['common']['CP_CAP_SLURM_QUEUE_NAME'])
        self.assertEqual('@allhosts', profiles['common']['CP_CAP_SLURM_HOSTLIST_NAME'])
        self.assertEqual('testprofile.q', profiles['1730000000']['CP_CAP_SLURM_QUEUE_NAME'])
        self.assertEqual('@testprofile.q', profiles['1730000000']['CP_CAP_SLURM_HOSTLIST_NAME'])
        self.assertEqual('false', profiles['1730000000']['CP_CAP_AUTOSCALE'])
        self.assertEqual('false', profiles['1730000000']['CP_CAP_SGE'])

    def test_additional_queue_is_named_after_its_instance_type(self):
        profiles = self._generate({'CP_CAP_AUTOSCALE_INSTANCE_TYPE_1': 'c5.xlarge'})

        self.assertEqual('c5.xlarge.q', profiles['1']['CP_CAP_SLURM_QUEUE_NAME'])
        self.assertEqual('@c5.xlarge.q', profiles['1']['CP_CAP_SLURM_HOSTLIST_NAME'])

    def test_additional_queues_with_the_same_name_get_different_names(self):
        profiles = self._generate({'CP_CAP_AUTOSCALE_INSTANCE_TYPE_1': 'c5.xlarge',
                                   'CP_CAP_AUTOSCALE_INSTANCE_TYPE_2': 'c5.xlarge'})

        self.assertEqual(['c5.xlarge.1.q', 'c5.xlarge.2.q'],
                         sorted(profiles[index]['CP_CAP_SLURM_QUEUE_NAME'] for index in ['1', '2']))
        for index in ['1', '2']:
            self.assertEqual('@' + profiles[index]['CP_CAP_SLURM_QUEUE_NAME'],
                             profiles[index]['CP_CAP_SLURM_HOSTLIST_NAME'])

    def test_default_queue_can_be_disabled(self):
        profiles = self._generate({'CP_CAP_SLURM_QUEUE_NAME_1': 'gpu.q'}, default_queue_disabled=True)

        self.assertEqual(['1'], list(profiles))
        self.assertEqual('gpu.q', profiles['1']['CP_CAP_SLURM_QUEUE_NAME'])


if __name__ == '__main__':
    unittest.main()
