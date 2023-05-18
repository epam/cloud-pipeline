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

import os
import shutil
import subprocess
from collections import namedtuple
from time import sleep as real_sleep

import pytest
from unittest.mock import MagicMock, patch

from src.utilities.storage import mount as mount_module
from src.utilities.storage.mount import Mount

MOUNT_POINT = '/cloud-data/bucket'
Partition = namedtuple('Partition', ['device', 'mountpoint'])


@pytest.fixture(autouse=True)
def no_sleep():
    with patch.object(mount_module.time, 'sleep') as sleep:
        yield sleep


@pytest.fixture(autouse=True)
def mount_delay(monkeypatch):
    monkeypatch.setenv('CP_PIPE_FUSE_MOUNT_DELAY', '500')


@pytest.fixture(autouse=True)
def not_windows():
    with patch.object(mount_module.platform, 'system', return_value='Linux'):
        yield


@pytest.fixture
def fuse_child():
    child = MagicMock()
    with patch.object(mount_module.psutil, 'Process') as process_class, \
            patch.object(mount_module.psutil, 'wait_procs') as wait_procs:
        process_class.return_value.children.return_value = [child]
        wait_procs.return_value = ([child], [])
        child.wait_procs = wait_procs
        yield child


def process(returncode=None):
    proc = MagicMock()
    proc.poll.return_value = returncode
    proc.returncode = returncode
    return proc


def partitions(device, mountpoint=MOUNT_POINT):
    return patch.object(mount_module.psutil, 'disk_partitions',
                        return_value=[Partition('/dev/sda1', '/'), Partition(device, mountpoint)])


def mounted_after(checks):
    return patch.object(mount_module.os.path, 'ismount', side_effect=[False] * (checks - 1) + [True])


def realpath_is_itself():
    return patch.object(mount_module.os.path, 'realpath', side_effect=lambda path: path)


def test_wait_returns_once_mount_point_is_mounted(no_sleep):
    proc = process()
    with mounted_after(3), partitions(Mount.PIPE_FUSE_FS_NAME), realpath_is_itself():
        Mount()._wait_mount_point(10000, proc, MOUNT_POINT)
    assert no_sleep.call_count == 3
    no_sleep.assert_called_with(0.5)
    proc.terminate.assert_not_called()


def test_wait_uses_the_configured_delay(no_sleep, monkeypatch):
    monkeypatch.setenv('CP_PIPE_FUSE_MOUNT_DELAY', '200')
    with mounted_after(1), partitions(Mount.PIPE_FUSE_FS_NAME), realpath_is_itself():
        Mount()._wait_mount_point(10000, process(), MOUNT_POINT)
    no_sleep.assert_called_once_with(0.2)


def test_wait_fails_when_mount_process_exits(capsys):
    with patch.object(mount_module.os.path, 'ismount', return_value=False), pytest.raises(SystemExit) as exit_info:
        Mount()._wait_mount_point(10000, process(returncode=3), MOUNT_POINT)
    assert exit_info.value.code == 1
    assert 'Mount command exited with return code: 3' in capsys.readouterr().err


def test_wait_fails_on_timeout_and_stops_the_fuse_process_first(no_sleep, fuse_child, capsys):
    proc = process()
    with patch.object(mount_module.os.path, 'ismount', return_value=False), pytest.raises(SystemExit) as exit_info:
        Mount()._wait_mount_point(2000, proc, MOUNT_POINT)
    assert exit_info.value.code == 1
    assert no_sleep.call_count == 4
    fuse_child.terminate.assert_called_once_with()
    fuse_child.wait_procs.assert_called_once_with([fuse_child], timeout=mount_module.MOUNT_STOP_TIMEOUT_SEC)
    proc.wait.assert_called_once_with(timeout=mount_module.MOUNT_STOP_TIMEOUT_SEC)
    proc.terminate.assert_not_called()
    assert 'timeout expired' in capsys.readouterr().err


def test_stop_terminates_the_mount_process_that_does_not_exit(fuse_child):
    proc = process()
    proc.wait.side_effect = subprocess.TimeoutExpired('bash', 5)
    Mount._stop_mount_proc(proc)
    fuse_child.terminate.assert_called_once_with()
    proc.terminate.assert_called_once_with()


def test_stop_ignores_processes_that_have_already_exited():
    proc = process()
    with patch.object(mount_module.psutil, 'Process', side_effect=mount_module.psutil.NoSuchProcess(1)), \
            patch.object(mount_module.psutil, 'wait_procs', return_value=([], [])) as wait_procs:
        Mount._stop_mount_proc(proc)
    wait_procs.assert_called_once_with([], timeout=mount_module.MOUNT_STOP_TIMEOUT_SEC)
    proc.wait.assert_called_once_with(timeout=mount_module.MOUNT_STOP_TIMEOUT_SEC)


def test_stop_continues_when_a_child_has_already_exited(fuse_child):
    fuse_child.terminate.side_effect = mount_module.psutil.NoSuchProcess(2)
    proc = process()
    Mount._stop_mount_proc(proc)
    proc.wait.assert_called_once_with(timeout=mount_module.MOUNT_STOP_TIMEOUT_SEC)


def test_wait_checks_at_least_once_with_a_timeout_below_the_delay(no_sleep):
    with mounted_after(1), partitions(Mount.PIPE_FUSE_FS_NAME), realpath_is_itself():
        Mount()._wait_mount_point(100, process(), MOUNT_POINT)
    no_sleep.assert_called_once_with(0.5)


def test_stop_kills_a_child_that_does_not_exit(fuse_child):
    fuse_child.wait_procs.return_value = ([], [fuse_child])
    Mount._stop_mount_proc(process())
    fuse_child.terminate.assert_called_once_with()
    fuse_child.kill.assert_called_once_with()


def test_wait_fails_and_stops_fuse_when_something_else_stays_mounted(no_sleep, fuse_child, capsys):
    with patch.object(mount_module.os.path, 'ismount', return_value=True), partitions('overlay'), \
            realpath_is_itself(), pytest.raises(SystemExit) as exit_info:
        Mount()._wait_mount_point(1000, process(), MOUNT_POINT)
    assert exit_info.value.code == 1
    assert no_sleep.call_count == 2
    assert 'unexpected FS name: overlay; expected: PIPE_FUSE.' in capsys.readouterr().err
    fuse_child.terminate.assert_called_once_with()


def test_wait_fails_and_stops_fuse_when_fs_name_cannot_be_determined(fuse_child, capsys):
    with patch.object(mount_module.os.path, 'ismount', return_value=True), \
            partitions('PIPE_FUSE', mountpoint='/other'), realpath_is_itself(), \
            pytest.raises(SystemExit) as exit_info:
        Mount()._wait_mount_point(1000, process(), MOUNT_POINT)
    assert exit_info.value.code == 1
    assert 'failed to determine FS name' in capsys.readouterr().err
    fuse_child.terminate.assert_called_once_with()


def test_wait_waits_for_pipe_fuse_on_top_of_another_mount(no_sleep):
    stacked = [[Partition('overlay', MOUNT_POINT)],
               [Partition('overlay', MOUNT_POINT), Partition(Mount.PIPE_FUSE_FS_NAME, MOUNT_POINT)]]
    proc = process()
    with patch.object(mount_module.os.path, 'ismount', return_value=True), realpath_is_itself(), \
            patch.object(mount_module.psutil, 'disk_partitions', side_effect=stacked):
        Mount()._wait_mount_point(10000, proc, MOUNT_POINT)
    assert no_sleep.call_count == 2
    proc.terminate.assert_not_called()


def test_linux_fs_name_is_the_top_mount():
    with patch.object(mount_module.psutil, 'disk_partitions',
                      return_value=[Partition('overlay', MOUNT_POINT), Partition('PIPE_FUSE', MOUNT_POINT),
                                    Partition('tmpfs', '/other')]):
        assert Mount._get_fs_name_linux(MOUNT_POINT) == 'PIPE_FUSE'


@pytest.mark.parametrize('system,expected', [('Darwin', 'PIPE_FUSE'), ('Linux', None)])
def test_fs_name_ignores_the_case_on_macos_only(system, expected):
    with patch.object(mount_module.platform, 'system', return_value=system), \
            partitions('PIPE_FUSE', mountpoint='/Users/user/Data'):
        assert Mount._get_fs_name_linux('/Users/user/data') == expected


def test_wait_checks_the_target_of_a_symlinked_mount_point():
    link, target = '/home/user/data', MOUNT_POINT
    with patch.object(mount_module.os.path, 'realpath', side_effect=lambda path: target if path == link else path), \
            patch.object(mount_module.os.path, 'ismount', side_effect=lambda path: path == target), \
            partitions(Mount.PIPE_FUSE_FS_NAME, mountpoint=target):
        Mount()._wait_mount_point(10000, process(), link)


def test_wait_checks_the_given_mount_point_on_windows():
    with patch.object(mount_module.platform, 'system', return_value='Windows'), \
            patch.object(mount_module.os.path, 'realpath', side_effect=lambda path: 'C:\\resolved\\'), \
            patch.object(mount_module.os.path, 'ismount', side_effect=lambda path: path == 'Z:') as ismount, \
            patch.object(Mount, '_get_fs_name_windows', return_value=Mount.PIPE_FUSE_FS_NAME):
        Mount()._wait_mount_point(10000, process(), 'Z:')
    ismount.assert_called_once_with('Z:')


@pytest.mark.parametrize('delay', ['', 'abc', '0', '-5', '0.5'])
def test_invalid_mount_delay_falls_back_to_the_default(no_sleep, monkeypatch, delay):
    monkeypatch.setenv('CP_PIPE_FUSE_MOUNT_DELAY', delay)
    with mounted_after(1), partitions(Mount.PIPE_FUSE_FS_NAME), realpath_is_itself():
        Mount()._wait_mount_point(10000, process(), MOUNT_POINT)
    no_sleep.assert_called_once_with(0.5)


def test_linux_fs_name_ignores_a_trailing_separator():
    with partitions(Mount.PIPE_FUSE_FS_NAME):
        assert Mount._get_fs_name_linux(MOUNT_POINT + os.path.sep) == Mount.PIPE_FUSE_FS_NAME


def test_run_starts_fuse_with_the_fs_name_and_waits_for_the_mount_point():
    config = MagicMock(api='https://cp/restapi/', proxy=None)
    config.get_token.return_value = 'token'
    proc = process()
    with patch.object(mount_module.subprocess, 'Popen', return_value=proc) as popen, \
            patch.object(Mount, '_wait_mount_point') as wait:
        Mount().run(config, ['pipe-fuse'], MOUNT_POINT, mount_timeout=3000)
    env = popen.call_args[1]['env']
    assert env['CP_PIPE_FUSE_FS_NAME'] == Mount.PIPE_FUSE_FS_NAME
    assert env['API_TOKEN'] == 'token'
    wait.assert_called_once_with(3000, proc, MOUNT_POINT)


def test_storage_mount_passes_the_mount_point_and_timeout():
    mount = MagicMock()
    mount.get_mount_storage_cmd.return_value = ['pipe-fuse']
    mount.get_python_path.return_value = None
    with patch.object(Mount, 'run') as run:
        Mount().mount_storage(mount, MagicMock(), MOUNT_POINT, None, None, 'bucket', 700, timeout=4000)
    run.assert_called_once_with(run.call_args[0][0], ['pipe-fuse'], MOUNT_POINT, python_path=None, log_file=None,
                                mount_timeout=4000)


def test_default_timeout_is_ten_seconds():
    with patch.object(Mount, 'mount_storage') as mount_storage, \
            patch.object(mount_module.Config, 'instance'), \
            patch.object(mount_module, 'is_frozen', return_value=True), \
            patch.object(mount_module, 'FrozenMount'):
        Mount().mount_storages(MOUNT_POINT, bucket='bucket')
    assert mount_storage.call_args[1]['timeout'] == 10000


def test_storage_operations_default_timeout_is_ten_seconds():
    from src.utilities.datastorage_operations import DataStorageOperations
    with patch('src.utilities.datastorage_operations.Mount') as mount:
        DataStorageOperations.mount_storage(MOUNT_POINT, bucket='bucket')
    assert mount.return_value.mount_storages.call_args[1]['timeout'] == 10000


def test_cli_default_timeout_is_ten_seconds():
    import pipe
    timeout = [param for param in pipe.mount_storage.params if param.name == 'timeout'][0]
    assert timeout.default == 10000


@pytest.mark.skipif(os.name == 'nt' or not shutil.which('bash'), reason='the wrapper script is a bash script')
def test_stop_lets_the_wrapper_script_clean_up(tmp_path, no_sleep):
    no_sleep.side_effect = real_sleep
    cleaned = tmp_path / 'cleaned'
    script = tmp_path / 'pipe-fuse-script'
    script.write_text('sleep 60\ntouch {}\n'.format(cleaned))
    proc = subprocess.Popen(['bash', str(script)])
    try:
        children = []
        for _ in range(50):
            children = mount_module.psutil.Process(proc.pid).children(recursive=True)
            if children:
                break
            real_sleep(0.1)
        assert children
        Mount._stop_mount_proc(proc)
        assert proc.poll() is not None
        assert not [child for child in children if child.is_running()]
        assert cleaned.exists()
    finally:
        if proc.poll() is None:
            proc.kill()
