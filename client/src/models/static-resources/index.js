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

import {API_PATH, SERVER} from '../../config';

const removeLeadingSlash = o => (o || '').startsWith('/') ? o.slice(1) : (o || '');
const removeTrailingSlash = o => (o || '').endsWith('/') ? o.slice(0, -1) : (o || '');
const removeSlash = o => removeLeadingSlash(removeTrailingSlash(o));

const prefix = [SERVER, API_PATH].map(removeSlash).join('/');

export function getStaticResourceUrl (storageName, path) {
  return `${prefix}/static-resources/${storageName}/${path}`;
}
