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

import {CP_NODEUP_RETRY_COUNT} from './parameters';

describe('isNodeUpRetryCountParameter', () => {
  let isNodeUpRetryCountParameter;

  beforeAll(() => {
    isNodeUpRetryCountParameter =
      require('./parameter-utilities').isNodeUpRetryCountParameter;
  });

  it('is false when the preference is off', () => {
    expect(isNodeUpRetryCountParameter(
      CP_NODEUP_RETRY_COUNT,
      {preferences: {allowNodeUpRetryCount: false}}
    )).toBe(false);
  });

  it('is true for CP_NODEUP_RETRY_COUNT when the preference is on', () => {
    expect(isNodeUpRetryCountParameter(
      CP_NODEUP_RETRY_COUNT,
      {preferences: {allowNodeUpRetryCount: true}}
    )).toBe(true);
  });

  it('is false for any other parameter name, even when the preference is on', () => {
    expect(isNodeUpRetryCountParameter(
      'CP_CAP_LIMIT_MOUNTS',
      {preferences: {allowNodeUpRetryCount: true}}
    )).toBe(false);
  });
});

describe('getVisibleParameters, with the node-up-retry-count preference on', () => {
  let getVisibleParameters;

  beforeAll(() => {
    jest.resetModules();
    jest.doMock('../../../../../models/preferences/PreferencesLoad', () => ({
      allowNodeUpRetryCount: true
    }));
    getVisibleParameters = require('./parameter-utilities').getVisibleParameters;
  });

  afterAll(() => {
    jest.dontMock('../../../../../models/preferences/PreferencesLoad');
    jest.resetModules();
  });

  const buildParameter = (name) => ({name, system: true});

  it('hides CP_NODEUP_RETRY_COUNT from the system parameters list', () => {
    const parameters = [buildParameter(CP_NODEUP_RETRY_COUNT), buildParameter('CP_CAP_SGE')];
    const visible = getVisibleParameters(parameters, true, false, undefined, true)
      .map((p) => p.name);
    expect(visible).not.toContain(CP_NODEUP_RETRY_COUNT);
  });
});
