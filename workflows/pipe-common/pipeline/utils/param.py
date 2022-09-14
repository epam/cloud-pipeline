# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import os
from abc import ABCMeta, abstractmethod

from pipeline.utils.lock import synchronized, FileSystemLock


class ParamsManager:
    __metaclass__ = ABCMeta

    @abstractmethod
    def get(self, key):
        pass

    @abstractmethod
    def put(self, key, value):
        pass


class FileSystemParamsManager(ParamsManager):

    def __init__(self, path, logger):
        self._path = path
        self._logger = logger

    def get(self, key):
        params = self._read()
        for param in params:
            if param.get('name') == key:
                return param.get('resolvedValue')

    def _read(self):
        if os.path.exists(self._path):
            with open(self._path) as f:
                return json.load(f)
        self._logger.debug('Dynamics parameters path was not found {}'.format(self._path))
        return []

    def put(self, key, value):
        old_params = self._read()
        new_params = []
        for param in old_params:
            if param.get('name') != key:
                new_params.append(param)
        new_params.append({
            'name': key,
            'value': value,
            'type': 'string',
            'resolvedValue': value
        })
        self._write(new_params)

    def _write(self, params):
        with open(self._path, 'w') as f:
            f.write(json.dumps(params, indent=4))


class LockingParamsManager(ParamsManager):

    def __init__(self, lock, inner):
        self._lock = lock
        self._inner = inner

    @synchronized
    def get(self, key):
        return self._inner.get(key)

    @synchronized
    def put(self, key, value):
        self._inner.put(key, value)


class EnvironmentParamsManager(ParamsManager):

    def __init__(self, inner):
        self._inner = inner

    def get(self, key):
        return self._inner.get(key) or os.getenv(key)

    def put(self, key, value):
        self._inner.put(key, value)


class DefaultParamsManager(ParamsManager):

    def __init__(self, path, logger):
        inner = FileSystemParamsManager(path=path, logger=logger)
        inner = LockingParamsManager(lock=FileSystemLock(lock_path=path + '.lock'), inner=inner)
        inner = EnvironmentParamsManager(inner=inner)
        self._inner = inner

    def get(self, key):
        return self._inner.get(key)

    def put(self, key, value):
        self._inner.put(key, value)
