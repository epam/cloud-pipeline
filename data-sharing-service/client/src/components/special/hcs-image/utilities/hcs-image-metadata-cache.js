/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import auditStorageAccessManager from '../../../../utils/audit-storage-access';
import {fetchSourceInfo} from '../hcs-image-viewer';

class HCSImageMetadataCache {
  /**
   * @param {ObjectStorage} objectStorage
   */
  constructor (objectStorage) {
    /**
     * @type {Map<string, Promise>}
     */
    this.cache = new Map();
    this.objectStorage = objectStorage;
  }

  destroy = () => {
    this.cache.clear();
    this.objectStorage = undefined;
  };

  clear = () => {
    this.cache.clear();
  }

  /**
   * @param {HCSImageWell} well
   */
  getMetadata = (well) => {
    const {
      omeTiffFileName,
      offsetsJsonFileName
    } = well;
    const key = `${omeTiffFileName}|${offsetsJsonFileName}`.toUpperCase();
    if (!this.cache.has(key)) {
      this.cache.set(key, new Promise(async (resolve, reject) => {
        try {
          const url = await this.objectStorage.generateFileUrl(omeTiffFileName);
          const offsets = await this.objectStorage.generateFileUrl(offsetsJsonFileName);
          auditStorageAccessManager.reportReadAccess({
            storageId: this.objectStorage ? this.objectStorage.id : undefined,
            path: omeTiffFileName,
            reportStorageType: 'S3'
          }, {
            storageId: this.objectStorage ? this.objectStorage.id : undefined,
            path: offsetsJsonFileName,
            reportStorageType: 'S3'
          });
          const info = await fetchSourceInfo({url, offsetsUrl: offsets});
          resolve(info.map((item) => item.metadata));
        } catch (error) {
          reject(error);
        }
      }));
    }
    return this.cache.get(key);
  };
}

export default HCSImageMetadataCache;
