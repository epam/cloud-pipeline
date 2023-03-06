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

import moment from 'moment-timezone';

const uuids = new Map();

export default function generateUUID () {
  const main = Number(moment.utc().format('YYYYMMDDHHmmssSSS')).toString(16);
  if (!uuids.has(main)) {
    uuids.set(main, 0);
    return main;
  }
  const extra = uuids.get(main) + 1;
  uuids.set(main, extra);
  return `${main}${Number(extra).toString(16)}`;
}
