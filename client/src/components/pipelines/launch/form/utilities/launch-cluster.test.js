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
  getNodeUpRetryCountParameter,
  getNodeUpRetryCountFieldValue,
  getNodeUpRetryCountDefaultValue,
  nodeUpRetryCountExceedsMax,
  getMaxNodeUpRetryCount,
  getAllSkippedSystemParametersList,
  getSkippedSystemParametersList
} from './launch-cluster';
import {CP_NODEUP_RETRY_COUNT} from './parameters';

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

  it('is undefined for a value above the maximum', () => {
    expect(getNodeUpRetryCountParameter('1000', preferences)).toBeUndefined();
    expect(getNodeUpRetryCountParameter('99999999999', preferences)).toBeUndefined();
  });

  it('allows a value above 999 when the preference itself is higher', () => {
    expect(getNodeUpRetryCountParameter('5000', {...preferences, defaultNodeUpRetryCount: 5000}))
      .toEqual({
        type: 'int',
        required: false,
        value: 5000
      });
  });

  it('returns the parameter config for the maximum value', () => {
    expect(getNodeUpRetryCountParameter('999', preferences)).toEqual({
      type: 'int',
      required: false,
      value: 999
    });
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

describe('getNodeUpRetryCountDefaultValue', () => {
  it('is the configured value when there is one, regardless of the preference', () => {
    expect(getNodeUpRetryCountDefaultValue('12', {defaultNodeUpRetryCount: 5}))
      .toBe('12');
  });

  it('falls back to the preference value when there is no configured value', () => {
    expect(getNodeUpRetryCountDefaultValue(undefined, {defaultNodeUpRetryCount: 7}))
      .toBe('7');
  });

  it('falls back to 5 when neither a configured value nor preferences are available', () => {
    expect(getNodeUpRetryCountDefaultValue(undefined, undefined)).toBe('5');
    expect(getNodeUpRetryCountDefaultValue(undefined, {})).toBe('5');
  });
});

describe('getMaxNodeUpRetryCount', () => {
  it('is 999 when the preference is lower, or missing', () => {
    expect(getMaxNodeUpRetryCount({defaultNodeUpRetryCount: 5})).toBe(999);
    expect(getMaxNodeUpRetryCount({})).toBe(999);
    expect(getMaxNodeUpRetryCount(undefined)).toBe(999);
  });

  it('is the preference value when that is higher, so the placeholder stays typeable', () => {
    expect(getMaxNodeUpRetryCount({defaultNodeUpRetryCount: 5000})).toBe(5000);
  });
});

describe('nodeUpRetryCountExceedsMax', () => {
  it('is false for a value up to the maximum', () => {
    expect(nodeUpRetryCountExceedsMax('1', 999)).toBe(false);
    expect(nodeUpRetryCountExceedsMax('999', 999)).toBe(false);
  });

  it('is true for a value above the maximum', () => {
    expect(nodeUpRetryCountExceedsMax('1000', 999)).toBe(true);
    expect(nodeUpRetryCountExceedsMax(1000, 999)).toBe(true);
  });

  it('is false for a value that is not set or not a positive integer, ' +
    'since the pattern rule reports those', () => {
    expect(nodeUpRetryCountExceedsMax(undefined, 999)).toBe(false);
    expect(nodeUpRetryCountExceedsMax('', 999)).toBe(false);
    expect(nodeUpRetryCountExceedsMax('abc', 999)).toBe(false);
  });
});

describe('getAllSkippedSystemParametersList', () => {
  it('never skips CP_NODEUP_RETRY_COUNT, since a parameter row must stay visible ' +
    'in an editor when it is already present', () => {
    expect(getAllSkippedSystemParametersList({allowNodeUpRetryCount: true}))
      .not.toContain(CP_NODEUP_RETRY_COUNT);
  });
});

describe('getSkippedSystemParametersList', () => {
  const buildController = (preferences, state = {}) => ({
    state,
    props: {preferences}
  });

  it('never skips CP_NODEUP_RETRY_COUNT, since a parameter row must stay visible ' +
    'in an editor when it is already present', () => {
    const controller = buildController(
      {allowNodeUpRetryCount: true},
      {launchCluster: true, autoScaledCluster: true}
    );
    expect(getSkippedSystemParametersList(controller)).not.toContain(CP_NODEUP_RETRY_COUNT);
  });
});
