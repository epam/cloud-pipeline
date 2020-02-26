/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Filters from '../filters';

export default function (filters, {group, key}) {
  if (filters && group) {
    if (/^storage$/i.test(group)) {
      if (key && /^file$/i.test(key)) {
        filters.reportNavigation(Filters.reportsRoutes.storages.file.name);
      } else if (key && /^object$/i.test(key)) {
        filters.reportNavigation(Filters.reportsRoutes.storages.object.name);
      } else {
        filters.reportNavigation(Filters.reportsRoutes.storages.name);
      }
    } else if (/^compute instances$/i.test(group)) {
      if (key && /^cpu$/i.test(key)) {
        filters.reportNavigation(Filters.reportsRoutes.instances.cpu.name);
      } else if (key && /^gpu$/i.test(key)) {
        filters.reportNavigation(Filters.reportsRoutes.instances.gpu.name);
      } else {
        filters.reportNavigation(Filters.reportsRoutes.instances.name);
      }
    }
  }
}
