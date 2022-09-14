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

MOUNT_LIMITS_NONE = 'none'
MOUNT_LIMITS_USER_DEFAULT = 'user_default'


class StorageMountsSerializer:

    def __init__(self, api, logger):
        self._api = api
        self._logger = logger

    def serialize(self, storage_ids):
        """
        :param storage_ids: list of storage ids
        :return: CP_CAP_LIMIT_MOUNTS run parameter value
        """
        return ','.join(self._predetermined(map(str, storage_ids)))

    def deserialize(self, storages_str):
        """
        :param storages_str: CP_CAP_LIMIT_MOUNTS run parameter value
        :return: list of unique and sorted storage ids. Example:
            None,'None','' -> []
            'user_default' -> [1]
            '3,3,3,2,2,1' -> [1, 2, 3]
        """
        if not storages_str:
            return []
        storage_ids = []
        for storage_str in storages_str.split(','):
            storage_id = self._get_storage_id(storage_str)
            if storage_id:
                storage_ids.append(storage_id)
        return self._predetermined(storage_ids)

    def _get_storage_id(self, storage_str):
        try:
            storage_str = (storage_str or '').lower().strip()
            if not storage_str:
                return None
            elif storage_str == MOUNT_LIMITS_USER_DEFAULT:
                user_info = self._api.load_current_user()
                user_storage_id = user_info.get('defaultStorageId')
                if not user_storage_id:
                    return None
                self._logger.info('User default storage is resolved as {}'.format(user_storage_id))
                return self._get_int(storage_str)
            elif storage_str == MOUNT_LIMITS_NONE:
                self._logger.info('{} placeholder found while parsing storage id, skipping it'
                                  .format(MOUNT_LIMITS_NONE))
                return None
            else:
                return self._get_int(storage_str)
        except Exception:
            self._logger.warning('Unable to parse {} placeholder to a storage ID.'.format(storage_str), trace=True)
            return None

    def _get_int(self, storage_id_str):
        if storage_id_str.isdigit():
            return int(storage_id_str)
        self._logger.warning('Unable to use storage with id {} because its id is not a number.'.format(storage_id_str))
        return None

    def _predetermined(self, storage_ids):
        return sorted(list(set(storage_ids)))
