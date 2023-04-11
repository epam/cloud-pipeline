#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

from pipefuse.fsclient import FileSystemClientDecorator
from pipefuse.storage import StorageLowLevelFileSystemClient
from src.common.audit import DataAccessEvent, DataAccessType


class AuditFileSystemClient(FileSystemClientDecorator, StorageLowLevelFileSystemClient):

    def __init__(self, inner, container):
        super(AuditFileSystemClient, self).__init__(inner)
        self._inner = inner
        self._container = container
        self._fhs = set()

    def upload(self, buf, path):
        self._container.put(DataAccessEvent(path, DataAccessType.WRITE))
        self._inner.upload(buf, path)

    def delete(self, path):
        self._container.put(DataAccessEvent(path, DataAccessType.DELETE))
        self._inner.delete(path)

    def mv(self, old_path, path):
        self._container.put_all([DataAccessEvent(old_path, DataAccessType.READ),
                                 DataAccessEvent(old_path, DataAccessType.DELETE),
                                 DataAccessEvent(path, DataAccessType.WRITE)])
        self._inner.mv(old_path, path)

    def download_range(self, fh, buf, path, offset=0, length=0):
        if fh not in self._fhs:
            self._fhs.add(fh)
            self._container.put(DataAccessEvent(path, DataAccessType.READ))
        self._inner.download_range(fh, buf, path, offset, length)

    def flush(self, fh, path):
        try:
            self._fhs.remove(fh)
        except KeyError:
            pass
        self._inner.flush(fh, path)

    def new_mpu(self, path, file_size, download, mv):
        self._container.put(DataAccessEvent(path, DataAccessType.WRITE))
        return self._inner.new_mpu(path, file_size, download, mv)
