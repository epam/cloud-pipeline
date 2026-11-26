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

"""
The pipe-fuse disk buffer downloads a whole file once, in parallel ranges, and serves reads from disk.

The ranges go straight into a partial file, so no range is held in memory, and the buffer appears
only once the whole file is there.

The tests need pipe-fuse's own dependencies, pipe-cli/mount/requirements.txt.
pipe-cli/requirements.txt alone does not bring them, so the module is skipped there.
"""

import io
import os
import runpy
import sys
import threading
import types
from unittest.mock import MagicMock, patch

import pytest

# pipe-fuse imports its modules as the top level pipefuse package, as pipe-cli/mount/pipe-fuse.py does.
MOUNT_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'mount')
sys.path.append(MOUNT_DIR)

# pipe-fuse dependencies that pipe-cli/requirements.txt does not bring
for _module in ['cachetools', 'intervals']:
    pytest.importorskip(_module)

try:
    import fuse  # noqa: F401
except (ImportError, EnvironmentError):
    # fusepy is not in the CLI requirements, and it loads libfuse on import.
    # These tests do not mount anything, so a stand-in is enough, as in test_mount_clients.py.
    _fuse = types.ModuleType('fuse')

    class FuseOSError(OSError):
        def __init__(self, errno):
            super(FuseOSError, self).__init__(errno, os.strerror(errno))

    class Operations(object):
        pass

    _fuse.FuseOSError = FuseOSError
    _fuse.Operations = Operations
    sys.modules['fuse'] = _fuse

# pipe-fuse.py itself imports more of fusepy than the other modules do.
# Another test module may have put a smaller stand-in into sys.modules already, so fill in what it lacks.
for _name in ['FUSE', 'fuse_operations', 'fuse_file_info', 'c_utimbuf']:
    if not hasattr(sys.modules['fuse'], _name):
        setattr(sys.modules['fuse'], _name, type(_name, (object,), {}))
if not hasattr(sys.modules['fuse'], 'fuse_get_context'):
    sys.modules['fuse'].fuse_get_context = lambda: (0, 0, 0)

from pipefuse import diskread  # noqa: E402
from pipefuse.diskread import DiskBufferingReadAllFileSystemClient  # noqa: E402
from pipefuse.fuseutils import MB  # noqa: E402
from pipefuse.s3 import S3StorageLowLevelClient  # noqa: E402

PATH = '/folder/file.bin'
CONTENT = bytes(bytearray(i % 251 for i in range(1000)))
FH = 7


class SourceClient(object):
    """The inner file system client: a single file, served by ranges."""

    def __init__(self, content=CONTENT, on_download=None):
        self.content = content
        self.ranges = []
        self.buffers = []
        self._on_download = on_download
        self._lock = threading.Lock()

    def attrs(self, path):
        return MagicMock(size=len(self.content))

    def download_range(self, fh, buf, path, offset=0, length=0):
        with self._lock:
            self.ranges.append((offset, length))
            self.buffers.append(buf)
        if self._on_download:
            self._on_download(offset, length)
        data = self.content[offset:offset + length]
        # pipe-fuse's storage clients write a range in 1 MB pieces, never as a whole
        for start in range(0, len(data), 100):
            buf.write(data[start:start + 100])


def _read(client, offset, length):
    buf = io.BytesIO()
    client.download_range(FH, buf, PATH, offset, length)
    return buf.getvalue()


def _buffer_files(buffer_dir):
    return sorted(os.path.relpath(os.path.join(root, name), str(buffer_dir))
                  for root, _, names in os.walk(str(buffer_dir)) for name in names)


def test_auto_chunks_split_the_file_into_one_range_per_worker(tmpdir):
    source = SourceClient()
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=4)

    assert _read(client, 100, 50) == CONTENT[100:150]

    assert sorted(source.ranges) == [(0, 250), (250, 250), (500, 250), (750, 250)]
    assert _buffer_files(tmpdir) == ['folder/file.bin']
    assert tmpdir.join('folder', 'file.bin').read_binary() == CONTENT


def test_fixed_chunks_cover_the_file_with_a_shorter_last_range(tmpdir):
    source = SourceClient()
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=300, path=str(tmpdir), download_workers=2)

    assert _read(client, 900, 500) == CONTENT[900:]

    assert sorted(source.ranges) == [(0, 300), (300, 300), (600, 300), (900, 100)]
    assert tmpdir.join('folder', 'file.bin').read_binary() == CONTENT


def test_one_worker_without_chunk_size_downloads_the_file_in_one_range(tmpdir):
    source = SourceClient()
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=None, path=str(tmpdir), download_workers=1)

    assert _read(client, 0, 10) == CONTENT[:10]

    assert source.ranges == [(0, 1000)]


def test_one_worker_downloads_fixed_chunks_in_order(tmpdir):
    source = SourceClient()
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=400, path=str(tmpdir), download_workers=1)

    assert _read(client, 0, 1000) == CONTENT

    assert source.ranges == [(0, 400), (400, 400), (800, 200)]


def test_workers_download_their_ranges_at_the_same_time(tmpdir):
    # Each range waits for all the others: a sequential download would break the barrier.
    barrier = threading.Barrier(4, timeout=10)
    source = SourceClient(on_download=lambda offset, length: barrier.wait())
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=4)

    assert _read(client, 0, 1000) == CONTENT

    assert len(source.ranges) == 4


def test_ranges_are_written_to_disk_and_not_kept_in_memory(tmpdir):
    source = SourceClient()
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=4)

    _read(client, 0, 10)

    assert len(source.buffers) == 4
    for buf in source.buffers:
        assert not isinstance(buf, io.BytesIO)
        assert os.path.dirname(buf.name) == str(tmpdir.join('folder'))
        assert buf.name.endswith('.part')


class LockStepSourceClient(SourceClient):
    """Starts every range at once, and finishes them one by one, from the first offset to the last."""

    def __init__(self, ranges, after_range):
        super(LockStepSourceClient, self).__init__()
        self._started = threading.Barrier(ranges, timeout=10)
        self._finished = dict((offset, threading.Event()) for offset in range(0, len(CONTENT), len(CONTENT) // ranges))
        self._after_range = after_range

    def download_range(self, fh, buf, path, offset=0, length=0):
        self._started.wait()
        previous = offset - length
        if previous >= 0:
            assert self._finished[previous].wait(timeout=10)
        super(LockStepSourceClient, self).download_range(fh, buf, path, offset, length)
        self._after_range(offset)
        self._finished[offset].set()


def test_buffer_appears_only_when_the_whole_file_is_downloaded(tmpdir):
    # Each range looks for the buffer when it has written its data, the last one after all the others
    seen = []
    buffer_path = str(tmpdir.join('folder', 'file.bin'))
    source = LockStepSourceClient(4, after_range=lambda offset: seen.append(os.path.exists(buffer_path)))
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=4)

    assert _read(client, 0, 1000) == CONTENT

    assert seen == [False] * 4
    assert os.path.exists(buffer_path)


def test_file_with_the_longest_name_is_buffered(tmpdir):
    # 255 bytes is the longest file name of the usual Linux file systems
    path = '/folder/' + 'n' * 255
    source = SourceClient()
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=4)

    buf = io.BytesIO()
    client.download_range(FH, buf, path, 0, 10)

    assert buf.getvalue() == CONTENT[:10]
    assert _buffer_files(tmpdir) == [os.path.join('folder', 'n' * 255)]


@pytest.fixture
def stopped():
    """Set once the disk buffer stops the download of a file."""
    stopped = threading.Event()

    class RecordingEvent(diskread.Event):

        def set(self):
            super(RecordingEvent, self).set()
            stopped.set()

    with patch.object(diskread, 'Event', RecordingEvent):
        yield stopped


class FailingSourceClient(SourceClient):
    """Fails the first range once another range has written a piece. The other ranges then try to go on."""

    def __init__(self, stopped):
        super(FailingSourceClient, self).__init__()
        self.pieces = []
        # Whether each range that went on saw the stop. An assert in a worker would only fail that worker.
        self.saw_stop = []
        self._piece_written = threading.Event()
        self._stopped = stopped

    def download_range(self, fh, buf, path, offset=0, length=0):
        with self._lock:
            self.ranges.append((offset, length))
        if offset == 0:
            assert self._piece_written.wait(timeout=10)
            raise RuntimeError('range failed')
        buf.write(self.content[offset:offset + 10])
        with self._lock:
            self.pieces.append(offset)
        self._piece_written.set()
        saw_stop = self._stopped.wait(timeout=10)
        with self._lock:
            self.saw_stop.append(saw_stop)
        buf.write(self.content[offset + 10:offset + length])
        with self._lock:
            self.pieces.append(offset + 10)


def test_failed_range_stops_the_running_ranges(tmpdir, stopped):
    source = FailingSourceClient(stopped)
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=2)

    with pytest.raises(RuntimeError, match='range failed'):
        _read(client, 0, 10)

    # The other range wrote its first piece only: the next one came after the stop
    assert source.saw_stop == [True]
    assert source.pieces == [500]
    assert _buffer_files(tmpdir) == []


def test_failed_range_stops_the_queued_ranges(tmpdir, stopped):
    source = FailingSourceClient(stopped)
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=100, path=str(tmpdir), download_workers=2)

    with pytest.raises(RuntimeError, match='range failed'):
        _read(client, 0, 10)

    # The worker of the failed range may take the next range before the stop, but no range after that starts
    started = sorted(offset for offset, _ in source.ranges)
    assert started in ([0, 100], [0, 100, 200])
    assert source.saw_stop and all(source.saw_stop)
    assert set(source.pieces) <= {100, 200}
    assert _buffer_files(tmpdir) == []


def test_next_reads_are_served_from_the_buffer(tmpdir):
    source = SourceClient()
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=4)
    _read(client, 0, 10)
    source.ranges[:] = []

    assert _read(client, 990, 100) == CONTENT[990:]
    assert _read(client, 1000, 10) == b''

    assert source.ranges == []


def test_failed_range_leaves_no_buffer_and_no_partial_file(tmpdir):
    def fail_second_range(offset, length):
        if offset == 250:
            raise RuntimeError('range failed')

    source = SourceClient(on_download=fail_second_range)
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=4)

    with pytest.raises(RuntimeError, match='range failed'):
        _read(client, 0, 10)

    assert _buffer_files(tmpdir) == []


def test_short_range_fails_the_download(tmpdir):
    source = SourceClient()
    source.attrs = lambda path: MagicMock(size=len(CONTENT) + 1)
    client = DiskBufferingReadAllFileSystemClient(source, read_ahead_size=0, path=str(tmpdir), download_workers=4)

    with pytest.raises(IOError, match='has 250 bytes instead of 251'):
        _read(client, 0, 10)

    assert _buffer_files(tmpdir) == []


def test_s3_client_allows_a_connection_per_download_worker():
    bucket_object = MagicMock(endpoint=None, region_name='eu-central-1')
    session = MagicMock()

    with patch.object(S3StorageLowLevelClient, '_generate_aws_session', return_value=session):
        S3StorageLowLevelClient('bucket', bucket_object, pipe=MagicMock(), chunk_size=10 * MB,
                                ca_bundle='/etc/ca.pem')

    _, kwargs = session.client.call_args
    assert kwargs['config'].max_pool_connections == 50
    assert kwargs['verify'] == '/etc/ca.pem'


@pytest.fixture(scope='module')
def pipe_fuse():
    return runpy.run_path(os.path.join(MOUNT_DIR, 'pipe-fuse.py'), run_name='pipe_fuse')


@pytest.mark.parametrize('read_ahead_size, download_workers, expected', [
    (None, 8, 0),
    (None, 1, 512 * MB),
    (64 * MB, 4, 64 * MB),
    (64 * MB, 1, 64 * MB),
    (0, 1, 0),
])
def test_disk_buffer_read_ahead_size(pipe_fuse, read_ahead_size, download_workers, expected):
    assert pipe_fuse['get_disk_buffer_read_ahead_size'](read_ahead_size, download_workers) == expected


@pytest.mark.parametrize('value, expected', [
    (None, 8),
    ('', 8),
    ('4', 4),
])
def test_int_env_falls_back_to_the_default_when_unset_or_empty(pipe_fuse, monkeypatch, value, expected):
    if value is None:
        monkeypatch.delenv('CP_PIPE_FUSE_READ_DISK_BUFFER_DOWNLOAD_WORKERS', raising=False)
    else:
        monkeypatch.setenv('CP_PIPE_FUSE_READ_DISK_BUFFER_DOWNLOAD_WORKERS', value)

    assert pipe_fuse['get_int_env']('CP_PIPE_FUSE_READ_DISK_BUFFER_DOWNLOAD_WORKERS', 8) == expected


@pytest.mark.parametrize('read_ahead_size, download_workers, expected_chunks', [
    (None, 8, [(i * 125, 125) for i in range(8)]),
    (None, 1, [(0, 1000)]),
    (400, 4, [(0, 400), (400, 400), (800, 200)]),
])
def test_disk_buffering_client_gets_the_options(pipe_fuse, tmpdir, read_ahead_size, download_workers,
                                                expected_chunks):
    source = SourceClient()
    client = pipe_fuse['create_disk_buffering_client'](source, path=str(tmpdir), read_ahead_size=read_ahead_size,
                                                       download_workers=download_workers)

    assert _read(client, 0, 1000) == CONTENT

    assert sorted(source.ranges) == expected_chunks


def test_pipe_fuse_accepts_the_download_workers_option(capsys):
    # The options pipe storage mount -c builds, as in issue 4937. Without --bucket the parser stops
    # right after parsing the arguments, so nothing is mounted.
    argv = ['pipe-fuse.py', '-p', '/cloud-data/storage',
            '--read-disk-buffer-path', '/pipe-storage-buffer/602',
            '--read-disk-buffer-download-workers', '4',
            '--read-disk-buffer-read-ahead-size', '67108864']

    with patch.object(sys, 'argv', argv), pytest.raises(SystemExit) as exit_info:
        runpy.run_path(os.path.join(MOUNT_DIR, 'pipe-fuse.py'), run_name='__main__')

    assert exit_info.value.code == 2
    stderr = capsys.readouterr().err
    assert 'unrecognized arguments' not in stderr
    assert 'Either --webdav or --bucket parameter should be specified.' in stderr
