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
import {
  isNodeUpRetryCountParameter,
  getVisibleParameters,
  parametersToPayloadParams,
  parametersToConfigurationParams
} from './parameter-utilities';

describe('isNodeUpRetryCountParameter', () => {
  it('is true for CP_NODEUP_RETRY_COUNT', () => {
    expect(isNodeUpRetryCountParameter(CP_NODEUP_RETRY_COUNT)).toBe(true);
  });

  it('is case-insensitive', () => {
    expect(isNodeUpRetryCountParameter('cp_nodeup_retry_count')).toBe(true);
  });

  it('is false for any other parameter name', () => {
    expect(isNodeUpRetryCountParameter('CP_CAP_LIMIT_MOUNTS')).toBe(false);
  });
});

describe('getVisibleParameters', () => {
  const buildParameter = (name) => ({name, system: true});
  const parameters = [buildParameter(CP_NODEUP_RETRY_COUNT), buildParameter('CP_CAP_SGE')];

  it('hides CP_NODEUP_RETRY_COUNT from the system parameters list when asked to', () => {
    const visible = getVisibleParameters(parameters, true, false, undefined, true, true)
      .map((p) => p.name);
    expect(visible).not.toContain(CP_NODEUP_RETRY_COUNT);
  });

  it('shows CP_NODEUP_RETRY_COUNT in the system parameters list by default, so an ' +
    'already-present value stays visible in a configuration editor', () => {
    const visible = getVisibleParameters(parameters, true, false, undefined, true)
      .map((p) => p.name);
    expect(visible).toContain(CP_NODEUP_RETRY_COUNT);
  });
});

describe('parametersToPayloadParams / parametersToConfigurationParams', () => {
  const parameters = [
    {
      name: CP_NODEUP_RETRY_COUNT,
      value: '7',
      system: true,
      configs: [{configId: 'default', type: 'int', value: '7'}]
    }
  ];

  it('parametersToConfigurationParams keeps CP_NODEUP_RETRY_COUNT, so a stored value ' +
    'survives saving a configuration', () => {
    const {parameters: result} = parametersToConfigurationParams(parameters);
    expect(result).toHaveProperty(CP_NODEUP_RETRY_COUNT);
  });

  it('parametersToPayloadParams drops CP_NODEUP_RETRY_COUNT when asked to, so the launch ' +
    'form\'s dedicated field is the single source of the launched value', () => {
    const result = parametersToPayloadParams(parameters, {hideNodeUpRetryCount: true});
    expect(result).not.toHaveProperty(CP_NODEUP_RETRY_COUNT);
  });

  it('parametersToPayloadParams keeps CP_NODEUP_RETRY_COUNT by default', () => {
    const result = parametersToPayloadParams(parameters);
    expect(result).toHaveProperty(CP_NODEUP_RETRY_COUNT);
  });
});
