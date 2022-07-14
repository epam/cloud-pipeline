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

import DataStorageItemUpdateContent
  from '../../../../../../models/dataStorage/DataStorageItemUpdateContent';

export default function updateAnnotation (storage, path, annotationId, annotation) {
  return new Promise((resolve) => {
    const url = path
      ? `${path}/annotations/${annotationId}.json`.replace(/\/\//g, '/')
      : `annotations/${annotationId}.json`;
    const request = new DataStorageItemUpdateContent(storage, url);
    try {
      request
        .send(annotation)
        .then(() => {
          if (request.loaded) {
            resolve({
              _id: `${storage}/${path || ''}/${annotationId}`,
              creatorId: +annotationId,
              annotation: JSON.parse(annotation)
            });
          } else {
            throw new Error(request.error);
          }
        })
        .catch((e) => {
          console.warn(`Error updating annotation: ${e.message}`);
          resolve();
        });
    } catch (e) {
      console.warn(`Error updating annotation: ${e.message}`);
      resolve();
    }
  });
}
