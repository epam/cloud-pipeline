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

import {
  getNodeUpRetryCountSkippedParameters,
  getAllSkippedSystemParametersList,
  getSkippedSystemParametersList
} from './launch-cluster';
import {CP_NODEUP_RETRY_COUNT} from './parameters';

describe('getNodeUpRetryCountSkippedParameters', () => {
  it('is empty when the preference is off', () => {
    expect(getNodeUpRetryCountSkippedParameters({allowNodeUpRetryCount: false})).toEqual([]);
  });

  it('is empty when preferences are missing', () => {
    expect(getNodeUpRetryCountSkippedParameters(undefined)).toEqual([]);
  });

  it('skips CP_NODEUP_RETRY_COUNT when the preference is on', () => {
    expect(getNodeUpRetryCountSkippedParameters({allowNodeUpRetryCount: true}))
      .toEqual([CP_NODEUP_RETRY_COUNT]);
  });
});

describe('getAllSkippedSystemParametersList', () => {
  it('does not skip CP_NODEUP_RETRY_COUNT when the preference is off', () => {
    expect(getAllSkippedSystemParametersList({allowNodeUpRetryCount: false}))
      .not.toContain(CP_NODEUP_RETRY_COUNT);
  });

  it('skips CP_NODEUP_RETRY_COUNT when the preference is on', () => {
    expect(getAllSkippedSystemParametersList({allowNodeUpRetryCount: true}))
      .toContain(CP_NODEUP_RETRY_COUNT);
  });
});

describe('getSkippedSystemParametersList', () => {
  const buildController = (preferences, state = {}) => ({
    state,
    props: {preferences}
  });

  it('does not skip CP_NODEUP_RETRY_COUNT when the preference is off', () => {
    const controller = buildController({allowNodeUpRetryCount: false});
    expect(getSkippedSystemParametersList(controller)).not.toContain(CP_NODEUP_RETRY_COUNT);
  });

  it('skips CP_NODEUP_RETRY_COUNT when the preference is on, without a cluster', () => {
    const controller = buildController({allowNodeUpRetryCount: true});
    expect(getSkippedSystemParametersList(controller)).toContain(CP_NODEUP_RETRY_COUNT);
  });

  it('skips CP_NODEUP_RETRY_COUNT when the preference is on, with an autoscaled cluster', () => {
    const controller = buildController(
      {allowNodeUpRetryCount: true},
      {launchCluster: true, autoScaledCluster: true}
    );
    expect(getSkippedSystemParametersList(controller)).toContain(CP_NODEUP_RETRY_COUNT);
  });
});
