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

import locale
import os
import sys
import types
import unittest

# The real watchdog, where it is installed: the observer test below checks the script against its API.
try:
    from watchdog.events import FileSystemEventHandler as _WatchdogEventHandler
    from watchdog.observers.api import BaseObserver as _WatchdogObserver, EventEmitter as _WatchdogEmitter, \
        ObservedWatch as _WatchdogObservedWatch
except ImportError:
    _WatchdogObservedWatch = None

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
# The stubs are removed again right after the import: pytest imports every test module before it runs
# any, so a stub left in sys.modules would shadow a real "import pipeline"/"psutil"/"watchdog.*" in the
# modules collected after this one. The script keeps the stub objects it has imported.
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

try:
    import watch_mount_shares as wms  # noqa: E402  (must follow the sys.modules stubbing above)
finally:
    for _name, _original in _ORIGINAL_MODULES.items():
        if _original is None:
            sys.modules.pop(_name, None)
        else:
            sys.modules[_name] = _original


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


class ExecuteCommandOutputThatIsNotUtf8Test(unittest.TestCase):
    # Python 3 decodes the output with the encoding of the locale and replaces a byte that is not valid there: with
    # UTF-8, the encoding of the test containers, it is U+FFFD. Python 2 keeps the bytes.

    _EXPECTED = b'caf\xe9\n'.decode(locale.getpreferredencoding(False), 'replace') \
        if sys.version_info[0] >= 3 else 'caf\xe9\n'

    def test_output_does_not_fail_the_command(self):
        exit_code, stdout, _ = wms.execute_cmd_command_and_get_stdout_stderr(r"printf 'caf\351\n'", silent=True)
        self.assertEqual(0, exit_code)
        self.assertEqual(self._EXPECTED, stdout)

    def test_output_does_not_fail_the_command_with_an_executable(self):
        exit_code, _, stderr = wms.execute_cmd_command_and_get_stdout_stderr(r"printf 'caf\351\n' 1>&2", silent=True,
                                                                             executable='/bin/bash')
        self.assertEqual(0, exit_code)
        self.assertEqual(self._EXPECTED, stderr)


def _without_init(cls):
    # An instance with no observer behind it. NFSMountWatcher is a classic class on Python 2: object.__new__ fails there
    class _Instance(cls):
        def __init__(self):
            pass
    return _Instance()


class _FileEvent(object):

    def __init__(self, event_type, src_path='/mnt/nfs/file.txt'):
        self.is_directory = False
        self.event_type = event_type
        self.src_path = src_path


class DispatchRecordsOnlyFileChangesTest(unittest.TestCase):
    # watchdog 6 reports the file accesses too, and "closed_no_write" starts with "c", the code of a created file

    def setUp(self):
        self.handler = _without_init(wms.CloudBucketDumpingEventHandler)
        self.handler._active_events = wms.OrderedDict()

    def _recorded_events(self):
        return [(path, event.event_type) for path, event in self.handler._active_events.items()]

    def test_a_read_of_a_file_records_nothing(self):
        for event_type in ['opened', 'closed_no_write', 'closed']:
            self.handler.dispatch(_FileEvent(event_type))
        self.assertEqual([], self._recorded_events())

    def test_a_modification_is_recorded(self):
        for event_type in ['opened', 'modified', 'closed']:
            self.handler.dispatch(_FileEvent(event_type))
        self.assertEqual([('/mnt/nfs/file.txt', wms.MODIFY_EVENT)], self._recorded_events())

    def test_a_creation_and_a_deletion_are_recorded(self):
        self.handler.dispatch(_FileEvent('created', '/mnt/nfs/new.txt'))
        self.handler.dispatch(_FileEvent('deleted', '/mnt/nfs/old.txt'))
        self.assertEqual([('/mnt/nfs/new.txt', wms.CREATE_EVENT), ('/mnt/nfs/old.txt', wms.DELETE_EVENT)],
                         self._recorded_events())


class RemoveUnmountedWatchersTest(unittest.TestCase):

    def _watcher(self, target_path_mapping):
        watcher = _without_init(wms.NFSMountWatcher)
        watcher._target_path_mapping = target_path_mapping
        return watcher

    def test_removes_several_mounts_in_one_pass(self):
        watcher = self._watcher({'/mnt/a': 'src-a', '/mnt/b': 'src-b', '/mnt/c': 'src-c'})
        watcher.try_to_remove_path_from_observer = lambda mnt_dest: True

        watcher._remove_unmounted_watchers({'/mnt/b': 'src-b'})

        self.assertEqual({'/mnt/b': 'src-b'}, watcher._target_path_mapping)

    @unittest.skipIf(_WatchdogObservedWatch is None, 'watchdog is not installed')
    def test_unschedules_the_watch_of_a_removed_mount(self):
        # The observer finds a watch by its path and recursive flag, and watchdog 6 takes the flag by keyword only
        watcher = self._watcher({'/mnt/a': 'src-a', '/mnt/b': 'src-b'})
        watcher._event_observer = _WatchdogObserver(_WatchdogEmitter)
        for mnt_dest in ['/mnt/a', '/mnt/b']:
            watcher._event_observer.schedule(_WatchdogEventHandler(), mnt_dest, recursive=True)
        script_observed_watch = wms.ObservedWatch
        wms.ObservedWatch = _WatchdogObservedWatch
        try:
            watcher._remove_unmounted_watchers({'/mnt/b': 'src-b'})
        finally:
            wms.ObservedWatch = script_observed_watch

        self.assertEqual({'/mnt/b': 'src-b'}, watcher._target_path_mapping)
        self.assertEqual(['/mnt/b'], [emitter.watch.path for emitter in watcher._event_observer.emitters])


class MaxAllowedWatchersTest(unittest.TestCase):

    def test_is_a_whole_number_of_watchers(self):
        # The share of the memory that the watchers may take, 1 KiB each, as Python 2 computed it
        memory = types.SimpleNamespace(total=16 * 1024 ** 3 + 1000)
        script_virtual_memory = wms.psutil.virtual_memory
        wms.psutil.virtual_memory = lambda: memory
        try:
            max_allowed_watchers = wms.NFSMountWatcher._calculate_max_allowed_watchers()
        finally:
            wms.psutil.virtual_memory = script_virtual_memory

        self.assertEqual((16 * 1024 ** 2) // 100 * wms.WATCHERS_USAGE_MEMORY_RATE, max_allowed_watchers)
        self.assertIsInstance(max_allowed_watchers, int)


if __name__ == '__main__':
    unittest.main()
