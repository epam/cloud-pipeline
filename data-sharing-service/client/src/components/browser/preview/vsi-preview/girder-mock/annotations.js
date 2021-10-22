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

import getStorageContents from './utilities/get-storage-folder-contents';
import getAnnotation from './utilities/get-annotation';
import updateAnnotation from './utilities/update-annotation';
import whoAmI from '../../../../../models/user/WhoAmI';

function annotations (item, resolve) {
  const {
    storage,
    path,
    method,
    data
  } = item;
  if (/^post$/i.test(method) && data) {
    whoAmI
      .fetchIfNeededOrWait()
      .then(() => {
        if (whoAmI.loaded) {
          const {id} = whoAmI.value || {};
          return updateAnnotation(storage, path, id, data);
        } else {
          throw new Error(whoAmI.error);
        }
      })
      .catch(() => Promise.resolve({}))
      .then(resolve);
  } else {
    getStorageContents(
      storage,
      path
        ? `${path}/annotations`.replace(/\/\//g, '/')
        : 'annotations'
    )
      .then((contents) => {
        const annotationFiles = (contents || [])
          .filter(item => /^file$/i.test(item.type) && /^[\d]+\.json$/i.test(item.name))
          .map(item => {
            const exec = /^([\d]+)\.json$/i.exec(item.name);
            if (exec && exec.length > 1) {
              return {
                _id: +exec[1],
                ...item
              };
            }
            return undefined;
          })
          .filter(Boolean);
        Promise.all(
          annotationFiles.map(file => getAnnotation(storage, path, file._id))
        )
          .then(results => {
            resolve(results);
          })
          .catch(() => resolve([]));
      });
  }
}

annotations.test = function (url, method, data) {
  const exec = /^annotation\?itemId=([\d]+)\/(.*?)(&|$)/i.exec(url);
  if (exec && exec.length > 2 && /^(get|post)$/i.test(method)) {
    return {
      storage: +exec[1],
      path: exec[2],
      method,
      data
    };
  }
  return undefined;
};

export default annotations;
