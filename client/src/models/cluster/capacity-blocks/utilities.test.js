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
  findCapacityBlockPool,
  getCapacityBlockAdditionalParameterNames
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
