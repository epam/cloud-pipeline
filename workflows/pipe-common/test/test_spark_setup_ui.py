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

# Covers the Spark UI proxy (shell/spark_setup_ui, a Python script without the .py suffix): the error for a response
# it does not support, and the link rewriting. The request to the Spark UI is a stub.

import os
import unittest

from mock import Mock, patch

SPARK_SETUP_UI = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell', 'spark_setup_ui')

try:
    import importlib.machinery
    import importlib.util

    def _load_spark_setup_ui():
        loader = importlib.machinery.SourceFileLoader('spark_setup_ui', SPARK_SETUP_UI)
        spec = importlib.util.spec_from_loader('spark_setup_ui', loader)
        module = importlib.util.module_from_spec(spec)
        loader.exec_module(module)
        return module
except ImportError:
    import imp
    import sys

    def _load_spark_setup_ui():
        # imp.load_source would write the compiled script next to it, as shell/spark_setup_uic
        dont_write_bytecode = sys.dont_write_bytecode
        sys.dont_write_bytecode = True
        try:
            return imp.load_source('spark_setup_ui', SPARK_SETUP_UI)
        finally:
            sys.dont_write_bytecode = dont_write_bytecode


class UnsupportedResponseTest(unittest.TestCase):

    def setUp(self):
        self.spark_setup_ui = _load_spark_setup_ui()

        # A handler with no request behind it. The handler is a classic class on Python 2: object.__new__ fails there
        class _Handler(self.spark_setup_ui.ProxyHandler):
            def __init__(self):
                self.path = '/proxy:worker:8081/'
        self.handler = _Handler()

    def test_names_the_response_code(self):
        # urlopen raises HTTPError for 4xx and 5xx, and follows the redirects: a 2xx other than 200 gets here
        response = Mock()
        response.getcode.return_value = 204
        with patch.object(self.spark_setup_ui.urllib_request, 'urlopen', return_value=response) as urlopen:
            with self.assertRaises(Exception) as raised:
                self.handler.proxyRequest(None)

        urlopen.assert_called_once_with('http://worker:8081/', None)
        self.assertIs(Exception, type(raised.exception))
        self.assertEqual('Unsupported response: 204', str(raised.exception))


class RewriteLinksTest(unittest.TestCase):

    def setUp(self):
        # A handler with no request behind it. The handler is a classic class on Python 2: object.__new__ fails there
        class _Handler(_load_spark_setup_ui().ProxyHandler):
            def __init__(self):
                pass
        self.handler = _Handler()

    def test_rewrites_the_links_of_a_page(self):
        self.assertEqual(b'<a href="/proxy:host:8080/jobs">',
                         self.handler.rewriteLinks(b'<a href="/jobs">', 'host:8080'))

    def test_keeps_the_text_of_a_page_that_is_not_ascii(self):
        page = u'<p>caf\xe9</p><img src="/static/logo.png">'.encode('utf-8')
        self.assertEqual(u'<p>caf\xe9</p><img src="/proxy:host:8080/static/logo.png">'.encode('utf-8'),
                         self.handler.rewriteLinks(page, 'host:8080'))

    def test_passes_a_binary_response_as_is(self):
        png = b'\x89PNG\r\n\x1a\n\x00\xff href="/x'
        self.assertEqual(png, self.handler.rewriteLinks(png, 'host:8080'))


if __name__ == '__main__':
    unittest.main()
