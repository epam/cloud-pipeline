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
import preferences, {
  DEFAULT_NODE_UP_RETRY_COUNT,
  DEFAULT_ASYNC_TASKS_MAX_USER_TASKS
} from './PreferencesLoad';

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

describe('asyncTasksConfig', () => {
  it('falls back to the default maxUserTasks when the preference is absent', async () => {
    stubApi({'/preferences': []});
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.asyncTasksConfig).toEqual({
      maxUserTasks: DEFAULT_ASYNC_TASKS_MAX_USER_TASKS
    });
  });

  it('reads maxUserTasks and passes the sections through', async () => {
    // The real shape: a section per task type, and `maxUserTasks` on top because it is one budget
    // across every type rather than a per-type setting.
    stubApi({
      '/preferences': [
        {
          name: 'system.async.tasks.config',
          value: JSON.stringify({
            maxUserTasks: 7,
            tasks: {
              storageDownloadAsArchive: {ttlDays: 7},
              storageUnpackArchive: {maxFilesCount: 100}
            }
          })
        }
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.asyncTasksConfig.maxUserTasks).toBe(7);
    expect(preferences.asyncTasksConfig.tasks).toEqual({
      storageDownloadAsArchive: {ttlDays: 7},
      storageUnpackArchive: {maxFilesCount: 100}
    });
  });

  it('falls back to the default maxUserTasks when the key is absent', async () => {
    stubApi({
      '/preferences': [
        {
          name: 'system.async.tasks.config',
          value: JSON.stringify({tasks: {storageDownloadAsArchive: {ttlDays: 14}}})
        }
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.asyncTasksConfig.maxUserTasks)
      .toBe(DEFAULT_ASYNC_TASKS_MAX_USER_TASKS);
    expect(preferences.asyncTasksConfig.tasks)
      .toEqual({storageDownloadAsArchive: {ttlDays: 14}});
  });

  it('does not read maxUserTasks out of a section', async () => {
    // A copy inside a section must not win, or an administrator would have two of a value
    // that is deliberately single - see the api's own AsyncTasksConfig.
    stubApi({
      '/preferences': [
        {
          name: 'system.async.tasks.config',
          value: JSON.stringify({tasks: {storageDownloadAsArchive: {maxUserTasks: 99}}})
        }
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.asyncTasksConfig.maxUserTasks)
      .toBe(DEFAULT_ASYNC_TASKS_MAX_USER_TASKS);
  });

  it('falls back to the default maxUserTasks and does not throw for a bad value', async () => {
    stubApi({
      '/preferences': [
        {name: 'system.async.tasks.config', value: 'not-json'}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(() => preferences.asyncTasksConfig).not.toThrow();
    expect(preferences.asyncTasksConfig).toEqual({
      maxUserTasks: DEFAULT_ASYNC_TASKS_MAX_USER_TASKS
    });
  });

  it('falls back to the default maxUserTasks when the value is not a JSON object', async () => {
    stubApi({
      '/preferences': [
        {name: 'system.async.tasks.config', value: JSON.stringify([1, 2, 3])}
      ]
    });
    preferences.invalidateCache();
    await preferences.fetch();

    expect(preferences.asyncTasksConfig).toEqual({
      maxUserTasks: DEFAULT_ASYNC_TASKS_MAX_USER_TASKS
    });
  });
});
