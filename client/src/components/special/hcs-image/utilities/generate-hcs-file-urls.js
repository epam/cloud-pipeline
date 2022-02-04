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

import GenerateDownloadUrl from '../../../../models/dataStorage/GenerateDownloadUrl';

export default function generateHCSFileURLs (options = {}) {
  const {
    storageId,
    path,
    s3Storage
  } = options;
  if (!storageId || !path) {
    return Promise.reject(new Error('`storageId` and `path` must be specified for HCS image'));
  }
  return new Promise(async (resolve, reject) => {
    try {
      if (s3Storage) {
        await s3Storage.updateCredentials();
        const url = await s3Storage.getSignedUrl(path);
        resolve(url);
      } else {
        const request = new GenerateDownloadUrl(storageId, path);
        await request.fetch();
        if (!request.loaded || request.error || !request.value) {
          // eslint-disable-next-line
          throw new Error(request.error || `Error generating URL for "${path}" (storage #${storageId})`)
        }
        resolve(request.value.url);
      }
    } catch (e) {
      reject(e);
    }
  });
}
