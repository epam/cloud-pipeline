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
import auditStorageAccessManager from '../../../../../utils/audit-storage-access';

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
    const reportObjectAccess = () => auditStorageAccessManager.reportReadAccess({
      storageId: objectStorage.id,
      reportStorageType: 'S3',
      path: objectStorage.getRelativePath(path)
    });
    const fetchUrl = async () => {
      if (url) {
        return url;
      }
      url = objectStorage.generateFileUrl(objectStorage.getRelativePath(path));
      setExpirationTimeout();
      return url;
    };
    const fetchUrlAndReportAccess = async () => {
      const url = await fetchUrl();
      reportObjectAccess();
      return url;
    };
    return {
      ...output,
      storageId: objectStorage.id,
      storagePath: objectStorage.getRelativePath(path),
      fetchUrl,
      fetchUrlAndReportAccess,
      reportObjectAccess
    };
  }
  return undefined;
}

/**
 * @param {{url: string?, storageId: string?, path: string?, checkExists: boolean?}} options
 * @returns {Promise<{url: string, callback: function: void}>}
 */
export async function generateResourceUrlWithAccessCallback (options = {}) {
  const {
    url,
    storageId,
    path,
    checkExists = false
  } = options;
  if (typeof url === 'string' && /^https?:\/\//i.test(url)) {
    return {url, callback: () => {}};
  }
  if (typeof url === 'string') {
    const accessInfo = await getOutputFileAccessInfo({file: url});
    if (accessInfo) {
      const generatedUrl = await accessInfo.fetchUrl();
      return {
        url: generatedUrl,
        callback: () => accessInfo.reportObjectAccess()
      };
    }
    return {url, callback: () => {}};
  }
  if (storageId && path) {
    if (checkExists) {
      const tags = new DataStorageTags(storageId, path);
      await tags.fetch();
      if (tags.error) {
        return {url: undefined, callback: () => {}};
      }
    }
    const request = new GenerateDownloadUrl(storageId, path);
    await request.fetch();
    if (request.error) {
      throw new Error(request.error);
    }
    return {
      url: request.value.url,
      callback: () => auditStorageAccessManager.reportReadAccess({
        storageId,
        path,
        reportStorageType: 'S3'
      })
    };
  }
  return {
    url: undefined,
    callback: () => {}
  };
}

/**
 * @param {{url: string?, storageId: string?, path: string?, checkExists: boolean?}} options
 * @returns {Promise<string>}
 */
export async function generateResourceUrl (options = {}) {
  const {url} = await generateResourceUrlWithAccessCallback(options);
  return url;
}
