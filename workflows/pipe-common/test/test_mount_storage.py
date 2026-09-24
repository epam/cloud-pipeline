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
import sys
import threading
import time

import pytest
from mock import MagicMock, Mock, patch

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'scripts'))

import mount_storage
from mount_storage import AzureMounter, MountStorageTask, StorageMounter, MOUNT_THREADS_ENV

MOUNT_ROOT = '/cloud-data'
TASK_NAME = 'MountDataStorages'
MOUNT_ENVS = [MOUNT_THREADS_ENV, 'CP_CAP_SKIP_MOUNTS', 'CP_CAP_FORCE_MOUNTS', 'CP_CAP_LIMIT_MOUNTS',
              'CP_SENSITIVE_RUN', 'CLOUD_REGION_ID', 'RUN_ID']


class MountRecorder:

    def __init__(self):
        self.lock = threading.Lock()
        self.started = []
        self.finished = []


def fake_mounter_class(recorder, behaviours=None):
    behaviours = behaviours or {}

    class FakeMounter(object):

        def __init__(self, api, storage, metadata, share_mount, sensitive_policy, mount_options=None):
            self.storage = storage

        @staticmethod
        def type():
            return 'S3'

        @staticmethod
        def check_or_install(task_name, sensitive_policy):
            pass

        @staticmethod
        def init_tmp_dir(tmp_dir, task_name):
            pass

        @staticmethod
        def is_available():
            return True

        def build_mount_point(self, mount_root):
            return self.storage.mount_point

        def mount(self, mount_root, task_name):
            with recorder.lock:
                recorder.started.append(self.storage.mount_point)
            behaviour = behaviours.get(self.storage.name)
            if behaviour:
                behaviour(self, recorder)
            with recorder.lock:
                recorder.finished.append(self.storage.mount_point)

    return FakeMounter


def storage(name, mount_point, storage_id=None):
    data_storage = Mock(id=storage_id or abs(hash(name)), mount_point=mount_point, path=name,
                        storage_type='S3', source_storage_id=None, mask=1, mount_disabled=False,
                        tools_to_mount=None, sensitive=False)
    # Mock takes a name argument as the name of the mock itself
    data_storage.name = name
    return data_storage


def mounters(mount_points, recorder=None, behaviours=None):
    mounter_class = fake_mounter_class(recorder or MountRecorder(), behaviours)
    return [mounter_class(None, storage(mount_point, mount_point), {}, None, None) for mount_point in mount_points]


def mount_points_of(waves):
    return [[mnt.build_mount_point(MOUNT_ROOT) for mnt in wave] for wave in waves]


def raise_runtime_error(mnt, recorder):
    raise RuntimeError('Failed mounting')


def raise_value_error(mnt, recorder):
    raise ValueError('Unexpected')


@pytest.fixture(autouse=True)
def clean_environment(monkeypatch):
    for env in MOUNT_ENVS:
        monkeypatch.delenv(env, raising=False)
    monkeypatch.setenv('API', 'https://cp/pipeline/restapi/')


@pytest.fixture
def logger():
    with patch.object(mount_storage, 'Logger') as logger_mock:
        yield logger_mock


@pytest.fixture
def task(logger):
    with patch.object(mount_storage, 'PipelineAPI'):
        yield MountStorageTask(TASK_NAME)


@pytest.mark.parametrize('value,expected', [(None, 1), ('', 1), ('1', 1), ('4', 4), ('16', 16)])
def test_mount_threads_are_parsed(task, logger, monkeypatch, value, expected):
    if value is not None:
        monkeypatch.setenv(MOUNT_THREADS_ENV, value)
    assert task._get_mount_threads() == expected
    logger.warn.assert_not_called()


@pytest.mark.parametrize('value', ['abc', '0', '-3', '2.5'])
def test_invalid_mount_threads_fall_back_to_one_thread(task, logger, monkeypatch, value):
    monkeypatch.setenv(MOUNT_THREADS_ENV, value)
    assert task._get_mount_threads() == 1
    logger.warn.assert_called_once()


def test_sequential_mount_keeps_the_given_order(task):
    recorder = MountRecorder()
    mount_points = ['/cloud-data/b', '/cloud-data/a', '/cloud-data/a/c']
    assert task._mount_sequentially(mounters(mount_points, recorder), MOUNT_ROOT) == []
    assert recorder.started == mount_points


def test_sequential_mount_collects_runtime_errors_and_continues(task):
    recorder = MountRecorder()
    failed = task._mount_sequentially(mounters(['/cloud-data/a', '/cloud-data/b', '/cloud-data/c'], recorder,
                                               {'/cloud-data/b': raise_runtime_error}), MOUNT_ROOT)
    assert failed == ['/cloud-data/b']
    assert recorder.finished == ['/cloud-data/a', '/cloud-data/c']


def test_sequential_mount_stops_on_unexpected_error(task):
    recorder = MountRecorder()
    with pytest.raises(ValueError):
        task._mount_sequentially(mounters(['/cloud-data/a', '/cloud-data/b', '/cloud-data/c'], recorder,
                                          {'/cloud-data/b': raise_value_error}), MOUNT_ROOT)
    assert recorder.started == ['/cloud-data/a', '/cloud-data/b']


def test_independent_mount_points_share_one_wave():
    waves = MountStorageTask._split_into_waves(mounters(['/cloud-data/a', '/cloud-data/b', '/opt/c']), MOUNT_ROOT)
    assert mount_points_of(waves) == [['/cloud-data/a', '/cloud-data/b', '/opt/c']]


def test_nested_mount_points_go_to_later_waves():
    waves = MountStorageTask._split_into_waves(
        mounters(['/cloud-data/a', '/cloud-data/a/b', '/cloud-data/a/b/c', '/cloud-data/d', '/cloud-data/d/e']),
        MOUNT_ROOT)
    assert mount_points_of(waves) == [['/cloud-data/a', '/cloud-data/d'],
                                      ['/cloud-data/a/b', '/cloud-data/d/e'],
                                      ['/cloud-data/a/b/c']]


def test_nested_mount_point_waits_for_an_outer_one_given_later():
    waves = MountStorageTask._split_into_waves(mounters(['/cloud-data/a/b', '/cloud-data/a']), MOUNT_ROOT)
    assert mount_points_of(waves) == [['/cloud-data/a'], ['/cloud-data/a/b']]


def test_common_name_prefix_is_not_nesting():
    waves = MountStorageTask._split_into_waves(mounters(['/cloud-data/a', '/cloud-data/ab']), MOUNT_ROOT)
    assert mount_points_of(waves) == [['/cloud-data/a', '/cloud-data/ab']]


def test_duplicate_mount_points_keep_their_order_in_separate_waves():
    waves = MountStorageTask._split_into_waves(mounters(['/cloud-data/a', '/cloud-data/a/', '/cloud-data/a/b']),
                                               MOUNT_ROOT)
    assert mount_points_of(waves) == [['/cloud-data/a'], ['/cloud-data/a/'], ['/cloud-data/a/b']]


def test_no_mounters_give_no_waves():
    assert MountStorageTask._split_into_waves([], MOUNT_ROOT) == []


def test_parallel_mount_runs_mounts_at_the_same_time(task):
    threads = 4
    all_started = threading.Event()
    recorder = MountRecorder()
    overlapped = []

    def wait_for_all(mnt, recorder):
        with recorder.lock:
            if len(recorder.started) == threads:
                all_started.set()
        overlapped.append(all_started.wait(5))

    mount_points = ['/cloud-data/{}'.format(i) for i in range(threads)]
    failed = task._mount_in_parallel(mounters(mount_points, recorder,
                                              {mount_point: wait_for_all for mount_point in mount_points}),
                                     MOUNT_ROOT, threads)
    assert failed == []
    assert overlapped == [True] * threads


def test_parallel_mount_mounts_outer_mount_point_first(task):
    recorder = MountRecorder()
    child_saw_parent_mounted = []

    def slow_mount(mnt, recorder):
        time.sleep(0.2)

    def check_parent(mnt, recorder):
        with recorder.lock:
            child_saw_parent_mounted.append('/cloud-data/a' in recorder.finished)

    task._mount_in_parallel(mounters(['/cloud-data/a', '/cloud-data/a/b', '/cloud-data/c'], recorder,
                                     {'/cloud-data/a': slow_mount, '/cloud-data/a/b': check_parent}),
                            MOUNT_ROOT, 4)
    assert child_saw_parent_mounted == [True]
    assert sorted(recorder.finished) == ['/cloud-data/a', '/cloud-data/a/b', '/cloud-data/c']


def test_parallel_mount_collects_all_errors_and_continues(task):
    recorder = MountRecorder()
    failed = task._mount_in_parallel(
        mounters(['/cloud-data/a', '/cloud-data/b', '/cloud-data/c', '/cloud-data/c/d'], recorder,
                 {'/cloud-data/a': raise_runtime_error, '/cloud-data/c': raise_value_error}),
        MOUNT_ROOT, 4)
    assert sorted(failed) == ['/cloud-data/a', '/cloud-data/c']
    assert sorted(recorder.finished) == ['/cloud-data/b', '/cloud-data/c/d']


def test_parallel_mount_with_no_mounters(task, logger):
    assert task._mount_in_parallel([], MOUNT_ROOT, 4) == []
    logger.info.assert_not_called()


def test_parallel_mount_uses_no_more_threads_than_the_largest_wave(task):
    with patch.object(mount_storage, 'ThreadPool', wraps=mount_storage.ThreadPool) as pool:
        task._mount_in_parallel(mounters(['/cloud-data/a', '/cloud-data/a/b', '/cloud-data/c']), MOUNT_ROOT, 1000)
    pool.assert_called_once_with(2)


def record_start_lock(locks):
    def record(mnt, recorder):
        locks.append(StorageMounter.start_lock)
    return record


def test_parallel_mount_starts_processes_under_a_lock_and_resets_it(task):
    locks = []
    task._mount_in_parallel(mounters(['/cloud-data/a', '/cloud-data/b'], behaviours={
        '/cloud-data/a': record_start_lock(locks), '/cloud-data/b': record_start_lock(locks)}), MOUNT_ROOT, 2)
    assert len(locks) == 2
    assert locks[0] is not None and locks[0] is locks[1]
    assert StorageMounter.start_lock is None


def test_sequential_mount_starts_processes_without_a_lock(task):
    locks = []
    task._mount_sequentially(mounters(['/cloud-data/a'], behaviours={'/cloud-data/a': record_start_lock(locks)}),
                             MOUNT_ROOT)
    assert locks == [None]


@pytest.mark.parametrize('start_lock', [None, threading.Lock()])
def test_mount_command_is_executed_with_the_start_lock(monkeypatch, logger, start_lock):
    monkeypatch.setenv('CP_CAP_MOUNT_SKIP_EXISTING', 'true')
    monkeypatch.setattr(StorageMounter, 'start_lock', start_lock)
    with patch.object(mount_storage.common, 'execute_cmd_command', side_effect=[1, 0]) as execute:
        StorageMounter.execute_mount('mount-command', {'path': 'bucket', 'mount': '/cloud-data/bucket'}, TASK_NAME)
    assert [c[0][0] for c in execute.call_args_list][1] == 'mount-command'
    assert [c[1]['start_lock'] for c in execute.call_args_list] == [start_lock, start_lock]


def test_azure_etc_hosts_rewrites_do_not_overlap():
    state = {'active': 0, 'max_active': 0}
    state_lock = threading.Lock()

    def execute(command, silent=False, start_lock=None):
        with state_lock:
            state['active'] += 1
            state['max_active'] = max(state['max_active'], state['active'])
        time.sleep(0.1)
        with state_lock:
            state['active'] -= 1
        return 0, '', ''

    azure_mounters = []
    for i in range(4):
        mnt = AzureMounter(None, storage('az{}'.format(i), '/cloud-data/az{}'.format(i)), {}, None, None)
        mnt._get_credentials = lambda s: ('account', 'key', 'region', None)
        azure_mounters.append(mnt)
    with patch.object(mount_storage.common, 'execute_cmd_command_and_get_stdout_stderr', side_effect=execute):
        workers = [threading.Thread(target=mnt._AzureMounter__resolve_azure_blob_service_url)
                   for mnt in azure_mounters]
        for worker in workers:
            worker.start()
        for worker in workers:
            worker.join()
    assert state['max_active'] == 1


def run_task(task, storages, recorder, behaviours=None):
    task.api.load_available_storages_with_share_mount.return_value = [
        Mock(storage=s, file_share_mount=None) for s in storages]
    task.api.get_preference.return_value = {}
    task.api.load_all_metadata_efficiently.return_value = []
    task.mounters = {'S3': fake_mounter_class(recorder, behaviours)}
    task.run(MOUNT_ROOT, '/tmp')


RUN_STORAGES = ['/cloud-data/b', '/cloud-data/a/c', '/cloud-data/a']


def test_run_mounts_one_by_one_in_mount_point_order_by_default(task, logger):
    recorder = MountRecorder()
    with patch.object(MountStorageTask, '_mount_in_parallel') as parallel:
        run_task(task, [storage(mp, mp, i + 1) for i, mp in enumerate(RUN_STORAGES)], recorder)
    parallel.assert_not_called()
    assert recorder.started == ['/cloud-data/a', '/cloud-data/a/c', '/cloud-data/b']
    logger.success.assert_called_once_with('Finished data storage mounting', task_name=TASK_NAME)


def test_run_stops_on_unexpected_error_by_default(task, logger):
    recorder = MountRecorder()
    with pytest.raises(SystemExit):
        run_task(task, [storage(mp, mp, i + 1) for i, mp in enumerate(RUN_STORAGES)], recorder,
                 {'/cloud-data/a/c': raise_value_error})
    assert recorder.started == ['/cloud-data/a', '/cloud-data/a/c']
    logger.fail.assert_called_once()


def test_run_mounts_in_parallel_with_several_threads(task, logger, monkeypatch):
    monkeypatch.setenv(MOUNT_THREADS_ENV, '4')
    recorder = MountRecorder()
    with patch.object(MountStorageTask, '_mount_sequentially') as sequential:
        run_task(task, [storage(mp, mp, i + 1) for i, mp in enumerate(RUN_STORAGES)], recorder)
    sequential.assert_not_called()
    assert sorted(recorder.finished) == ['/cloud-data/a', '/cloud-data/a/c', '/cloud-data/b']
    assert recorder.finished.index('/cloud-data/a') < recorder.started.index('/cloud-data/a/c')
    logger.success.assert_called_once_with('Finished data storage mounting', task_name=TASK_NAME)


def test_run_fails_with_failed_storages_in_parallel(task, logger, monkeypatch):
    monkeypatch.setenv(MOUNT_THREADS_ENV, '4')
    recorder = MountRecorder()
    with pytest.raises(SystemExit):
        run_task(task, [storage(mp, mp, i + 1) for i, mp in enumerate(RUN_STORAGES)], recorder,
                 {'/cloud-data/b': raise_runtime_error})
    logger.fail.assert_called_once_with('The following data storages have not been mounted: /cloud-data/b',
                                        task_name=TASK_NAME)
