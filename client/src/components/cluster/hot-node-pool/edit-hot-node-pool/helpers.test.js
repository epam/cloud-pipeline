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

import moment from 'moment-timezone';
import {
  arraysAreEqual,
  getValidationErrors,
  isModified,
  buildPoolPayload,
  buildSchedulePayload,
  buildCapacityReservationRequest,
  buildRunOwnerFilter,
  getCapacityReservationStatus
} from './helpers';
import {
  COMMITMENT_DATE_TIME_FORMAT,
  RESERVATION_STATUS_PENDING,
  RESERVATION_STATUS_APPROVED
} from './constants';

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
    commitmentEnd: undefined,
    ...overrides
  };
}

// Now-relative fixtures: the backend's lead-time/duration rules are relative to "now",
// so a fixed past date (like the old `'2026-01-01 10:00:00.000'`) would fail them. `fmt`
// formats a moment through the backend's own wire format; `inDays` is "N days from now,
// in that format"; `futureDatedValues` is a fully valid future-dated values object a
// single field can be overridden from.
const fmt = (m) => m.format(COMMITMENT_DATE_TIME_FORMAT);
const inDays = (days) => fmt(moment().add(days, 'days'));
const futureDatedValues = (overrides = {}) => baseValues({
  mode: 'FUTURE_DATED',
  poolType: 'STANDARD',
  count: 1,
  commitmentStart: inDays(30),
  commitmentEnd: inDays(90),
  commitmentDurationDays: 14,
  ...overrides
});

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

  describe('future-dated commitment window', () => {
    it('is valid, every commitment error undefined, for a fully valid future-dated values object',
      () => {
        const {valid, errors} = getValidationErrors(futureDatedValues());
        expect(valid).toBe(true);
        expect(errors.commitmentStart).toBeUndefined();
        expect(errors.commitmentEnd).toBeUndefined();
        expect(errors.commitmentDurationDays).toBeUndefined();
      });

    it.each([
      ['missing', undefined],
      ['unparseable', 'tomorrow'],
      ['missing milliseconds', '2026-01-01 10:00:00']
    ])('requires a valid, strictly-formatted start date (%s)', (name, commitmentStart) => {
      const {errors} = getValidationErrors(futureDatedValues({commitmentStart}));
      expect(errors.commitmentStart).toBe('Search from date is required');
    });

    it('requires an end date', () => {
      const {errors} = getValidationErrors(futureDatedValues({commitmentEnd: undefined}));
      expect(errors.commitmentEnd).toBe('Search until date is required');
    });

    it('rejects a start date in the past', () => {
      const {errors} = getValidationErrors(futureDatedValues({commitmentStart: inDays(-1)}));
      expect(errors.commitmentStart).toBe('Search from date should be in the future');
    });

    it('rejects a start date with too little lead time', () => {
      const {errors} = getValidationErrors(futureDatedValues({commitmentStart: inDays(3)}));
      expect(errors.commitmentStart)
        .toBe('Search from date should be between 5 and 120 days from now');
    });

    it('rejects a start date with too much lead time', () => {
      const {errors} = getValidationErrors(futureDatedValues({
        commitmentStart: inDays(150),
        commitmentEnd: inDays(200)
      }));
      expect(errors.commitmentStart)
        .toBe('Search from date should be between 5 and 120 days from now');
    });

    it('rejects an end date that is not after the start date', () => {
      const {errors} = getValidationErrors(futureDatedValues({
        commitmentStart: inDays(30),
        commitmentEnd: inDays(10)
      }));
      expect(errors.commitmentEnd).toBe('Search until date should be after the search from date');
    });

    it.each([0, -5])('rejects a non-positive duration %i', (commitmentDurationDays) => {
      const {errors} = getValidationErrors(futureDatedValues({commitmentDurationDays}));
      expect(errors.commitmentDurationDays)
        .toBe('Duration should be a positive number of days');
    });

    it('rejects a non-integer duration', () => {
      const {errors} = getValidationErrors(futureDatedValues({commitmentDurationDays: 14.5}));
      expect(errors.commitmentDurationDays)
        .toBe('Duration should be a whole number of days');
    });

    it('rejects a duration shorter than the 14-day minimum', () => {
      const {errors} = getValidationErrors(futureDatedValues({commitmentDurationDays: 7}));
      expect(errors.commitmentDurationDays)
        .toBe('Duration should be at least 14 days');
    });

    it('accepts a duration given as a numeric string', () => {
      const {errors} = getValidationErrors(futureDatedValues({commitmentDurationDays: '14'}));
      expect(errors.commitmentDurationDays).toBeUndefined();
    });

    it('rejects a duration that does not fit between the search-from and search-until dates',
      () => {
        const {errors} = getValidationErrors(futureDatedValues({
          commitmentStart: inDays(30),
          commitmentEnd: inDays(40),
          commitmentDurationDays: 30
        }));
        expect(errors.commitmentDurationDays)
          .toBe('Duration should fit between "Search from" and "Search until"');
      });

    it('requires count to be at least 1 for a future-dated pool', () => {
      const {valid, errors} = getValidationErrors(futureDatedValues({count: 0}));
      expect(errors.count).toBe('Count should be at least 1 for a Future dated pool');
      expect(valid).toBe(false);
    });

    it('Default-mode regression: a count of 0 means a disabled pool, not an error', () => {
      const {errors} = getValidationErrors(baseValues({mode: 'DEFAULT', count: 0}));
      expect(errors.count).toBeUndefined();
    });

    it('Default-mode regression: commitment fields are never validated', () => {
      const {valid, errors} = getValidationErrors(baseValues({
        mode: 'DEFAULT',
        commitmentStart: 'tomorrow',
        commitmentEnd: 'whenever',
        commitmentDurationDays: -5
      }));
      expect(errors.commitmentStart).toBeUndefined();
      expect(errors.commitmentEnd).toBeUndefined();
      expect(errors.commitmentDurationDays).toBeUndefined();
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

describe('buildCapacityReservationRequest', () => {
  it('returns exactly the four backend-contract keys', () => {
    const request = buildCapacityReservationRequest({
      commitmentStart: inDays(30),
      commitmentEnd: inDays(90),
      commitmentDurationDays: 14
    });
    expect(Object.keys(request).sort()).toEqual([
      'commitmentDuration',
      'requestedEndDate',
      'requestedStartDate',
      'reservationType'
    ].sort());
  });

  it('always sends reservationType FUTURE_DATED', () => {
    const request = buildCapacityReservationRequest({
      commitmentStart: inDays(30),
      commitmentEnd: inDays(90),
      commitmentDurationDays: 14
    });
    expect(request.reservationType).toBe('FUTURE_DATED');
  });

  it('converts the 14 days to exactly 1209600 seconds, the backend minimum', () => {
    const request = buildCapacityReservationRequest({
      commitmentStart: inDays(30),
      commitmentEnd: inDays(90),
      commitmentDurationDays: 14
    });
    expect(request.commitmentDuration).toBe(1209600);
  });

  it('converts commitmentDurationDays to a number even when given as a string', () => {
    const request = buildCapacityReservationRequest({
      commitmentStart: inDays(30),
      commitmentEnd: inDays(90),
      commitmentDurationDays: '14'
    });
    expect(typeof request.commitmentDuration).toBe('number');
    expect(request.commitmentDuration).toBe(1209600);
  });

  it('formats requestedStartDate/requestedEndDate in the backend wire format', () => {
    const request = buildCapacityReservationRequest({
      commitmentStart: inDays(30),
      commitmentEnd: inDays(90),
      commitmentDurationDays: 14
    });
    const pattern = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$/;
    expect(request.requestedStartDate).toMatch(pattern);
    expect(request.requestedEndDate).toMatch(pattern);
    expect(request.requestedStartDate).not.toContain('T');
    expect(request.requestedStartDate).not.toContain('Z');
    expect(request.requestedEndDate).not.toContain('T');
    expect(request.requestedEndDate).not.toContain('Z');
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

  it.each(['FUTURE_DATED', 'DEFAULT'])('never includes mode: it has no backend slot (%s)',
    (mode) => {
      const payload = buildPoolPayload(values({mode}));
      expect(Object.keys(payload)).not.toContain('mode');
    });

  describe('future-dated mode', () => {
    function futureDatedPayloadValues (overrides = {}) {
      return values({
        mode: 'FUTURE_DATED',
        poolType: 'STANDARD',
        commitmentStart: inDays(30),
        commitmentEnd: inDays(90),
        commitmentDurationDays: 14,
        ...overrides
      });
    }

    it('sends poolType as given', () => {
      expect(buildPoolPayload(futureDatedPayloadValues()).poolType).toBe('STANDARD');
      expect(buildPoolPayload(futureDatedPayloadValues({poolType: 'SHARABLE_NODE'})).poolType)
        .toBe('SHARABLE_NODE');
    });

    it('sends capacityReservationRequest matching buildCapacityReservationRequest', () => {
      const input = futureDatedPayloadValues();
      const payload = buildPoolPayload(input);
      expect(payload.capacityReservationRequest).toEqual(buildCapacityReservationRequest(input));
    });

    it('its capacityReservationRequest.commitmentDuration is in seconds (14 days = 1209600)',
      () => {
        const payload = buildPoolPayload(futureDatedPayloadValues());
        expect(payload.capacityReservationRequest.commitmentDuration).toBe(14 * 86400);
        expect(payload.capacityReservationRequest.commitmentDuration).toBe(1209600);
      });

    it('never includes commitmentStart/commitmentEnd/commitmentDurationDays as top-level keys',
      () => {
        const payload = buildPoolPayload(futureDatedPayloadValues());
        expect(Object.keys(payload)).not.toContain('commitmentStart');
        expect(Object.keys(payload)).not.toContain('commitmentEnd');
        expect(Object.keys(payload)).not.toContain('commitmentDurationDays');
      });
  });

  it('Default mode sends poolType too (STANDARD by default, SHARABLE_NODE when chosen), ' +
    'but no capacityReservationRequest even with every commitment field populated', () => {
    const standardPayload = buildPoolPayload(values({mode: 'DEFAULT', poolType: 'STANDARD'}));
    expect(standardPayload.poolType).toBe('STANDARD');
    expect(Object.keys(standardPayload)).not.toContain('capacityReservationRequest');

    const shareablePayload = buildPoolPayload(values({
      mode: 'DEFAULT',
      poolType: 'SHARABLE_NODE',
      commitmentStart: inDays(30),
      commitmentEnd: inDays(90),
      commitmentDurationDays: 14
    }));
    expect(shareablePayload.poolType).toBe('SHARABLE_NODE');
    expect(Object.keys(shareablePayload)).not.toContain('capacityReservationRequest');
  });
});

describe('buildRunOwnerFilter', () => {
  it('builds a single AND-ed RUN_OWNER/EQUAL criterion for the given user', () => {
    expect(buildRunOwnerFilter('USER1')).toEqual({
      operator: 'AND',
      filters: [{type: 'RUN_OWNER', operator: 'EQUAL', value: 'USER1'}]
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

  // Pins the future-dated-mode regression at the helper level, since driving the real
  // DatePickers through the UI to reach a savable future-dated state is impractical
  // under antd 2.13.14 (see index.test.js's "no schedule request" test for the matching
  // UI-level, weaker check).
  it('is undefined for a future-dated pool: nothing in the future-dated UI can add a schedule' +
    ' entry', () => {
    expect(buildSchedulePayload(futureDatedValues())).toBeUndefined();
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

describe('getCapacityReservationStatus', () => {
  it('is undefined for a non-reservation pool', () => {
    expect(getCapacityReservationStatus({
      isCapacityReservation: false,
      startDate: '2026-03-01 10:00:00.000',
      endDate: '2026-04-01 10:00:00.000'
    })).toBeUndefined();
  });

  it('is undefined for an absent pool', () => {
    expect(getCapacityReservationStatus(undefined)).toBeUndefined();
  });

  it('is pending for a reservation pool with no granted window yet', () => {
    expect(getCapacityReservationStatus({
      isCapacityReservation: true,
      startDate: undefined,
      endDate: undefined
    })).toBe(RESERVATION_STATUS_PENDING);
  });

  it.each([
    ['startDate', 'endDate'],
    ['endDate', 'startDate']
  ])('is pending when only %s is granted', (present, missing) => {
    expect(getCapacityReservationStatus({
      isCapacityReservation: true,
      [present]: '2026-03-01 10:00:00.000',
      [missing]: undefined
    })).toBe(RESERVATION_STATUS_PENDING);
  });

  it('is approved for a reservation pool with a granted window', () => {
    expect(getCapacityReservationStatus({
      isCapacityReservation: true,
      startDate: '2026-03-01 10:00:00.000',
      endDate: '2026-04-01 10:00:00.000'
    })).toBe(RESERVATION_STATUS_APPROVED);
  });
});
