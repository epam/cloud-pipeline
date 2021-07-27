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

import deleteAnnotation from './utilities/delete-annotation';
import getAnnotation from './utilities/get-annotation';
import updateAnnotation from './utilities/update-annotation';

function annotation (item, resolve) {
  const {
    storage,
    path,
    annotationId,
    method,
    data
  } = item;
  if (/^put$/i.test(method)) {
    updateAnnotation(
      storage,
      path,
      annotationId,
      data
    )
      .then(resolve)
      .catch(() => resolve());
  } else if (/^delete$/i.test(method)) {
    deleteAnnotation(
      storage,
      path,
      annotationId
    )
      .then(resolve)
      .catch(() => resolve());
  } else {
    getAnnotation(
      storage,
      path,
      annotationId
    )
      .then(resolve)
      .catch(() => resolve());
  }
}

annotation.test = function (url, method, data) {
  const exec = /^annotation\/([\d]+)\/(.*?)\/([\d]+)$/i.exec(url);
  if (exec && exec.length > 3 && /^(get|put|delete)$/i.test(method)) {
    return {
      storage: +exec[1],
      path: exec[2],
      annotationId: exec[3],
      method: method || 'GET',
      data
    };
  }
  return undefined;
};

export default annotation;
