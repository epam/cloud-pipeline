/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import Remote from '../basic/Remote';
const FETCH_INTERVAL_SECONDS = 60;

class ActiveNotifications extends Remote {
  constructor () {
    super();
    this.url = '/notification/active';
    setInterval(async () => {
      await this.fetch();
      if (this.onFetched) {
        this.onFetched(this);
      }
    }, FETCH_INTERVAL_SECONDS * 1000);
  }

  onFetched;
}

export default new ActiveNotifications();
