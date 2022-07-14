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

import DataStorageItemDelete from '../../../../../../models/dataStorage/DataStorageItemDelete';

export default function deleteAnnotation (storage, path, annotationId) {
  return new Promise((resolve) => {
    const url = path
      ? `${path}/annotations/${annotationId}.json`.replace(/\/\//g, '/')
      : `annotations/${annotationId}.json`;
    const request = new DataStorageItemDelete(storage);
    try {
      request
        .send([{
          path: url,
          type: 'File'
        }])
        .then(() => {
          if (request.loaded) {
            resolve();
          } else {
            throw new Error(request.error);
          }
        })
        .catch((e) => {
          console.warn(`Error deleting annotation: ${e.message}`);
          resolve();
        });
    } catch (e) {
      console.warn(`Error deleting annotation: ${e.message}`);
      resolve();
    }
  });
}
