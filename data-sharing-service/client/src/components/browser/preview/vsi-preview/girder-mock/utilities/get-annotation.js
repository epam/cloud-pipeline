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

import getStorageItemContent from './get-storage-item-content';

export default function getAnnotation (storage, path, id) {
  const url = path
    ? `${path}/annotations/${id}.json`.replace(/\/\//g, '/')
    : `annotations/${id}.json`;
  return new Promise((resolve) => {
    getStorageItemContent(storage, url)
      .then(content => {
        try {
          const annotation = JSON.parse(content);
          resolve({
            _id: `${storage}/${path || ''}/${id}`,
            creatorId: +id,
            annotation
          });
        } catch (e) {
          throw new Error(`Error parsing annotation: ${e.message}`);
        }
      })
      .catch(() => resolve());
  });
}
