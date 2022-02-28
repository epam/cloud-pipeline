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

import {generateRule} from './file-sharing-extensions';
import rules from './rules';

const extendFNs = rules.map(generateRule);

function generateExtendedListForItem (item, delimiter) {
  return [item].concat(
    extendFNs
      .map(fn => fn(item, delimiter))
      .reduce((r, c) => [...r, ...c], [])
  ).filter(Boolean);
}

/**
 * Generates list of items to share.
 * See ./rules.js to configure sharing extensions.
 * @param {string|string[]} items - file/folder paths
 * @param {string} [delimiter='/']
 * @return {string[]}
 */
export default function generateSharingList (items, delimiter = '/') {
  const asArray = items && Array.isArray(items) ? items : [items];
  const fullList = asArray
    .filter(Boolean)
    .map(item => generateExtendedListForItem(item, delimiter))
    .reduce((r, c) => [...r, ...c], []);
  return [...(new Set(fullList))];
}
