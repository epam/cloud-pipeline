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

import {createObjectStorageWrapper} from '../../../../../utils/object-storage';
import storages from '../../../../../models/dataStorage/DataStorageAvailable';
import GenerateDownloadUrl from '../../../../../models/dataStorage/GenerateDownloadUrl';
import DataStorageTags from '../../../../../models/dataStorage/tags/DataStorageTags';

const expirationTimeoutMS = 1000 * 60; // 1 minute

/**
 * @param output
 * @returns {Promise<AnalysisOutputResult>}
 */
export async function getOutputFileAccessInfo (output) {
  const {
    file: path
  } = output;
  const objectStorage = await createObjectStorageWrapper(
    storages,
    path,
    {write: false, read: true},
    {generateCredentials: false}
  );
  if (objectStorage) {
    let url;
    let handle;
    const setExpirationTimeout = () => {
      clearTimeout(handle);
      handle = setTimeout(() => {
        url = undefined;
      }, expirationTimeoutMS);
    };
    const fetchUrl = async () => {
      if (url) {
        return url;
      }
      url = objectStorage.generateFileUrl(objectStorage.getRelativePath(path));
      setExpirationTimeout();
      return url;
    };
    return {
      ...output,
      fetchUrl
    };
  }
  return undefined;
}

/**
 * @param {{url: string?, storageId: string?, path: string?, checkExists: boolean?}} options
 * @returns {Promise<string>}
 */
export async function generateResourceUrl (options = {}) {
  const {
    url,
    storageId,
    path,
    checkExists = false
  } = options;
  if (typeof url === 'string' && /^https?:\/\//i.test(url)) {
    return url;
  }
  if (typeof url === 'string') {
    const accessInfo = await getOutputFileAccessInfo({file: url});
    if (accessInfo) {
      return accessInfo.fetchUrl();
    }
    return url;
  }
  if (storageId && path) {
    if (checkExists) {
      const tags = new DataStorageTags(storageId, path);
      await tags.fetch();
      if (tags.error) {
        return undefined;
      }
    }
    const request = new GenerateDownloadUrl(storageId, path);
    await request.fetch();
    if (request.error) {
      throw new Error(request.error);
    }
    return request.value.url;
  }
  return undefined;
}
