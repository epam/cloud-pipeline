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

import DataStoragePage from '../../../../../../models/dataStorage/DataStoragePage';

export default function getStorageContents (storage, path) {
  function getPage (marker) {
    return new Promise((resolve) => {
      const request = new DataStoragePage(
        storage,
        path,
        false,
        false,
        2,
        marker
      );
      request
        .fetchPage(marker)
        .then(() => {
          if (request.loaded) {
            const {
              results = [],
              nextPageMarker
            } = request.value;
            if (nextPageMarker) {
              return Promise.all([
                Promise.resolve(results),
                getPage(nextPageMarker)
              ]);
            }
            return Promise.resolve([results]);
          } else {
            throw new Error(request.error);
          }
        })
        .then(pages => {
          resolve((pages || []).reduce((result, page) => ([...result, ...page]), []));
        })
        .catch(() => resolve([]));
    });
  }
  return getPage();
}
