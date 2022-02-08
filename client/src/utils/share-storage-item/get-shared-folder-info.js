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
import {getSharedStoragePermissions} from './shared-storage-permissions';
import getSharedLink from './get-shared-link';
import roleModel from '../roleModel';

/**
 * @typedef {Object} SharedStorageInfo
 * @property {string|undefined} url
 * @property {boolean} permissionsModificationAllowed
 * @property {SharedStoragePermissions} permissions
 */

/**
 * Gets shared storage folder info.
 * @param {PreferencesLoad} preferences
 * @param {Object} sharedStorage
 * @param {string} sharedFolder
 * @returns {Promise<undefined|SharedStorageInfo>}
 */
export default function getSharedFolderInfo (
  preferences,
  sharedStorage,
  sharedFolder
) {
  return new Promise((resolve, reject) => {
    let sharedStorageId;
    let sharedStoragePermissions;
    let mask = 0;
    findSharedStorage(preferences, sharedStorage, sharedFolder)
      .then(storage => {
        const {id: storageId, mask: storageMask = 0} = storage || {};
        mask = storageMask;
        if (storageId) {
          sharedStorageId = storageId;
          return getSharedStoragePermissions(storageId);
        }
        return Promise.resolve();
      })
      .then(permissions => {
        sharedStoragePermissions = permissions;
        return getSharedLink(sharedStorageId);
      })
      .then(url => sharedStorageId
        ? resolve({
          url,
          permissions: sharedStoragePermissions,
          permissionsModificationAllowed: roleModel.isOwner({mask})
        })
        : resolve(undefined)
      )
      .catch(reject);
  });
}
