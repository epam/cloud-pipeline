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

# Covers the error of the Spark UI proxy (shell/spark_setup_ui, a Python 2 script without the .py suffix) for a response
# it does not support. The request to the Spark UI is a stub.

import imp
import os
import sys
import unittest

from mock import Mock, patch

SPARK_SETUP_UI = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell', 'spark_setup_ui')


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
        # urllib2 raises HTTPError for 4xx and 5xx, and follows the redirects: a 2xx other than 200 gets here
        response = Mock()
        response.getcode.return_value = 204
        with patch.object(self.spark_setup_ui.urllib2, 'urlopen', return_value=response) as urlopen:
            with self.assertRaises(Exception) as raised:
                self.handler.proxyRequest(None)

        urlopen.assert_called_once_with('http://worker:8081/', None)
        self.assertIs(Exception, type(raised.exception))
        self.assertEqual('Unsupported response: 204', str(raised.exception))


if __name__ == '__main__':
    unittest.main()
