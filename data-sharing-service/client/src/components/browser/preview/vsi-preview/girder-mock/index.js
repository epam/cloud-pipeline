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

import userMe from './user-me';
import itemInfo from './item-info';
import folderInfo from './folder-info';
import folderItems from './folder-items';
import annotation from './annotation';
import annotations from './annotations';

const handlers = [
  userMe,
  itemInfo,
  folderInfo,
  folderItems,
  annotation,
  annotations
];

window.girder = {
  rest: {
    restRequest (opts) {
      const {url, method, data} = opts;
      return {
        done (cb1) {
          const cb = (...opts) => {
            cb1(...opts);
          };
          for (let h = 0; h < handlers.length; h++) {
            const handler = handlers[h];
            const test = handler.test(url, method, data);
            if (test) {
              return handler(test, cb);
            }
          }
          console.warn('unhandled request!', opts);
        }
      };
    }
  }
};
