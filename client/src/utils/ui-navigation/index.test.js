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

import UINavigation from './index';

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
