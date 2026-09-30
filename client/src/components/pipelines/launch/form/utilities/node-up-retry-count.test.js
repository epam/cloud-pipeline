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
  applyNodeUpRetryCountDefault,
  applyNodeUpRetryCountValue,
  getDefaultNodeUpRetryCount,
  getMaxNodeUpRetryCount,
  getNodeUpRetryCountError,
  getNodeUpRetryCountSkippedParameters,
  isNodeUpRetryCountParameter,
  nodeUpRetryCountNameIsReserved,
  nodeUpRetryCountValueIsEmpty,
  nodeUpRetryCountValuesDiffer
} from './node-up-retry-count';
import {CP_NODEUP_RETRY_COUNT} from './parameters';

const preferences = {allowNodeUpRetryCount: true, defaultNodeUpRetryCount: 4};

describe('isNodeUpRetryCountParameter', () => {
  it('is true for CP_NODEUP_RETRY_COUNT, in any case', () => {
    expect(isNodeUpRetryCountParameter(CP_NODEUP_RETRY_COUNT)).toBe(true);
    expect(isNodeUpRetryCountParameter('cp_nodeup_retry_count')).toBe(true);
  });

  it('is false for any other name', () => {
    expect(isNodeUpRetryCountParameter('CP_CAP_LIMIT_MOUNTS')).toBe(false);
    expect(isNodeUpRetryCountParameter(undefined)).toBe(false);
  });
});

describe('getNodeUpRetryCountSkippedParameters', () => {
  it('skips CP_NODEUP_RETRY_COUNT when the preference is on', () => {
    expect(getNodeUpRetryCountSkippedParameters(preferences)).toEqual([CP_NODEUP_RETRY_COUNT]);
  });

  it('skips nothing when the preference is off', () => {
    expect(getNodeUpRetryCountSkippedParameters({allowNodeUpRetryCount: false})).toEqual([]);
    expect(getNodeUpRetryCountSkippedParameters(undefined)).toEqual([]);
  });
});

describe('nodeUpRetryCountNameIsReserved', () => {
  it('is true for CP_NODEUP_RETRY_COUNT, in any case, when the preference is on', () => {
    expect(nodeUpRetryCountNameIsReserved(CP_NODEUP_RETRY_COUNT, preferences)).toBe(true);
    expect(nodeUpRetryCountNameIsReserved('cp_nodeup_retry_count', preferences)).toBe(true);
  });

  it('is false for any other name', () => {
    expect(nodeUpRetryCountNameIsReserved('OTHER', preferences)).toBe(false);
  });

  it('is false when the preference is off', () => {
    expect(nodeUpRetryCountNameIsReserved(CP_NODEUP_RETRY_COUNT, {allowNodeUpRetryCount: false}))
      .toBe(false);
    expect(nodeUpRetryCountNameIsReserved(CP_NODEUP_RETRY_COUNT, undefined)).toBe(false);
  });
});

describe('nodeUpRetryCountValueIsEmpty', () => {
  it('is true for a value that is not set', () => {
    expect(nodeUpRetryCountValueIsEmpty(undefined)).toBe(true);
    expect(nodeUpRetryCountValueIsEmpty(null)).toBe(true);
    expect(nodeUpRetryCountValueIsEmpty('')).toBe(true);
    expect(nodeUpRetryCountValueIsEmpty(' ')).toBe(true);
  });

  it('is false for a set value', () => {
    expect(nodeUpRetryCountValueIsEmpty('7')).toBe(false);
    expect(nodeUpRetryCountValueIsEmpty(7)).toBe(false);
  });
});

describe('getDefaultNodeUpRetryCount', () => {
  it('is the preference value', () => {
    expect(getDefaultNodeUpRetryCount(preferences)).toBe(4);
  });

  it('is 5 when preferences are missing', () => {
    expect(getDefaultNodeUpRetryCount(undefined)).toBe(5);
    expect(getDefaultNodeUpRetryCount({})).toBe(5);
  });
});

describe('getMaxNodeUpRetryCount', () => {
  it('is 999 when the preference is lower, or missing', () => {
    expect(getMaxNodeUpRetryCount(preferences)).toBe(999);
    expect(getMaxNodeUpRetryCount({})).toBe(999);
    expect(getMaxNodeUpRetryCount(undefined)).toBe(999);
  });

  it('is the preference value when that is higher', () => {
    expect(getMaxNodeUpRetryCount({defaultNodeUpRetryCount: 5000})).toBe(5000);
  });
});

describe('getNodeUpRetryCountError', () => {
  it('reports nothing for an empty value, since the parameter is optional', () => {
    expect(getNodeUpRetryCountError(undefined, preferences)).toBeUndefined();
    expect(getNodeUpRetryCountError('', preferences)).toBeUndefined();
  });

  it('reports nothing for a positive integer up to the maximum', () => {
    expect(getNodeUpRetryCountError('1', preferences)).toBeUndefined();
    expect(getNodeUpRetryCountError('999', preferences)).toBeUndefined();
    expect(getNodeUpRetryCountError(10, preferences)).toBeUndefined();
  });

  it('reports a value that is not a positive integer', () => {
    ['0', '00', '-1', 'abc', '1.5', ' 7'].forEach((value) => {
      expect(getNodeUpRetryCountError(value, preferences))
        .toBe('Please enter a valid positive integer number');
    });
  });

  it('reports a value above the maximum', () => {
    expect(getNodeUpRetryCountError('1000', preferences)).toBe('Maximum value is 999');
  });

  it('allows a value above 999 when the preference is higher', () => {
    expect(getNodeUpRetryCountError('5000', {defaultNodeUpRetryCount: 5000})).toBeUndefined();
    expect(getNodeUpRetryCountError('5001', {defaultNodeUpRetryCount: 5000}))
      .toBe('Maximum value is 5000');
  });
});

describe('nodeUpRetryCountValuesDiffer', () => {
  it('is false when both values are empty', () => {
    expect(nodeUpRetryCountValuesDiffer(undefined, '')).toBe(false);
    expect(nodeUpRetryCountValuesDiffer(null, undefined)).toBe(false);
  });

  it('is false for the same value as a string and as a number', () => {
    expect(nodeUpRetryCountValuesDiffer('7', 7)).toBe(false);
  });

  it('is true when only one value is empty, or the values differ', () => {
    expect(nodeUpRetryCountValuesDiffer('7', undefined)).toBe(true);
    expect(nodeUpRetryCountValuesDiffer('', '7')).toBe(true);
    expect(nodeUpRetryCountValuesDiffer('7', '8')).toBe(true);
  });
});

describe('applyNodeUpRetryCountValue', () => {
  const other = {name: 'OTHER', type: 'string', value: 'a'};

  it('adds the control value when the preference is on', () => {
    expect(applyNodeUpRetryCountValue([other], '7', preferences)).toEqual([
      other,
      {name: CP_NODEUP_RETRY_COUNT, type: 'int', value: '7'}
    ]);
  });

  it('adds nothing when the control is empty, so the parameter stays unset', () => {
    expect(applyNodeUpRetryCountValue([other], '', preferences)).toEqual([other]);
    expect(applyNodeUpRetryCountValue([other], undefined, preferences)).toEqual([other]);
  });

  it('lets the control value replace one in the list when the preference is on', () => {
    const params = [other, {name: CP_NODEUP_RETRY_COUNT, type: 'int', value: '3'}];
    expect(applyNodeUpRetryCountValue(params, '7', preferences)).toEqual([
      other,
      {name: CP_NODEUP_RETRY_COUNT, type: 'int', value: '7'}
    ]);
  });

  it('leaves the list as it is, and ignores the control, when the preference is off', () => {
    [
      [other, {name: CP_NODEUP_RETRY_COUNT, type: 'int', value: '3'}],
      [other, {name: CP_NODEUP_RETRY_COUNT, type: 'int', value: ''}]
    ].forEach((params) => {
      expect(applyNodeUpRetryCountValue(params, '7', {allowNodeUpRetryCount: false}))
        .toBe(params);
      expect(applyNodeUpRetryCountValue(params, '7', undefined)).toBe(params);
    });
  });
});

describe('applyNodeUpRetryCountDefault', () => {
  it('adds the preference value when the parameter is not set', () => {
    expect(applyNodeUpRetryCountDefault({OTHER: {value: 'a'}}, preferences)).toEqual({
      OTHER: {value: 'a'},
      [CP_NODEUP_RETRY_COUNT]: {type: 'int', required: false, value: 4}
    });
  });

  it('keeps the value the user set', () => {
    const params = {[CP_NODEUP_RETRY_COUNT]: {type: 'int', value: '7'}};
    expect(applyNodeUpRetryCountDefault(params, preferences)).toBe(params);
  });

  it('keeps a value set under a name in another case', () => {
    const params = {cp_nodeup_retry_count: {type: 'int', value: '7'}};
    expect(applyNodeUpRetryCountDefault(params, preferences)).toBe(params);
  });

  it('adds nothing when the preference is off', () => {
    const params = {};
    expect(applyNodeUpRetryCountDefault(params, {...preferences, allowNodeUpRetryCount: false}))
      .toBe(params);
    expect(applyNodeUpRetryCountDefault(params, undefined)).toBe(params);
  });
});
