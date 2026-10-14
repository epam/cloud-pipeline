# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
Picking the right source for a storage.

The one place in the service that knows which storage types can be archived. Everything else works
against `FileSource`.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from .base import FileSource, SourceFile
from .nfs import NfsSource
from .s3 import S3Source
from ..aws import build_s3_client
from ..config import ServiceConfig
from ..errors import UnsupportedSource
from ..log import get_logger
from ..model import Storage, StorageType

if TYPE_CHECKING:
    # Imports inside `if TYPE_CHECKING` run only for a type checker, never at runtime. The block is how
    # Python breaks an import cycle while keeping the annotation: a type checker reads it, the
    # interpreter skips it, and the annotations below stay strings because of `from __future__ import
    # annotations` at the top. There is no cycle to break here today - `api.py` imports nothing from
    # this package - and the import is in the block anyway, because a factory importing the client it
    # only names is exactly how one gets created later.
    from ..api import CloudPipelineApi

LOG = get_logger(__name__)

# `__all__` lists what `from sas.sources import *` would bring in. Nobody should write that import, but
# the list doubles as a statement of what this package means to export - and the re-exports above are
# otherwise flagged as unused by linters.
__all__ = ['FileSource', 'SourceFile', 'open_source']


def open_source(api: CloudPipelineApi, storage: Storage, config: ServiceConfig) -> FileSource:
    """
    Opens a reader for the storage a task points at.

    `match` (Python 3.10 and later) is structural pattern matching. On a plain value it reads like a
    switch, `case _` is the default, and - unlike a switch - there is no fall-through to forget.

    The caller is expected to use the result in a `with` block, so that whatever the source holds is
    released on the way out:

        with open_source(api, storage, config) as source:
            ...
    """
    match storage.type:
        case StorageType.S3:
            LOG.debug('Storage %s is S3 bucket %s', storage.name, storage.path)
            return S3Source(storage, build_s3_client(api, storage))
        case StorageType.NFS:
            if not storage.file_share_mount_id:
                raise UnsupportedSource(f'NFS storage {storage.name} has no file share mount, so '
                                        f'there is no share to mount')
            share = api.load_file_share(storage.file_share_mount_id)
            LOG.debug('Storage %s is on %s share %s', storage.name, share.mount_type,
                      share.mount_root)
            return NfsSource(storage, share, config.nfs_mount_root,
                             config.nfs_mount_timeout_seconds)
        case _:
            # Azure and GCP storages reach this. Supporting them means a module next to `s3.py` using
            # that cloud's own SDK; until then the task fails with a message naming the type, which is
            # the one thing the user needs to be told.
            raise UnsupportedSource(f'Storage {storage.name} is of type {storage.type}, which this '
                                    f'service cannot read')
