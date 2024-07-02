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

import CreateDataStorage from '../../models/dataStorage/DataStorageSave';
import getStoragePath from './get-storage-path';
import generateSharingList from './extend-sharing-list';

const ServiceTypes = {
  objectStorage: 'OBJECT_STORAGE',
  fileShare: 'FILE_SHARE'
};

function wrapRequest (request, payload) {
  return new Promise((resolve, reject) => {
    request.send(payload)
      .then(() => resolve(request))
      .catch(reject);
  });
}

export default function createSharedStorage (
  preferences,
  sharedStorage,
  sharedFolder,
  sharedItems = []
) {
  if (!preferences) {
    return Promise.reject(
      new Error('Shared storages system directory not specified (no preferences)')
    );
  }
  if (!sharedStorage) {
    return Promise.reject(new Error('Storage not specified'));
  }
  return new Promise((resolve, reject) => {
    const path = getStoragePath(sharedStorage, sharedFolder);
    preferences
      .fetchIfNeededOrWait()
      .then(() => {
        if (preferences.sharedStoragesSystemDirectory) {
          const request = new CreateDataStorage(false, true);
          const serviceType = /^nfs$/i.test(sharedStorage.type)
            ? ServiceTypes.fileShare
            : ServiceTypes.objectStorage;
          const payload = {
            sourceStorageId: sharedStorage.id,
            linkingMasks: generateSharingList(
              sharedItems,
              sharedStorage ? sharedStorage.delimiter : undefined
            ),
            parentFolderId: preferences.sharedStoragesSystemDirectory,
            path,
            shared: true,
            serviceType,
            regionId: serviceType === ServiceTypes.objectStorage && sharedStorage.regionId
              ? sharedStorage.regionId
              : undefined,
            storagePolicy: serviceType === ServiceTypes.objectStorage
              ? {versioningEnabled: false}
              : undefined
          };
          return wrapRequest(request, payload);
        } else {
          throw new Error('Shared storages system directory not specified');
        }
      })
      .then((request) => {
        if (request.loaded) {
          const {id} = request.value;
          return Promise.resolve(id);
        } else {
          console.warn(request.error);
          throw new Error(request.error || 'error creating storage');
        }
      })
      .then(resolve)
      .catch(reject);
  });
}
