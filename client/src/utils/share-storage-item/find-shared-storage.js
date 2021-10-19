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

import FolderLoad from '../../models/folders/FolderLoad';
import getStoragePath from './get-storage-path';

function wrapRequest (request) {
  return new Promise((resolve, reject) => {
    request.fetch()
      .then(() => resolve(request))
      .catch(reject);
  });
}

function removeTrailingSlash (path) {
  if (path && path.endsWith('/')) {
    return path.slice(0, -1);
  }
  return path;
}

export default function findSharedStorage (preferences, sharedStorage, sharedFolder) {
  if (!preferences) {
    return Promise.reject(
      new Error('Shared storages system directory not specified (no preferences)')
    );
  }
  if (!sharedStorage) {
    return Promise.reject(new Error('Storage not specified'));
  }
  return new Promise((resolve, reject) => {
    const path = removeTrailingSlash(getStoragePath(sharedStorage, sharedFolder));
    preferences
      .fetchIfNeededOrWait()
      .then(() => {
        if (preferences.sharedStoragesSystemDirectory) {
          const request = new FolderLoad(preferences.sharedStoragesSystemDirectory);
          return wrapRequest(request);
        } else {
          throw new Error('Shared storages system directory not specified');
        }
      })
      .then((request) => {
        if (request.error) {
          throw new Error(request.error);
        } else if (request.loaded) {
          const {
            storages = []
          } = request.value;
          const sharedStorage = storages.find(s => removeTrailingSlash(s.path) === path);
          if (sharedStorage) {
            return Promise.resolve(sharedStorage.id);
          }
          return Promise.resolve(undefined);
        }
      })
      .then(resolve)
      .catch(reject);
  });
}
