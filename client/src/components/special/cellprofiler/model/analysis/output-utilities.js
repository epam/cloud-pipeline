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
      if (objectStorage.pathMask) {
        const e = (new RegExp(`^${objectStorage.pathMask}/(.+)$`, 'i')).exec(path);
        if (e && e.length) {
          url = objectStorage.generateFileUrl(e[1]);
          setExpirationTimeout();
          return url;
        }
      }
      url = objectStorage.generateFileUrl(path);
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
