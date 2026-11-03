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

import {stubApi} from '@test';
import preferences, {DEFAULT_NODE_UP_RETRY_COUNT} from './PreferencesLoad';

describe('allowNodeUpRetryCount', () => {
  it('is false when the preference is absent', async () => {
    stubApi({'/preferences': []});
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.allowNodeUpRetryCount).toBe(false);
  });

  it('is true only when the preference is the string "true"', async () => {
    stubApi({
      '/preferences': [
        {name: 'ui.launch.allow.nodeup.count', value: 'true'}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.allowNodeUpRetryCount).toBe(true);
  });

  it('is false for any other value', async () => {
    stubApi({
      '/preferences': [
        {name: 'ui.launch.allow.nodeup.count', value: 'false'}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.allowNodeUpRetryCount).toBe(false);
  });
});

describe('defaultNodeUpRetryCount', () => {
  it('falls back to the default when the preference is absent', async () => {
    stubApi({'/preferences': []});
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.defaultNodeUpRetryCount).toBe(DEFAULT_NODE_UP_RETRY_COUNT);
  });

  it('reads the numeric value of cluster.nodeup.retry.count', async () => {
    stubApi({
      '/preferences': [
        {name: 'cluster.nodeup.retry.count', value: '7'}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.defaultNodeUpRetryCount).toBe(7);
  });
});
