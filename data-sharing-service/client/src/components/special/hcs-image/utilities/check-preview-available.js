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

import Viewer from '../hcs-image-viewer';
import HCSInfo from './hcs-image-info';

/**
 * @typedef {Object} CheckHCSOptions
 * @property {string|number} [storageId]
 * @property {string|number} [path]
 */

/**
 * Check if HcsImageViewer control is available and passed file is a *.hcs file
 * @param {CheckHCSOptions} options
 * @return {boolean}
 */
function fastCheckPreviewAvailable (options = {}) {
  const {
    storageId,
    path
  } = options;
  return !!Viewer && storageId && path && /\.hcs$/i.test(path);
}

/**
 * Check if HCS preview available for the file
 * @param {Object} options
 * @param {string|number} [options.storageId]
 * @param {string|number} [options.path]
 * @return {Promise<boolean>}
 */
function checkPreviewAvailable (options = {}) {
  if (!fastCheckPreviewAvailable(options)) {
    return Promise.resolve(false);
  }
  const {
    storageId,
    path
  } = options;
  return new Promise((resolve) => {
    HCSInfo
      .fetch({storageId, path})
      .then(info => {
        if (info && info.sequences && info.sequences.length) {
          return Promise.resolve();
        }
        return Promise.reject(new Error());
      })
      .then(() => resolve(true))
      .catch(() => resolve(false));
  });
}

export {checkPreviewAvailable, fastCheckPreviewAvailable};
export default checkPreviewAvailable;
