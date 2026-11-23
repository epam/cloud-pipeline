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

import {showOptionalParametersFilter} from './utils';

const hidden = {enabled: false, defaultValue: undefined};

describe('showOptionalParametersFilter', () => {
  it('hides the checkbox in both sections without launch form settings', () => {
    expect(showOptionalParametersFilter(undefined)).toEqual({pipelines: hidden, tools: hidden});
    expect(showOptionalParametersFilter({})).toEqual({pipelines: hidden, tools: hidden});
  });

  it('reads each section on its own', () => {
    const settings = {
      pipelines: {'optional-parameters-filter': true},
      tools: {'optional-parameters-filter': {default_value: true}}
    };
    expect(showOptionalParametersFilter(settings)).toEqual({
      pipelines: {enabled: true, defaultValue: undefined},
      tools: {enabled: true, defaultValue: true}
    });
  });

  it('matches the keys against the overrides', () => {
    const settings = {
      tools: {'optional-parameters-filter': {overrides: {'registry/group/tool': false}}}
    };
    expect(showOptionalParametersFilter(settings, ['registry/group/tool']).tools)
      .toEqual({enabled: false, defaultValue: false});
    expect(showOptionalParametersFilter(settings, ['registry/group/other']).tools)
      .toEqual({enabled: true, defaultValue: false});
  });
});
