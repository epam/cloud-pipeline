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

# Covers the link rewriting of the Spark UI proxy (shell/spark_setup_ui, a Python script without the .py suffix).

import importlib.machinery
import importlib.util
import os
import unittest

SPARK_SETUP_UI = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell', 'spark_setup_ui')


def _load_spark_setup_ui():
    loader = importlib.machinery.SourceFileLoader('spark_setup_ui', SPARK_SETUP_UI)
    spec = importlib.util.spec_from_loader('spark_setup_ui', loader)
    module = importlib.util.module_from_spec(spec)
    loader.exec_module(module)
    return module


class RewriteLinksTest(unittest.TestCase):

    def setUp(self):
        self.handler = object.__new__(_load_spark_setup_ui().ProxyHandler)

    def test_rewrites_the_links_of_a_page(self):
        self.assertEqual(b'<a href="/proxy:host:8080/jobs">', self.handler.rewriteLinks(b'<a href="/jobs">', 'host:8080'))

    def test_passes_a_binary_response_as_is(self):
        png = b'\x89PNG\r\n\x1a\n\x00\xff href="/x'
        self.assertEqual(png, self.handler.rewriteLinks(png, 'host:8080'))


if __name__ == '__main__':
    unittest.main()
