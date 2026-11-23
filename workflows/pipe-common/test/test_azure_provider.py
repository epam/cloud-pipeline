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

# Covers the OS profile of an Azure node (pipeline/autoscaling/azureprovider.py): its custom data is the user data
# script in base64, as text - str on Python 3, str or unicode on Python 2.

import base64
import unittest

from pipeline.autoscaling.azureprovider import AzureInstanceProvider

TEXT_TYPES = (str, type(u''))


class OsProfileTest(unittest.TestCase):

    def test_custom_data_is_the_user_data_script_in_base64(self):
        provider = object.__new__(AzureInstanceProvider)
        user_data_script = '#!/bin/bash\necho "init"\n'

        profile = provider._AzureInstanceProvider__get_os_profile('az-node', 'ssh-rsa AAAA', 'pipeline',
                                                                  user_data_script, 'computer_name')

        self.assertIsInstance(profile['custom_data'], TEXT_TYPES)
        self.assertEqual(user_data_script.encode('utf-8'), base64.b64decode(profile['custom_data']))
        self.assertEqual('az-node', profile['computer_name'])


if __name__ == '__main__':
    unittest.main()
