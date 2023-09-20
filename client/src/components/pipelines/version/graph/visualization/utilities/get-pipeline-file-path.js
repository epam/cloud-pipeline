/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

export default function getPipelineFilePath (filePath, currentPath = '') {
  let codePath = currentPath || '';
  if (codePath.startsWith('/')) {
    codePath = codePath.slice(1);
  }
  if (codePath.endsWith('/')) {
    codePath = codePath.slice(0, -1);
  }
  let path = filePath || '';
  if (path.startsWith('/')) {
    path = path.slice(1);
  }
  let relativePath = path;
  if (!/^(\.\/|\.\.\/)/.test(relativePath)) {
    relativePath = './'.concat(relativePath);
  }
  const root = codePath.length ? `http://root/${codePath}/` : 'http://root/';
  try {
    const url = new URL(relativePath, root);
    let result = url.pathname;
    if (result.startsWith('/')) {
      result = result.slice(1);
    }
    return result;
  } catch (_) {
    return `${codePath}/${path}`;
  }
}
