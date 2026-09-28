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
  getNodeUpRetryCountParameter,
  getNodeUpRetryCountFieldValue,
  nodeUpRetryCountChanged,
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

describe('getNodeUpRetryCountParameter', () => {
  const preferences = {allowNodeUpRetryCount: true, defaultNodeUpRetryCount: 5};

  it('is undefined when the preference is off', () => {
    expect(getNodeUpRetryCountParameter('10', {...preferences, allowNodeUpRetryCount: false}))
      .toBeUndefined();
  });

  it('is undefined when preferences are missing', () => {
    expect(getNodeUpRetryCountParameter('10', undefined)).toBeUndefined();
  });

  it('is undefined for an empty value', () => {
    expect(getNodeUpRetryCountParameter('', preferences)).toBeUndefined();
    expect(getNodeUpRetryCountParameter(undefined, preferences)).toBeUndefined();
    expect(getNodeUpRetryCountParameter(null, preferences)).toBeUndefined();
  });

  it('is undefined for zero, since it is not a positive integer', () => {
    expect(getNodeUpRetryCountParameter('0', preferences)).toBeUndefined();
    expect(getNodeUpRetryCountParameter('00', preferences)).toBeUndefined();
  });

  it('is undefined for a non-integer value', () => {
    expect(getNodeUpRetryCountParameter('abc', preferences)).toBeUndefined();
    expect(getNodeUpRetryCountParameter('-1', preferences)).toBeUndefined();
  });

  it('returns the parameter config even when the value equals the preference default', () => {
    expect(getNodeUpRetryCountParameter('5', preferences)).toEqual({
      type: 'int',
      required: false,
      value: 5
    });
  });

  it('returns the parameter config for a value that differs from the default', () => {
    expect(getNodeUpRetryCountParameter(' 10 ', preferences)).toEqual({
      type: 'int',
      required: false,
      value: 10
    });
  });
});

describe('getNodeUpRetryCountFieldValue', () => {
  it('is undefined for a value that is not set', () => {
    expect(getNodeUpRetryCountFieldValue(undefined)).toBeUndefined();
    expect(getNodeUpRetryCountFieldValue(null)).toBeUndefined();
    expect(getNodeUpRetryCountFieldValue(' ')).toBeUndefined();
  });

  it('is the trimmed string for a stored value', () => {
    expect(getNodeUpRetryCountFieldValue(7)).toBe('7');
    expect(getNodeUpRetryCountFieldValue(' 7 ')).toBe('7');
  });
});

describe('nodeUpRetryCountChanged', () => {
  it('is false when neither value is set', () => {
    expect(nodeUpRetryCountChanged(undefined, '')).toBe(false);
    expect(nodeUpRetryCountChanged(null, undefined)).toBe(false);
  });

  it('is false when the field keeps the stored value', () => {
    expect(nodeUpRetryCountChanged(7, '7')).toBe(false);
  });

  it('is true when a value is set, changed or cleared', () => {
    expect(nodeUpRetryCountChanged(undefined, '5')).toBe(true);
    expect(nodeUpRetryCountChanged(7, '8')).toBe(true);
    expect(nodeUpRetryCountChanged(7, undefined)).toBe(true);
  });
});

describe('getAllSkippedSystemParametersList', () => {
  it('does not skip CP_NODEUP_RETRY_COUNT when the preference is off', () => {
    expect(getAllSkippedSystemParametersList({allowNodeUpRetryCount: false}))
      .not.toContain(CP_NODEUP_RETRY_COUNT);
  });

  it('does not skip CP_NODEUP_RETRY_COUNT when the preference is on, since this list has ' +
    'no dedicated field for it', () => {
    expect(getAllSkippedSystemParametersList({allowNodeUpRetryCount: true}))
      .not.toContain(CP_NODEUP_RETRY_COUNT);
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

  it('does not skip CP_NODEUP_RETRY_COUNT when the preference is on, without a cluster, ' +
    'since this list has no dedicated field for it', () => {
    const controller = buildController({allowNodeUpRetryCount: true});
    expect(getSkippedSystemParametersList(controller)).not.toContain(CP_NODEUP_RETRY_COUNT);
  });

  it('does not skip CP_NODEUP_RETRY_COUNT when the preference is on, with an autoscaled ' +
    'cluster, since this list has no dedicated field for it', () => {
    const controller = buildController(
      {allowNodeUpRetryCount: true},
      {launchCluster: true, autoScaledCluster: true}
    );
    expect(getSkippedSystemParametersList(controller)).not.toContain(CP_NODEUP_RETRY_COUNT);
  });
});
