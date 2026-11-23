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

import UINavigation, {fetchGroupsAttributes} from './index';

// A plain stand-in for the `preferences` store: only what fetchGroupsAttributes reads.
const preferencesStub = (groupsUIPreferences) => ({
  fetchIfNeededOrWait: () => Promise.resolve(),
  groupsUIPreferences
});

const fetchFor = (groupsUIPreferences, groups, options) => fetchGroupsAttributes(
  preferencesStub(groupsUIPreferences),
  groups,
  options
);

describe('fetchGroupsAttributes', () => {
  test('the first group in the preference wins when no priority is set', async () => {
    const result = await fetchFor({
      A: {'ui-home-page': 'runs'},
      B: {'ui-home-page': 'library'}
    }, ['A', 'B']);
    expect(result.homePage).toBe('runs');
  });

  test('the group with the higher priority wins', async () => {
    const result = await fetchFor({
      A: {priority: 1, 'ui-home-page': 'runs'},
      B: {priority: 10, 'ui-home-page': 'library'}
    }, ['A', 'B']);
    expect(result.homePage).toBe('library');
  });

  test('a missing priority counts as 0', async () => {
    const result = await fetchFor({
      A: {priority: -1, 'ui-home-page': 'runs'},
      B: {'ui-home-page': 'library'}
    }, ['A', 'B']);
    expect(result.homePage).toBe('library');
  });

  test('groups with the same priority keep the preference order', async () => {
    const result = await fetchFor({
      A: {priority: 5, 'ui-home-page': 'runs'},
      B: {priority: '5', 'ui-home-page': 'library'}
    }, ['A', 'B']);
    expect(result.homePage).toBe('runs');
  });

  test('a priority that is not a number counts as 0', async () => {
    const result = await fetchFor({
      A: {priority: 'high', 'ui-home-page': 'runs'},
      B: {priority: 1, 'ui-home-page': 'library'}
    }, ['A', 'B']);
    expect(result.homePage).toBe('library');
  });

  test('groups the user is not a member of are ignored', async () => {
    const result = await fetchFor({
      A: {priority: 100, 'ui-home-page': 'runs'},
      B: {'ui-home-page': 'library'}
    }, ['B']);
    expect(result.homePage).toBe('library');
  });

  test('ui-library-expanded follows the priority too', async () => {
    const result = await fetchFor({
      A: {'ui-library-expanded': 'false'},
      B: {priority: 1, 'ui-library-expanded': 'true'}
    }, ['A', 'B']);
    expect(result.libraryExpanded).toBe(true);
  });

  test('ui-run-logs-main-task set to false by a higher priority group wins', async () => {
    const result = await fetchFor({
      A: {'ui-run-logs-main-task': 'true'},
      B: {priority: 1, 'ui-run-logs-main-task': 'false'}
    }, ['A', 'B']);
    expect(result.runLogsMainTask).toBe(false);
  });

  test('settings from the user\'s own attributes win over any group', async () => {
    const result = await fetchFor({
      A: {priority: 100, 'ui-home-page': 'runs'}
    }, ['A'], {homePage: 'library'});
    expect(result.homePage).toBe('library');
  });

  test('a group with an empty config does not drop the other groups', async () => {
    const result = await fetchFor({
      A: {},
      B: {'ui-home-page': 'library'}
    }, ['A', 'B']);
    expect(result.homePage).toBe('library');
  });
});

function createUINavigation () {
  const user = {loaded: false, fetchIfNeededOrWait: () => Promise.resolve()};
  return new UINavigation(user, {});
}

describe('UINavigation utils.showOptionalParametersFilter', () => {
  it('resolves the loaded launch form settings for the given keys', () => {
    const uiNavigation = createUINavigation();
    uiNavigation.launchForm = {
      pipelines: {
        'optional-parameters-filter': {overrides: {7: {default_value: true}}}
      }
    };
    expect(uiNavigation.utils.showOptionalParametersFilter([7, 'pipeline']).pipelines)
      .toEqual({enabled: true, defaultValue: true});
    expect(uiNavigation.utils.showOptionalParametersFilter([8, 'other']).pipelines)
      .toEqual({enabled: true, defaultValue: false});
  });

  it('hides the checkbox without launch form settings', () => {
    const uiNavigation = createUINavigation();
    uiNavigation.launchForm = undefined;
    expect(uiNavigation.utils.showOptionalParametersFilter().tools)
      .toEqual({enabled: false, defaultValue: undefined});
  });
});
