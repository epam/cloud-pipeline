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

import json
import os
import unittest

os.environ['API'] = 'https://pipeline.example.com/restapi'
os.environ['API_TOKEN'] = 'test-token'

from internal.synchronization.git_server import GitServer  # noqa: E402


class GenerateSshKeysTest(unittest.TestCase):

    def test_keys_are_text_for_json_request_bodies(self):
        private_key, public_key = GitServer.generate_ssh_keys()
        self.assertIsInstance(private_key, str)
        self.assertIsInstance(public_key, str)
        self.assertTrue(private_key.startswith('-----BEGIN PRIVATE KEY-----'))
        self.assertTrue(public_key.startswith('ssh-rsa '))
        json.dumps({'key': public_key, 'private': private_key})


if __name__ == '__main__':
    unittest.main()
