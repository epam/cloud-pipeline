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

import unittest

from internal.compat import to_bytes
from internal.model.git_project import GitProject


class ToBytesTest(unittest.TestCase):

    def test_keeps_a_native_str(self):
        self.assertEqual('Group name', to_bytes('Group name'))
        self.assertIsInstance(to_bytes('Group name'), str)

    def test_keeps_none(self):
        self.assertIsNone(to_bytes(None))

    def test_project_fields_stay_str(self):
        project = GitProject.load({'id': 1, 'name': 'pipeline-1', 'path_with_namespace': 'group/pipeline 1'})
        self.assertEqual('pipeline-1', project.name)
        self.assertEqual('group%2Fpipeline+1', project.path_with_namespace)


if __name__ == '__main__':
    unittest.main()
