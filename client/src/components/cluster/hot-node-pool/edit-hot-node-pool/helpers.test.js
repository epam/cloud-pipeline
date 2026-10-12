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
  arraysAreEqual,
  getValidationErrors,
  isModified,
  buildPoolPayload,
  buildSchedulePayload
} from './helpers';

const validSchedule = {from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'};
const invalidSchedule = {from: 'MONDAY', fromTime: '09:00:00', to: undefined, toTime: undefined};

function baseValues (overrides = {}) {
  return {
    disk: 50,
    instanceType: 'm5.xlarge',
    dockerImages: [{id: 1, image: 'library/image:1', removed: false}],
    schedule: [],
    count: 1,
    name: 'pool',
    region: 1,
    filters: undefined,
    autoscaled: false,
    minSize: undefined,
    maxSize: undefined,
    scaleDownThreshold: undefined,
    scaleUpThreshold: undefined,
    scaleStep: undefined,
    labels: [],
    ...overrides
  };
}

describe('getValidationErrors', () => {
  it('reports no errors for a fully valid, non-autoscaled pool', () => {
    const {valid, errors} = getValidationErrors(baseValues());
    expect(valid).toBe(true);
    expect(errors.disk).toBeUndefined();
    expect(errors.instanceType).toBeUndefined();
    expect(errors.name).toBeUndefined();
    expect(errors.region).toBeUndefined();
    expect(errors.count).toBeUndefined();
  });

  it.each([14, 15361])('rejects disk size %i as out of range', (disk) => {
    const {valid, errors} = getValidationErrors(baseValues({disk}));
    expect(valid).toBe(false);
    expect(errors.disk).toEqual(expect.stringContaining('positive integer'));
  });

  it.each([15, 15360])('accepts disk size %i at the boundary', (disk) => {
    const {errors} = getValidationErrors(baseValues({disk}));
    expect(errors.disk).toBeUndefined();
  });

  it('requires an instance type', () => {
    const {errors} = getValidationErrors(baseValues({instanceType: undefined}));
    expect(errors.instanceType).toBe('Instance type is required');
  });

  it('rejects duplicate (non-removed) docker images', () => {
    const {errors} = getValidationErrors(baseValues({
      dockerImages: [
        {id: 1, image: 'library/image:1', removed: false},
        {id: 2, image: 'library/image:1', removed: false}
      ]
    }));
    expect(errors.dockerImages).toBe('Duplicates are not allowed');
  });

  it('ignores a removed duplicate', () => {
    const {errors} = getValidationErrors(baseValues({
      dockerImages: [
        {id: 1, image: 'library/image:1', removed: false},
        {id: 2, image: 'library/image:1', removed: true}
      ]
    }));
    expect(errors.dockerImages).toBeUndefined();
  });

  it('rejects an invalid schedule entry', () => {
    const {errors} = getValidationErrors(baseValues({schedule: [invalidSchedule]}));
    expect(errors.schedule).toBe('Invalid schedule');
  });

  it('accepts a fully specified schedule entry', () => {
    const {errors} = getValidationErrors(baseValues({schedule: [validSchedule]}));
    expect(errors.schedule).toBeUndefined();
  });

  it('requires a name', () => {
    const {errors} = getValidationErrors(baseValues({name: undefined}));
    expect(errors.name).toBe('Name is required');
  });

  it('requires a region', () => {
    const {errors} = getValidationErrors(baseValues({region: undefined}));
    expect(errors.region).toBe('Region is required');
  });

  it('passes filters through getFiltersError', () => {
    const {errors} = getValidationErrors(baseValues({
      filters: {operator: undefined, filters: [{operator: 'EQUAL', type: 'x', value: '1'}]}
    }));
    expect(errors.filters).toBe('You must specify a condition');
  });

  it('rejects a negative count when not autoscaled', () => {
    const {errors} = getValidationErrors(baseValues({count: -1}));
    expect(errors.count).toEqual(expect.stringContaining('positive value or zero'));
  });

  it('accepts a zero count when not autoscaled (pool disabled)', () => {
    const {errors} = getValidationErrors(baseValues({count: 0}));
    expect(errors.count).toBeUndefined();
  });

  it('never checks count while autoscaled, even if it is negative', () => {
    const {errors} = getValidationErrors(baseValues({
      autoscaled: true,
      count: -1,
      minSize: 1,
      maxSize: 2,
      scaleDownThreshold: 20,
      scaleUpThreshold: 80,
      scaleStep: 1
    }));
    expect(errors.count).toBeUndefined();
  });

  describe('autoscaled branch', () => {
    function autoscaledValues (overrides = {}) {
      return baseValues({
        autoscaled: true,
        minSize: 1,
        maxSize: 2,
        scaleDownThreshold: 20,
        scaleUpThreshold: 80,
        scaleStep: 1,
        ...overrides
      });
    }

    it('requires minSize >= 1', () => {
      const {errors} = getValidationErrors(autoscaledValues({minSize: 0}));
      expect(errors.minSize).toEqual(expect.stringContaining('larger than or equal 1'));
    });

    it('requires maxSize >= 1', () => {
      const {errors} = getValidationErrors(autoscaledValues({maxSize: 0}));
      expect(errors.maxSize).toEqual(expect.stringContaining('larger than or equal 1'));
    });

    it('puts the same message on both fields when minSize > maxSize', () => {
      const {errors} = getValidationErrors(autoscaledValues({minSize: 5, maxSize: 2}));
      expect(errors.minSize).toBe('Min Size should not be greater than Max Size');
      expect(errors.maxSize).toBe('Min Size should not be greater than Max Size');
    });

    it('rejects a scaleUpThreshold outside 0..100', () => {
      const {errors} = getValidationErrors(autoscaledValues({scaleUpThreshold: 101}));
      expect(errors.scaleUpThreshold).toEqual(expect.stringContaining('0..100%'));
    });

    it('rejects a scaleDownThreshold outside 0..100', () => {
      const {errors} = getValidationErrors(autoscaledValues({scaleDownThreshold: -1}));
      expect(errors.scaleDownThreshold).toEqual(expect.stringContaining('0..100%'));
    });

    it('puts the same message on both thresholds when scaleDown >= scaleUp', () => {
      const {errors} = getValidationErrors(autoscaledValues({
        scaleDownThreshold: 80,
        scaleUpThreshold: 80
      }));
      expect(errors.scaleDownThreshold).toEqual(expect.stringContaining('larger than Scale Down'));
      expect(errors.scaleUpThreshold).toEqual(expect.stringContaining('larger than Scale Down'));
    });

    it('requires scaleStep >= 1', () => {
      const {errors} = getValidationErrors(autoscaledValues({scaleStep: 0}));
      expect(errors.scaleStep).toEqual(expect.stringContaining('larger than or equal 1'));
    });

    it('is valid at the boundaries', () => {
      const {valid} = getValidationErrors(autoscaledValues());
      expect(valid).toBe(true);
    });
  });

  describe('labels', () => {
    it('flags a label with an empty name as invalid', () => {
      const {errors} = getValidationErrors(baseValues({
        labels: [{key: 'a', name: '', value: 'v', monitored: false}]
      }));
      expect(errors.labelsError).toBe('There are invalid node labels');
    });

    it('flags duplicate label names, preferring the invalid-name message first', () => {
      const {errors} = getValidationErrors(baseValues({
        labels: [
          {key: 'a', name: 'env', value: '1', monitored: false},
          {key: 'b', name: 'env', value: '2', monitored: false}
        ]
      }));
      expect(errors.labelsError).toBe('There are duplicate node labels');
    });

    it('is case-insensitive and trims when detecting duplicate names', () => {
      const {errors} = getValidationErrors(baseValues({
        labels: [
          {key: 'a', name: ' Env ', value: '1', monitored: false},
          {key: 'b', name: 'env', value: '2', monitored: false}
        ]
      }));
      expect(errors.labelsError).toBe('There are duplicate node labels');
    });

    it('has no label error for distinct, non-empty names', () => {
      const {errors, valid} = getValidationErrors(baseValues({
        labels: [
          {key: 'a', name: 'env', value: '1', monitored: false},
          {key: 'b', name: 'tier', value: '2', monitored: false}
        ]
      }));
      expect(errors.labelsError).toBeUndefined();
      expect(valid).toBe(true);
    });

    it('fails overall validity when there is a label error', () => {
      const {valid} = getValidationErrors(baseValues({
        labels: [{key: 'a', name: '', value: 'v', monitored: false}]
      }));
      expect(valid).toBe(false);
    });
  });
});

describe('isModified', () => {
  function unmodified (overrides = {}) {
    return {
      disk: 50,
      initialDisk: 50,
      instanceType: 'm5.xlarge',
      initialInstanceType: 'm5.xlarge',
      dockerImages: [{id: 1, image: 'library/image:1', removed: false}],
      initialDockerImages: ['library/image:1'],
      schedule: [],
      initialSchedule: [],
      count: 1,
      initialCount: 1,
      spot: 'SPOT',
      initialSpot: 'SPOT',
      instanceImage: undefined,
      initialInstanceImage: undefined,
      name: 'pool',
      initialName: 'pool',
      region: 1,
      initialRegion: 1,
      filters: {},
      initialFilters: {},
      autoscaled: false,
      initialAutoScaled: false,
      minSize: 1,
      initialMinSize: 999,
      maxSize: 2,
      initialMaxSize: 999,
      scaleDownThreshold: 20,
      initialScaleDownThreshold: 999,
      scaleUpThreshold: 80,
      initialScaleUpThreshold: 999,
      scaleStep: 1,
      initialScaleStep: 999,
      ...overrides
    };
  }

  it('is false when nothing changed', () => {
    expect(isModified(unmodified())).toBe(false);
  });

  it.each([
    ['disk', 51],
    ['count', 2],
    ['instanceType', 'm5.2xlarge'],
    ['spot', 'ON_DEMAND'],
    ['instanceImage', 'ami-1'],
    ['name', 'renamed'],
    ['region', 2]
  ])('is true when %s changes', (field, value) => {
    expect(isModified(unmodified({[field]: value}))).toBe(true);
  });

  it('ignores minSize/maxSize/thresholds/scaleStep while not autoscaled', () => {
    // unmodified() already sets these to differ from their initial* counterparts
    // while autoscaled stays false — this is the quirk under test.
    expect(isModified(unmodified())).toBe(false);
  });

  it('considers minSize/maxSize/thresholds/scaleStep once autoscaled', () => {
    expect(isModified(unmodified({autoscaled: true, initialAutoScaled: true}))).toBe(true);
  });

  it('detects a docker image added or removed', () => {
    expect(isModified(unmodified({
      dockerImages: [
        {id: 1, image: 'library/image:1', removed: false},
        {id: 2, image: 'library/image:2', removed: false}
      ]
    }))).toBe(true);
  });

  it('ignores a removed docker image that was never saved as initial', () => {
    expect(isModified(unmodified({
      dockerImages: [
        {id: 1, image: 'library/image:1', removed: false},
        {id: 2, image: 'library/image:2', removed: true}
      ]
    }))).toBe(false);
  });

  it('detects a schedule entry change', () => {
    expect(isModified(unmodified({schedule: [validSchedule]}))).toBe(true);
  });

  it('detects a filters change', () => {
    expect(isModified(unmodified({
      filters: {operator: 'AND', filters: [{operator: 'EQUAL', type: 'x', value: '1'}]}
    }))).toBe(true);
  });
});

describe('buildPoolPayload', () => {
  function values (overrides = {}) {
    return {
      count: 3,
      disk: 50,
      dockerImages: [
        {id: 1, image: 'library/image:1', removed: false},
        {id: 2, image: 'library/image:2', removed: true}
      ],
      instanceType: 'm5.xlarge',
      name: 'pool',
      spot: 'SPOT',
      region: 1,
      instanceImage: 'ami-1',
      filters: {operator: 'AND', filters: []},
      autoscaled: false,
      minSize: 2,
      maxSize: 5,
      scaleDownThreshold: 20,
      scaleUpThreshold: 80,
      scaleStep: 2,
      labels: [
        {key: 'a', name: 'env', value: 'prod', monitored: true},
        {key: 'b', name: 'tier', value: 'web', monitored: false}
      ],
      ...overrides
    };
  }

  it('uses count and omits every autoscaling field when not autoscaled', () => {
    const payload = buildPoolPayload(values());
    expect(payload.count).toBe(3);
    expect(payload.minSize).toBeUndefined();
    expect(payload.maxSize).toBeUndefined();
    expect(payload.scaleDownThreshold).toBeUndefined();
    expect(payload.scaleUpThreshold).toBeUndefined();
    expect(payload.scaleStep).toBeUndefined();
  });

  it('uses minSize as count and includes every autoscaling field when autoscaled', () => {
    const payload = buildPoolPayload(values({autoscaled: true}));
    expect(payload.count).toBe(2);
    expect(payload.minSize).toBe(2);
    expect(payload.maxSize).toBe(5);
    expect(payload.scaleDownThreshold).toBe(20);
    expect(payload.scaleUpThreshold).toBe(80);
    expect(payload.scaleStep).toBe(2);
  });

  it('renames disk/region/spot to the API field names', () => {
    const payload = buildPoolPayload(values());
    expect(payload.instanceDisk).toBe(50);
    expect(payload.regionId).toBe(1);
    expect(payload.priceType).toBe('SPOT');
    expect(payload.filter).toEqual({operator: 'AND', filters: []});
  });

  it('flattens non-removed docker images to a plain string array', () => {
    const payload = buildPoolPayload(values());
    expect(payload.dockerImages).toEqual(['library/image:1']);
  });

  it('builds kubeLabels keyed by label name', () => {
    const payload = buildPoolPayload(values());
    expect(payload.kubeLabels).toEqual({
      env: {value: 'prod', monitored: true},
      tier: {value: 'web', monitored: false}
    });
  });
});

describe('buildSchedulePayload', () => {
  it('is undefined when there is no schedule', () => {
    expect(buildSchedulePayload({schedule: [], name: 'pool'})).toBeUndefined();
  });

  it('carries the pool name and strips the id from every entry', () => {
    const payload = buildSchedulePayload({
      schedule: [{...validSchedule, id: 0}, {...validSchedule, id: 1}],
      name: 'pool'
    });
    expect(payload.name).toBe('pool');
    expect(payload.scheduleEntries).toEqual([validSchedule, validSchedule]);
    expect(payload.scheduleEntries.every(e => !('id' in e))).toBe(true);
  });
});

describe('arraysAreEqual', () => {
  it('treats absent and empty as equal', () => {
    expect(arraysAreEqual(undefined, undefined)).toBe(true);
    expect(arraysAreEqual([], [])).toBe(true);
  });

  it('treats one side absent as different', () => {
    expect(arraysAreEqual(undefined, [])).toBe(false);
    expect(arraysAreEqual(['a'], undefined)).toBe(false);
  });

  it('ignores order and duplicates (set comparison)', () => {
    expect(arraysAreEqual(['a', 'b'], ['b', 'a'])).toBe(true);
    expect(arraysAreEqual(['a', 'a'], ['a'])).toBe(true);
  });

  it('detects differing membership', () => {
    expect(arraysAreEqual(['a', 'b'], ['a', 'c'])).toBe(false);
  });
});
