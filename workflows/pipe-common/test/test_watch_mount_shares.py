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

# Covers execute_command()/execute_cmd_command_and_get_stdout_stderr() returning str under both
# Python 2 and 3 (issue #4531). Without universal_newlines=True, Popen.communicate() returns bytes
# under Python 3, and every caller downstream treats the result as str - this is exactly what broke
# once the NFS observer actually started running under Python 3 on cp-dav:
#   File "watch_mount_shares.py", line 439, in _get_target_mount_points
#     for line in out.split(NEWLINE):
#   TypeError: a bytes-like object is required, not 'str'
# plain unittest, not pytest+mock like this directory's other tests, so it runs under both
# interpreters without extra packages - run with `python -m unittest test_watch_mount_shares` (or
# under pytest) from inside workflows/pipe-common/test.

import os
import sys
import types
import unittest

SCRIPTS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'scripts')
sys.path.insert(0, SCRIPTS_DIR)


def _stub_module(name, **attrs):
    module = types.ModuleType(name)
    for key, value in attrs.items():
        setattr(module, key, value)
    sys.modules[name] = module
    return module


# watch_mount_shares.py imports "pipeline", "watchdog.*" and "psutil" at module level - none of
# which this test exercises, and none of which this test's environment is guaranteed to have
# installed. Stub them out so importing the module under test doesn't drag in the full pipe-common
# package tree (and its own, unrelated dependency chain) just to check subprocess output types.
#
# Saved so tearDownModule() below can restore sys.modules afterwards - left in place, a stub would
# otherwise shadow a real "import pipeline"/"psutil"/"watchdog.*" in any test collected after this
# one in the same process (pytest and unittest discovery both import every module into one process).
_STUBBED_MODULE_NAMES = ['psutil', 'watchdog', 'watchdog.events', 'watchdog.observers',
                        'watchdog.observers.inotify', 'watchdog.observers.api', 'pipeline']
_ORIGINAL_MODULES = dict((name, sys.modules.get(name)) for name in _STUBBED_MODULE_NAMES)

_stub_module('psutil', virtual_memory=lambda: None)
_stub_module('watchdog')
_stub_module('watchdog.events', FileSystemEventHandler=object, FileMovedEvent=object)
_stub_module('watchdog.observers')
_stub_module('watchdog.observers.inotify', InotifyObserver=object)
_stub_module('watchdog.observers.api', ObservedWatch=object)
_stub_module('pipeline', PipelineAPI=object)

import watch_mount_shares as wms  # noqa: E402  (must follow the sys.modules stubbing above)


def tearDownModule():
    for name, original in _ORIGINAL_MODULES.items():
        if original is None:
            sys.modules.pop(name, None)
        else:
            sys.modules[name] = original


class ExecuteCommandReturnsTextTest(unittest.TestCase):

    def test_stdout_is_text_not_bytes(self):
        out, success = wms.execute_command('echo hello')
        self.assertTrue(success)
        self.assertIsInstance(out, str)
        self.assertEqual('hello', out.strip())

    def test_split_on_newline_does_not_raise(self):
        # The exact call site that broke in production: _get_target_mount_points() does
        # "out.split(NEWLINE)" on execute_command()'s result.
        out, success = wms.execute_command('printf "a\\nb\\nc"')
        self.assertTrue(success)
        self.assertEqual(['a', 'b', 'c'], out.split(wms.NEWLINE))

    def test_stderr_is_text_on_failure(self):
        # execute_command()'s own failure-logging path does "stderr.rstrip(NEWLINE)" - same bug,
        # different call site.
        exit_code, stdout, stderr = wms.execute_cmd_command_and_get_stdout_stderr(
            'echo failure-message 1>&2; exit 1', silent=True)
        self.assertNotEqual(0, exit_code)
        self.assertIsInstance(stderr, str)
        self.assertEqual('failure-message', stderr.rstrip(wms.NEWLINE))


class CurrentUtcTimeMillisTest(unittest.TestCase):
    """Covers current_utc_time_millis() (issue #4531). current_utc_time() is tz-aware (via the
    _UTC shim added in commit 9f85dc0533's Python 3 pass), but current_utc_time_millis() subtracted
    a naive datetime(1970, 1, 1) from it, raising "can't subtract offset-naive and offset-aware
    datetimes" - reproduced in production the first time a real filesystem event was dispatched
    (Event.__init__ -> current_utc_time_millis()), on both Python 2 and 3 alike."""

    def test_does_not_raise(self):
        millis = wms.current_utc_time_millis()
        self.assertIsInstance(millis, int)

    def test_returns_a_plausible_epoch_millis_value(self):
        # Loose sanity bound (year ~2001 to ~2192 in epoch millis) - catches a wrong reference
        # point (e.g. "millis since 1970" accidentally computed as "millis since now") without
        # pinning an exact value that would make this test flaky.
        millis = wms.current_utc_time_millis()
        self.assertGreater(millis, 1000000000000)
        self.assertLess(millis, 7000000000000)


if __name__ == '__main__':
    unittest.main()
