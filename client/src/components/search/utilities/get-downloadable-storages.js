/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import MetadataSearchEntry from '../../../models/metadata/MetadataSearchEntry';
import {
  checkStorageDownloadEnabledAttributeValue
} from '../../../models/dataStorage/data-storage-listing';

export default async function getNotDownloadableStorages (
  authenticatedUserInfo,
  preferences
) {
  if (!preferences || !authenticatedUserInfo) {
    return [];
  }
  await authenticatedUserInfo.fetchIfNeededOrWait();
  if (
    authenticatedUserInfo.loaded &&
    authenticatedUserInfo.value &&
    authenticatedUserInfo.value.admin
  ) {
    return [];
  }
  const userGroups = new Set([
    ...(authenticatedUserInfo.value.groups || []).map((group) => group.toLowerCase()),
    ...(authenticatedUserInfo.value.roles || []).map((role) => role.name.toLowerCase())
  ]);
  await preferences.fetchIfNeededOrWait();
  try {
    const attribute = preferences.storageDownloadAttribute;
    if (attribute) {
      const request = new MetadataSearchEntry('DATA_STORAGE', attribute);
      await request.fetch();
      if (request.loaded) {
        return (request.value || [])
          .filter((metadata) => {
            return metadata &&
              metadata.data &&
              metadata.entity &&
              metadata.data[attribute] &&
              metadata.data[attribute].value &&
              !checkStorageDownloadEnabledAttributeValue(
                metadata.data[attribute].value,
                userGroups
              );
          })
          .map((metadata) => Number(metadata.entity.entityId));
      } else {
        return [];
      }
    }
  } catch (error) {
    console.warn(error.message);
  }
  return [];
}
