/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import UsageCreditsUsers from './UsageCreditsUsers';

const MOCK_USERS = [
  {
    userId: 1,
    creditsBalance: {
      timestamp: '2026-07-08 17:32:10',
      current: 2800
    }
  },
  {
    userId: 2,
    creditsBalance: {
      timestamp: '2026-07-08 14:05:44',
      current: 1500
    }
  },
  {
    userId: 3,
    creditsBalance: {
      timestamp: '2026-07-09 08:12:33',
      current: 24
    }
  }
];

const DEFAULT_BALANCE_TIMESTAMP = '2026-07-08 17:32:10';

function randomCreditsBalance () {
  const min = 500;
  const max = 5000;
  const step = 100;
  const steps = (max - min) / step;
  const current = min + Math.floor(Math.random() * (steps + 1)) * step;
  return {
    timestamp: DEFAULT_BALANCE_TIMESTAMP,
    current
  };
}

// Delete this class when /usage/credits/users backend is ready.
export default class UsageCreditsUsersMock extends UsageCreditsUsers {
  async send (body = {}) {
    this._pending = true;
    this.failed = false;
    this.error = undefined;
    this.networkError = undefined;
    try {
      const userIds = body.userIds || [];
      const elements = userIds.length
        ? userIds.map((userId) => {
          const existing = MOCK_USERS.find((u) => Number(u.userId) === Number(userId));
          return existing || {userId, creditsBalance: randomCreditsBalance()};
        })
        : MOCK_USERS;
      this.update({
        status: 'OK',
        payload: {
          elements,
          totalCount: elements.length
        }
      });
    } finally {
      this._pending = false;
    }
  }
}
