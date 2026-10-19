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
  correctCapacityBlockValue,
  decodeInstanceTypeValue,
  encodeCapacityBlockValue,
  findCapacityBlockPool,
  getCapacityBlockAdditionalParameterNames,
  getCapacityBlockOptions
} from './utilities';

describe('findCapacityBlockPool', () => {
  it('matches the instance type exactly and case-insensitively', () => {
    const pool = {id: 1, instanceType: 'M5.Large', regionId: 1};

    expect(findCapacityBlockPool([pool], 'm5.large')).toBe(pool);
    expect(findCapacityBlockPool([pool], 'm5.xlarge')).toBeUndefined();
  });

  it('requires the region to match when one is given', () => {
    const pool = {id: 1, instanceType: 'm5.large', regionId: 1};

    expect(findCapacityBlockPool([pool], 'm5.large', {regionId: 1})).toBe(pool);
    expect(findCapacityBlockPool([pool], 'm5.large', {regionId: 2})).toBeUndefined();
  });

  it('tolerates a missing regionId rather than returning nothing', () => {
    const pool = {id: 1, instanceType: 'm5.large', regionId: 1};

    expect(findCapacityBlockPool([pool], 'm5.large')).toBe(pool);
    expect(findCapacityBlockPool([pool], 'm5.large', {})).toBe(pool);
  });

  it('prefers the pool with a launchConfig when several match', () => {
    const withoutConfig = {id: 1, instanceType: 'm5.large', regionId: 1};
    const withConfig = {
      id: 2,
      instanceType: 'm5.large',
      regionId: 1,
      launchConfig: {cpuRequestsEnabled: true}
    };

    expect(findCapacityBlockPool([withoutConfig, withConfig], 'm5.large')).toBe(withConfig);
    // order in the array must not matter
    expect(findCapacityBlockPool([withConfig, withoutConfig], 'm5.large')).toBe(withConfig);
  });

  it('falls back to the lowest id when the launchConfig tie-break does not decide', () => {
    const higherId = {
      id: 5,
      instanceType: 'm5.large',
      regionId: 1,
      launchConfig: {cpuRequestsEnabled: true}
    };
    const lowerId = {
      id: 2,
      instanceType: 'm5.large',
      regionId: 1,
      launchConfig: {cpuRequestsEnabled: true}
    };

    expect(findCapacityBlockPool([higherId, lowerId], 'm5.large')).toBe(lowerId);
  });

  it('returns undefined for an empty or missing pool list', () => {
    expect(findCapacityBlockPool([], 'm5.large')).toBeUndefined();
    expect(findCapacityBlockPool(undefined, 'm5.large')).toBeUndefined();
  });
});

describe('getCapacityBlockAdditionalParameterNames', () => {
  it('collects every additionalParameters key across all pools, deduped', () => {
    const pools = [
      {
        id: 1,
        launchConfig: {additionalParameters: {CP_CAP_A: 'true', CP_CAP_B: 'true'}}
      },
      {
        id: 2,
        launchConfig: {additionalParameters: {CP_CAP_B: 'true', CP_CAP_C: 'true'}}
      },
      {
        // no launchConfig at all - contributes nothing
        id: 3
      }
    ];

    expect(getCapacityBlockAdditionalParameterNames(pools).sort()).toEqual([
      'CP_CAP_A', 'CP_CAP_B', 'CP_CAP_C'
    ]);
  });

  it('returns an empty array when no pool has an additionalParameters map', () => {
    const pools = [{id: 1}, {id: 2, launchConfig: {}}];

    expect(getCapacityBlockAdditionalParameterNames(pools)).toEqual([]);
  });

  it('returns an empty array for a missing pool list', () => {
    expect(getCapacityBlockAdditionalParameterNames(undefined)).toEqual([]);
  });
});

describe('encodeCapacityBlockValue', () => {
  it('encodes the pool id as "pool:<id>"', () => {
    expect(encodeCapacityBlockValue({id: 351, name: 'pool-351'})).toBe('pool:351');
  });

  it('returns undefined for a pool with no usable id', () => {
    expect(encodeCapacityBlockValue({name: 'no-id'})).toBeUndefined();
    expect(encodeCapacityBlockValue(undefined)).toBeUndefined();
    expect(encodeCapacityBlockValue(null)).toBeUndefined();
  });
});

describe('decodeInstanceTypeValue', () => {
  const pools = [
    {id: 351, instanceType: 'm5.4xlarge', name: 'pool-351'},
    {id: 2, instanceType: 'm5.large', name: 'pool-2'}
  ];

  it('round-trips a pool through encode then decode', () => {
    const pool = pools[0];
    const value = encodeCapacityBlockValue(pool);

    expect(decodeInstanceTypeValue(value, pools)).toEqual({
      instanceType: 'm5.4xlarge',
      poolId: 351
    });
  });

  it('never decodes an encoded value to the pool: form itself - the tripwire ' +
    'a launch payload\'s instance_size/instanceType relies on', () => {
    for (const pool of pools) {
      const {instanceType} = decodeInstanceTypeValue(encodeCapacityBlockValue(pool), pools);

      expect(instanceType).toBe(pool.instanceType);
      expect(instanceType).not.toMatch(/^pool:/);
    }
  });

  it('decodes a plain instance type name to itself, with no poolId', () => {
    expect(decodeInstanceTypeValue('m5.large', pools)).toEqual({
      instanceType: 'm5.large',
      poolId: undefined
    });
  });

  it('tolerates a missing pools argument for a plain instance type', () => {
    expect(decodeInstanceTypeValue('m5.large')).toEqual({
      instanceType: 'm5.large',
      poolId: undefined
    });
  });

  it('rejects an encoded value whose pool is not in the list', () => {
    expect(decodeInstanceTypeValue('pool:999', pools)).toEqual({
      instanceType: undefined,
      poolId: undefined
    });
  });

  it('rejects a non-numeric or empty encoded id', () => {
    expect(decodeInstanceTypeValue('pool:abc', pools)).toEqual({
      instanceType: undefined,
      poolId: undefined
    });
    expect(decodeInstanceTypeValue('pool:', pools)).toEqual({
      instanceType: undefined,
      poolId: undefined
    });
  });

  it('rejects nonsense input rather than throwing', () => {
    expect(decodeInstanceTypeValue(undefined, pools)).toEqual({
      instanceType: undefined,
      poolId: undefined
    });
    expect(decodeInstanceTypeValue(null, pools)).toEqual({
      instanceType: undefined,
      poolId: undefined
    });
    expect(decodeInstanceTypeValue('', pools)).toEqual({
      instanceType: undefined,
      poolId: undefined
    });
    expect(decodeInstanceTypeValue(42, pools)).toEqual({
      instanceType: undefined,
      poolId: undefined
    });
  });
});

describe('getCapacityBlockOptions', () => {
  const instanceTypes = [
    {name: 'm5.4xlarge', instanceFamily: 'General purpose'},
    {name: 'm5.large', instanceFamily: 'General purpose'}
  ];

  it('keeps a pool whose region and instance type both match', () => {
    const pool = {id: 1, instanceType: 'M5.4XLarge', regionId: 1, name: 'pool-1'};

    expect(getCapacityBlockOptions([pool], instanceTypes, {regionId: 1})).toEqual([
      {pool, instanceType: instanceTypes[0]}
    ]);
  });

  it('drops a pool outside the requested region', () => {
    const pool = {id: 1, instanceType: 'm5.4xlarge', regionId: 2, name: 'pool-1'};

    expect(getCapacityBlockOptions([pool], instanceTypes, {regionId: 1})).toEqual([]);
  });

  it('drops a pool whose instance type is not allowed', () => {
    const pool = {id: 1, instanceType: 'm5.8xlarge', regionId: 1, name: 'pool-1'};

    expect(getCapacityBlockOptions([pool], instanceTypes, {regionId: 1})).toEqual([]);
  });

  it('keeps two pools backing the same instance type, one option each', () => {
    const poolA = {id: 5, instanceType: 'm5.large', regionId: 1, name: 'pool-a'};
    const poolB = {id: 2, instanceType: 'm5.large', regionId: 1, name: 'pool-b'};

    expect(getCapacityBlockOptions([poolA, poolB], instanceTypes, {regionId: 1})).toEqual([
      {pool: poolB, instanceType: instanceTypes[1]},
      {pool: poolA, instanceType: instanceTypes[1]}
    ]);
  });

  it('orders by the lowest pool id so the dropdown does not reshuffle', () => {
    const higherId = {id: 9, instanceType: 'm5.large', regionId: 1, name: 'pool-9'};
    const lowerId = {id: 3, instanceType: 'm5.4xlarge', regionId: 1, name: 'pool-3'};

    const result = getCapacityBlockOptions([higherId, lowerId], instanceTypes, {regionId: 1});

    expect(result.map((o) => o.pool.id)).toEqual([3, 9]);
  });

  it('does not filter by region when none is given', () => {
    const pool = {id: 1, instanceType: 'm5.large', regionId: 1, name: 'pool-1'};

    expect(getCapacityBlockOptions([pool], instanceTypes)).toEqual([
      {pool, instanceType: instanceTypes[1]}
    ]);
  });

  it('returns an empty array for a missing or empty pool list', () => {
    expect(getCapacityBlockOptions([], instanceTypes)).toEqual([]);
    expect(getCapacityBlockOptions(undefined, instanceTypes)).toEqual([]);
  });

  it('returns an empty array for a missing or empty instance type list', () => {
    const pool = {id: 1, instanceType: 'm5.large', regionId: 1, name: 'pool-1'};

    expect(getCapacityBlockOptions([pool], [])).toEqual([]);
    expect(getCapacityBlockOptions([pool], undefined)).toEqual([]);
  });
});

describe('correctCapacityBlockValue', () => {
  const instanceTypes = [
    {name: 'm5.4xlarge', instanceFamily: 'General purpose'},
    {name: 'm5.large', instanceFamily: 'General purpose'}
  ];
  const pool = {id: 351, instanceType: 'm5.4xlarge', regionId: 1, name: 'pool-351'};

  it('returns undefined for a plain value - the caller decides its own fallback', () => {
    expect(correctCapacityBlockValue('m5.large', [pool], instanceTypes, {regionId: 1}))
      .toBeUndefined();
  });

  it('returns undefined for nonsense that does not name a pool either', () => {
    expect(correctCapacityBlockValue('not-a-value', [pool], instanceTypes, {regionId: 1}))
      .toBeUndefined();
  });

  it('keeps an encoded value unchanged while its pool is still offered', () => {
    const value = encodeCapacityBlockValue(pool);

    expect(correctCapacityBlockValue(value, [pool], instanceTypes, {regionId: 1})).toBe(value);
  });

  it('falls back to the plain instance type once a region switch drops the pool - ' +
    'never back to the pool: form', () => {
    const value = encodeCapacityBlockValue(pool);

    const corrected = correctCapacityBlockValue(value, [pool], instanceTypes, {regionId: 2});

    expect(corrected).toBe('m5.4xlarge');
    expect(corrected).not.toMatch(/^pool:/);
  });

  it('clears the field when the region switch drops the pool and its instance ' +
    'type is not allowed either', () => {
    const narrowedInstanceTypes = [{name: 'm5.large', instanceFamily: 'General purpose'}];
    const value = encodeCapacityBlockValue(pool);

    expect(correctCapacityBlockValue(value, [pool], narrowedInstanceTypes, {regionId: 2}))
      .toBeNull();
  });

  it('defers to the caller\'s own fallback when the pool it names is not in the ' +
    'list at all - decodeInstanceTypeValue already could not resolve it either', () => {
    expect(correctCapacityBlockValue('pool:999', [pool], instanceTypes, {regionId: 1}))
      .toBeUndefined();
  });
});
