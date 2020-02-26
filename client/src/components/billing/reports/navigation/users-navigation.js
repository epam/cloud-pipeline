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

export default function (filters, users, {key}) {
  if (filters && users) {
    users
      .fetchIfNeededOrWait()
      .then(() => {
        if (users.loaded) {
          const r = new RegExp(`^${key}$`, 'i');
          const [user] = (users.value || []).filter(({userName}) => r.test(userName));
          if (user) {
            filters.reportNavigation(
              Filters.reportsRoutes.general.name,
              {id: user.id, type: Filters.runnerTypes.user}
            );
          }
        }
      })
      .catch(() => {});
  }
}
