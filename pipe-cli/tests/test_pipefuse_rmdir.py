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
rmdir() on a pipe-fuse mount removes only an empty folder.

A folder that holds anything is refused with ENOTEMPTY, as on a local disk. Tools such as git
remove parent folders until one is refused, so a mount that never refuses loses their content.

The tests need pipe-fuse's own dependencies, pipe-cli/mount/requirements.txt.
pipe-cli/requirements.txt alone does not bring them, so the module is skipped there.
"""

import errno
import os
import sys
import types
from datetime import datetime
from unittest.mock import MagicMock, patch

import pytest
from dateutil.tz import tzutc

# pipe-fuse imports its modules as the top level pipefuse package, as pipe-cli/mount/pipe-fuse.py does.
sys.path.append(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'mount'))

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

from cachetools import TTLCache  # noqa: E402
from pipefuse import fuseutils  # noqa: E402
from pipefuse.cache import CachingListingFileSystemClient, ListingCache  # noqa: E402
from pipefuse.fsclient import File, ForbiddenOperationException, NotEmptyOperationException  # noqa: E402
from pipefuse.gcp import GoogleStorageLowLevelFileSystemClient  # noqa: E402
from pipefuse.s3 import S3StorageLowLevelClient  # noqa: E402
from pipefuse.storage import StorageHighLevelFileSystemClient  # noqa: E402
from pipefuse.storage_path_permissions import StoragePathPermissionsFileSystemClient  # noqa: E402
from pipefuse.webdav import WebDavClient  # noqa: E402
from fuse import FuseOSError  # noqa: E402
from pipefuse.pipefs import PipeFS, ResilientFS  # noqa: E402

MARKER = '.DS_Store'
MODIFIED = datetime(2026, 1, 1, tzinfo=tzutc())


class NoLock(object):
    """The path lock of a single threaded mount."""

    def lock(self, path):
        pass

    def unlock(self, path):
        pass


def _file(name, is_dir=False):
    return File(name=name, size=0, mtime=None, ctime=None, contenttype='', is_dir=is_dir,
                storage_class=None, mask=None)


class FakeS3(object):
    """In-memory AWS S3 client: list_objects_v2 with a delimiter, put_object and delete_object."""

    def __init__(self, keys):
        self.keys = set(keys)
        self.deleted = []

    def get_paginator(self, name):
        assert name == 'list_objects_v2'
        return self

    def paginate(self, Bucket, Prefix='', Delimiter=None):
        contents = []
        prefixes = set()
        for key in sorted(self.keys):
            if not key.startswith(Prefix):
                continue
            rest = key[len(Prefix):]
            if Delimiter and Delimiter in rest:
                prefixes.add(Prefix + rest.split(Delimiter)[0] + Delimiter)
            else:
                contents.append({'Key': key, 'Size': 0, 'LastModified': MODIFIED, 'StorageClass': 'STANDARD'})
        page = {'IsTruncated': False}
        if contents:
            page['Contents'] = contents
        if prefixes:
            page['CommonPrefixes'] = [{'Prefix': prefix} for prefix in sorted(prefixes)]
        return [page]

    def put_object(self, Bucket, Key, Body, ACL):
        self.keys.add(Key)

    def delete_object(self, Bucket, Key):
        self.deleted.append(Key)
        self.keys.discard(Key)


class FakeGcsBlob(object):

    def __init__(self, bucket, name):
        self._bucket = bucket
        self.name = name
        self.size = 0
        self.updated = MODIFIED

    def delete(self):
        self._bucket.deleted.append(self.name)
        self._bucket.names.discard(self.name)


class FakeGcsBlobs(object):

    def __init__(self, blobs, prefixes):
        self._blobs = blobs
        self.prefixes = prefixes

    def __iter__(self):
        return iter(self._blobs)


class FakeGcs(object):
    """In-memory Google Cloud Storage client and bucket: list_blobs with a delimiter, blob and delete."""

    def __init__(self, names):
        self.names = set(names)
        self.deleted = []

    def bucket(self, name):
        return self

    def blob(self, name):
        return FakeGcsBlob(self, name)

    def list_blobs(self, prefix, delimiter=None):
        blobs = []
        prefixes = set()
        for name in sorted(self.names):
            if not name.startswith(prefix):
                continue
            rest = name[len(prefix):]
            if delimiter and delimiter in rest:
                prefixes.add(prefix + rest.split(delimiter)[0] + delimiter)
            else:
                blobs.append(FakeGcsBlob(self, name))
        return FakeGcsBlobs(blobs, prefixes)


def _s3_client(keys):
    s3 = FakeS3(keys)
    with patch.object(S3StorageLowLevelClient, '_generate_s3_client', return_value=s3):
        low_level_client = S3StorageLowLevelClient('bucket', bucket_object=MagicMock(), pipe=MagicMock(),
                                                   chunk_size=10)
    return StorageHighLevelFileSystemClient(low_level_client), s3


def _gcs_client(names):
    gcs = FakeGcs(names)
    with patch.object(GoogleStorageLowLevelFileSystemClient, '_generate_gcp', return_value=gcs):
        low_level_client = GoogleStorageLowLevelFileSystemClient('bucket', bucket_object=MagicMock(),
                                                                 pipe=MagicMock(), chunk_size=10)
    return StorageHighLevelFileSystemClient(low_level_client), gcs


@pytest.mark.parametrize('name,mac,hidden', [
    (MARKER, False, True),
    (MARKER, True, True),
    ('._keep.txt', False, False),
    ('._keep.txt', True, True),
    ('.localized', False, False),
    ('.localized', True, True),
    ('keep.txt', True, False),
    ('.hidden', True, False),
])
def test_is_hidden_file(name, mac, hidden):
    assert fuseutils.is_hidden_file(name, mac=mac) == hidden


@pytest.mark.parametrize('listing,paths', [
    ([], []),
    ([_file(MARKER)], ['/A/' + MARKER]),
    ([_file('A/'), _file(MARKER)], ['/A/', '/A/' + MARKER]),
    ([_file(MARKER), _file('keep.txt')], None),
    ([_file('sub/', is_dir=True)], None),
])
def test_get_empty_folder_paths(listing, paths):
    with patch.object(fuseutils, 'IS_MAC', False):
        assert fuseutils.get_empty_folder_paths('/A/', listing) == paths


class TestObjectStorageRmdir(object):

    @pytest.mark.parametrize('keys', [
        ['A/keep.txt'],
        ['A/sub/inner.txt'],
        ['A/' + MARKER, 'A/keep.txt'],
        ['A/' + MARKER, 'A/sub/' + MARKER],
        ['A/keep.txt', 'A/sub/inner.txt', 'B/neighbour.txt'],
    ])
    def test_refuses_folder_that_is_not_empty_and_deletes_nothing(self, keys):
        client, s3 = _s3_client(keys)

        with pytest.raises(NotEmptyOperationException):
            client.rmdir('/A')

        assert s3.deleted == []
        assert s3.keys == set(keys)

    def test_deletes_folder_marker_of_empty_folder(self):
        client, s3 = _s3_client(['A/' + MARKER, 'A_neighbour/keep.txt', 'B/' + MARKER])

        client.rmdir('/A')

        assert s3.deleted == ['A/' + MARKER]
        assert s3.keys == {'A_neighbour/keep.txt', 'B/' + MARKER}

    def test_removes_folder_created_by_mkdir(self):
        client, s3 = _s3_client([])

        client.mkdir('/A/B')
        client.rmdir('/A/B')

        assert s3.keys == set()

    def test_succeeds_for_folder_without_objects(self):
        # A folder with no marker disappears with its last file, e.g. during rm -r.
        client, s3 = _s3_client(['B/keep.txt'])

        client.rmdir('/A')

        assert s3.deleted == []
        assert s3.keys == {'B/keep.txt'}

    def test_stops_parent_folders_removal_at_first_folder_that_is_not_empty(self):
        # git removes a file, then calls rmdir() on each parent folder until one is refused.
        keys = ['C/repo/.git/worktrees/wt/logs/HEAD', 'C/repo/.git/worktrees/wt/HEAD', 'C/repo/data/untracked.txt',
                'C/neighbour/keep.txt']
        client, s3 = _s3_client(keys)

        client.delete('/C/repo/.git/worktrees/wt/logs/HEAD')
        removed = []
        for folder in ['/C/repo/.git/worktrees/wt/logs', '/C/repo/.git/worktrees/wt', '/C/repo/.git/worktrees',
                       '/C/repo/.git', '/C/repo', '/C']:
            try:
                client.rmdir(folder)
                removed.append(folder)
            except NotEmptyOperationException:
                break

        assert removed == ['/C/repo/.git/worktrees/wt/logs']
        assert s3.keys == set(keys) - {'C/repo/.git/worktrees/wt/logs/HEAD'}

    def test_deletes_macos_service_files_of_folder_that_looks_empty_on_macos(self):
        client, s3 = _s3_client(['A/' + MARKER, 'A/._keep.txt', 'A/.localized', 'B/keep.txt'])

        with patch.object(fuseutils, 'IS_MAC', True):
            client.rmdir('/A')

        assert s3.keys == {'B/keep.txt'}

    def test_refuses_folder_with_macos_service_files_on_other_platforms(self):
        keys = ['A/' + MARKER, 'A/._keep.txt']
        client, s3 = _s3_client(keys)

        with patch.object(fuseutils, 'IS_MAC', False):
            with pytest.raises(NotEmptyOperationException):
                client.rmdir('/A')

        assert s3.keys == set(keys)

    def test_refuses_gcs_folder_that_is_not_empty_and_deletes_nothing(self):
        names = ['A/', 'A/' + MARKER, 'A/keep.txt']
        client, gcs = _gcs_client(names)

        with pytest.raises(NotEmptyOperationException):
            client.rmdir('/A')

        assert gcs.deleted == []
        assert gcs.names == set(names)

    def test_deletes_gcs_folder_object_and_marker_of_empty_folder(self):
        client, gcs = _gcs_client(['A/', 'A/' + MARKER, 'B/keep.txt'])

        client.rmdir('/A')

        assert sorted(gcs.deleted) == ['A/', 'A/' + MARKER]
        assert gcs.names == {'B/keep.txt'}


class TestStoragePathPermissionsRmdir(object):

    def test_checks_write_permission_on_folder_marker_and_delegates(self):
        inner = MagicMock()
        inner.ls.return_value = [_file(MARKER)]
        manager = MagicMock()
        client = StoragePathPermissionsFileSystemClient(inner, manager)

        client.rmdir('/A')

        inner.ls.assert_called_once_with('/A/', depth=1)
        manager.check_path_access.assert_called_once_with('/A/' + MARKER, inner.attrs.return_value)
        inner.rmdir.assert_called_once_with('/A')
        inner.delete.assert_not_called()

    @pytest.mark.parametrize('listing', [
        [_file(MARKER), _file('keep.txt')],
        [_file(MARKER), _file('sub/', is_dir=True)],
        [_file(MARKER), _file('._keep.txt')],
    ])
    def test_refuses_folder_that_is_not_empty_before_permission_check(self, listing):
        inner = MagicMock()
        inner.ls.return_value = listing
        manager = MagicMock()
        manager.check_path_access.side_effect = ForbiddenOperationException()
        client = StoragePathPermissionsFileSystemClient(inner, manager)

        with patch.object(fuseutils, 'IS_MAC', False):
            with pytest.raises(NotEmptyOperationException):
                client.rmdir('/A')

        manager.check_path_access.assert_not_called()
        inner.rmdir.assert_not_called()
        inner.delete.assert_not_called()

    def test_checks_write_permission_on_macos_service_files_on_macos(self):
        inner = MagicMock()
        inner.ls.return_value = [_file(MARKER), _file('._keep.txt')]
        manager = MagicMock()
        client = StoragePathPermissionsFileSystemClient(inner, manager)

        with patch.object(fuseutils, 'IS_MAC', True):
            client.rmdir('/A')

        assert [call[0][0] for call in manager.check_path_access.call_args_list] == ['/A/' + MARKER, '/A/._keep.txt']
        inner.rmdir.assert_called_once_with('/A')

    def test_refuses_folder_without_write_permission(self):
        inner = MagicMock()
        inner.ls.return_value = [_file(MARKER)]
        manager = MagicMock()
        manager.check_path_access.side_effect = ForbiddenOperationException()
        client = StoragePathPermissionsFileSystemClient(inner, manager)

        with pytest.raises(ForbiddenOperationException):
            client.rmdir('/A')

        inner.rmdir.assert_not_called()
        inner.delete.assert_not_called()

    def test_skips_permission_check_for_folder_without_hidden_files(self):
        inner = MagicMock()
        inner.ls.return_value = []
        manager = MagicMock()
        client = StoragePathPermissionsFileSystemClient(inner, manager)

        client.rmdir('/A')

        manager.check_path_access.assert_not_called()
        inner.rmdir.assert_called_once_with('/A')

    @pytest.mark.parametrize('keys', [
        ['A/keep.txt'],
        ['A/' + MARKER, 'A/keep.txt'],
    ])
    def test_refuses_folder_that_looks_empty_to_user_without_permissions(self, keys):
        storage_client, s3 = _s3_client(keys)
        manager = MagicMock()
        manager.get_parent_permissions.return_value = (None, {})
        manager.get_children_with_depth_permissions.return_value = {}
        manager.check_path_access.side_effect = ForbiddenOperationException()
        client = StoragePathPermissionsFileSystemClient(storage_client, manager)

        assert client.ls('/A/') == []
        with pytest.raises(NotEmptyOperationException):
            client.rmdir('/A')

        assert s3.keys == set(keys)

    def test_refuses_folder_that_is_not_empty_and_deletes_nothing(self):
        keys = ['A/' + MARKER, 'A/keep.txt', 'A/sub/inner.txt']
        storage_client, s3 = _s3_client(keys)
        client = StoragePathPermissionsFileSystemClient(storage_client, MagicMock())

        with pytest.raises(NotEmptyOperationException):
            client.rmdir('/A')

        assert s3.deleted == []
        assert s3.keys == set(keys)


class FakeResponse(object):

    def __init__(self, status_code, content=b''):
        self.status_code = status_code
        self.content = content
        self.headers = {}


class FakeWebDavSession(object):
    """Answers PROPFIND with the given folder items and DELETE with 204."""

    def __init__(self, hrefs):
        self._hrefs = hrefs
        self.requests = []

    def request(self, method, url, allow_redirects=False, **kwargs):
        self.requests.append((method, url))
        if method == 'PROPFIND':
            return FakeResponse(207, self._multistatus())
        if method == 'DELETE':
            return FakeResponse(204)
        return FakeResponse(405)

    def _multistatus(self):
        responses = []
        for href in self._hrefs:
            resource_type = '<D:collection/>' if href.endswith('/') else ''
            responses.append('<D:response><D:href>%s</D:href><D:propstat><D:prop>'
                             '<D:resourcetype>%s</D:resourcetype>'
                             '</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>'
                             % (href, resource_type))
        return ('<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:">%s</D:multistatus>'
                % ''.join(responses)).encode('utf-8')


def _webdav_client(hrefs):
    client = WebDavClient(webdav_url='https://dav.example.com/webdav/', bearer='token')
    client.session = FakeWebDavSession(hrefs)
    return client


class TestWebDavRmdir(object):

    @pytest.mark.parametrize('hrefs', [
        ['/webdav/A/', '/webdav/A/keep.txt'],
        ['/webdav/A/', '/webdav/A/sub/'],
        ['/webdav/A/', '/webdav/A/' + MARKER, '/webdav/A/keep.txt'],
        ['/webdav/A/', '/webdav/A/._keep.txt'],
    ])
    def test_refuses_folder_that_is_not_empty_and_sends_no_delete(self, hrefs):
        client = _webdav_client(hrefs)

        with patch.object(fuseutils, 'IS_MAC', False):
            with pytest.raises(NotEmptyOperationException):
                client.rmdir('/A')

        assert [method for method, _ in client.session.requests] == ['PROPFIND']

    @pytest.mark.parametrize('hrefs', [
        ['/webdav/A/'],
        ['/webdav/A/', '/webdav/A/' + MARKER],
    ])
    def test_deletes_empty_folder(self, hrefs):
        client = _webdav_client(hrefs)

        client.rmdir('/A')

        assert client.session.requests == [('PROPFIND', 'https://dav.example.com:443/webdav/A/'),
                                           ('DELETE', 'https://dav.example.com:443/webdav/A')]

    def test_deletes_folder_with_macos_service_files_on_macos(self):
        client = _webdav_client(['/webdav/A/', '/webdav/A/._keep.txt', '/webdav/A/.localized'])

        with patch.object(fuseutils, 'IS_MAC', True):
            client.rmdir('/A')

        assert [method for method, _ in client.session.requests] == ['PROPFIND', 'DELETE']


class TestCachingRmdir(object):

    def test_keeps_parent_listing_and_logs_no_error_when_folder_is_not_empty(self):
        inner = MagicMock()
        inner.rmdir.side_effect = NotEmptyOperationException()
        cache = ListingCache(TTLCache(maxsize=10, ttl=60))
        cache.set('/', {'A': _file('A', is_dir=True)})
        cache.set('/A/', {'keep.txt': _file('keep.txt')})
        client = CachingListingFileSystemClient(inner, cache)

        with patch('pipefuse.cache.logging') as cache_logging:
            with pytest.raises(NotEmptyOperationException):
                client.rmdir('/A')

        assert list(cache.get('/').keys()) == ['A']
        assert cache.get('/A/') is None
        cache_logging.exception.assert_not_called()
        cache_logging.error.assert_not_called()
        cache_logging.warning.assert_not_called()

    def test_removes_folder_from_parent_listing(self):
        inner = MagicMock()
        cache = ListingCache(TTLCache(maxsize=10, ttl=60))
        cache.set('/', {'A': _file('A', is_dir=True), 'B': _file('B', is_dir=True)})
        client = CachingListingFileSystemClient(inner, cache)

        client.rmdir('/A')

        inner.rmdir.assert_called_once_with('/A')
        assert list(cache.get('/').keys()) == ['B']


class TestFileSystemRmdir(object):

    def test_maps_not_empty_folder_to_enotempty(self):
        class NotEmptyFS(object):
            def rmdir(self, path):
                raise NotEmptyOperationException()

        with pytest.raises(FuseOSError) as error:
            ResilientFS(NotEmptyFS()).rmdir('/A')

        assert error.value.errno == errno.ENOTEMPTY

    def test_refuses_folder_that_is_not_empty_with_enotempty(self):
        keys = ['A/keep.txt', 'A/sub/inner.txt']
        client, s3 = _s3_client(keys)
        fs = ResilientFS(PipeFS(client=client, lock=NoLock()))

        with pytest.raises(FuseOSError) as error:
            fs.rmdir('/A')

        assert error.value.errno == errno.ENOTEMPTY
        assert s3.keys == set(keys)

    def test_removes_folder_that_looks_empty_on_macos(self):
        client, s3 = _s3_client(['A/' + MARKER, 'A/._keep.txt'])
        pipe_fs = PipeFS(client=client, lock=NoLock())
        pipe_fs._is_mac = True
        fs = ResilientFS(pipe_fs)

        with patch.object(fuseutils, 'IS_MAC', True):
            assert list(fs.readdir('/A', None)) == ['.', '..']
            fs.rmdir('/A')

        assert s3.keys == set()

    def test_lists_macos_service_files_but_not_folder_marker_on_other_platforms(self):
        client, _ = _s3_client(['A/' + MARKER, 'A/._keep.txt'])
        pipe_fs = PipeFS(client=client, lock=NoLock())
        pipe_fs._is_mac = False

        assert list(ResilientFS(pipe_fs).readdir('/A', None)) == ['.', '..', '._keep.txt']
