/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import findSharedStorage from './find-shared-storage';
import createSharedStorage from './create-shared-storage';
import {grantSharedStoragePermissions} from './shared-storage-permissions';
import getSharedLink from './get-shared-link';
import getSharedFolderInfo from './get-shared-folder-info';

export {getSharedFolderInfo as getSharedStorageItemInfo};

/**
 * @typedef {Object} PermissionsOptions
 * @param {number} [mask=1]
 * @param {SharedStoragePermission[]} [permissions]
 * @param {boolean} [replace=true] - if we should replace current shared storage
 * @param {boolean} [createNewStorage=false] - if we should not check storage existence
 */

/**
 * Shares storage folder.
 * Creates (if not exists) a new storage with a path of `sharedStorage.path / sharedFolder`,
 * sets permissions (removes current permissions if `permissionsOptions.replace` = true)
 * and returns Promise with shared storage url.
 * @param {PreferencesLoad} preferences
 * @param {Object} sharedStorage
 * @param {string} sharedFolder
 * @param {string[]} sharedItems - files ("file-name.ext") and folders ("folder-name/**") to share.
 * @param {PermissionsOptions} [permissionsOptions]
 * permissions with a new one
 * @returns {Promise<string>}
 */
export function shareStorageItem (
  preferences,
  sharedStorage,
  sharedFolder,
  sharedItems = [],
  permissionsOptions = {}
) {
  const {
    mask = 1,
    permissions = [],
    replace = true,
    createNewStorage = false
  } = permissionsOptions;
  return new Promise((resolve, reject) => {
    findSharedStorage(preferences, sharedStorage, sharedFolder, createNewStorage)
      .then(storage => {
        const {id: storageId} = storage || {};
        if (storageId) {
          return Promise.resolve(storageId);
        }
        return createSharedStorage(preferences, sharedStorage, sharedFolder, sharedItems);
      })
      .then(storageId => {
        if (storageId) {
          return grantSharedStoragePermissions(storageId, mask, permissions, replace);
        } else {
          throw new Error('Storage was not created');
        }
      })
      .then(getSharedLink)
      .then(resolve)
      .catch(reject);
  });
}
