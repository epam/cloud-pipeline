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
import {useFakeTimers, useRealTimers, setSystemTime} from '@test';
import {
  arraysAreEqual,
  getValidationErrors,
  isModified,
  buildPoolPayload,
  buildSchedulePayload,
  buildCapacityReservationRequest,
  buildRunOwnerFilter,
  getCapacityReservationStatus,
  getReservationDisplayInstanceCount,
  humanizeReservationStatus,
  needsForceToDeletePool,
  getCompleteMlBlockCandidates,
  buildCapacityBlockOfferingRequest,
  canSearchCapacityBlockOfferings,
  getOfferingsForCandidate,
  getSelectedOffering,
  formatCapacityBlockInstanceType,
  formatCloudCommitmentDate
} from './helpers';
import {
  COMMITMENT_DATE_TIME_FORMAT,
  COMMITMENT_DATE_ONLY_FORMAT,
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
// formats a moment through the backend's own wire format, used for an offering's own
// `startDate`/`endDate` (AWS-origin, always a full datetime); `inDays` is "N days from
// now" as the day-only string `commitmentStart`/`commitmentEnd` hold; `futureDatedValues`
// is a fully valid future-dated values object a single field can be overridden from.
const fmt = (m) => m.format(COMMITMENT_DATE_TIME_FORMAT);
const inDays = (days) => moment().add(days, 'days').format(COMMITMENT_DATE_ONLY_FORMAT);
const futureDatedValues = (overrides = {}) => baseValues({
  mode: 'FUTURE_DATED',
  poolType: 'STANDARD',
  count: 1,
  commitmentStart: inDays(30),
  commitmentEnd: inDays(90),
  commitmentDurationDays: 14,
  ...overrides
});

const sampleOffering = (overrides = {}) => ({
  offeringId: 'offering-1',
  resourceType: 'INSTANCE',
  instanceType: 'p5.48xlarge',
  availabilityZone: 'us-east-1a',
  instanceCount: 4,
  startDate: fmt(moment().add(31, 'days')),
  endDate: fmt(moment().add(32, 'days')),
  capacityDurationHours: 24,
  upfrontFeeAmount: 1234.5,
  upfrontFeeCurrency: 'USD',
  tenancy: 'default',
  ...overrides
});

// A fully valid ML Block values object has a selected offering, and the instanceType/
// count it filled onto the form.
const mlBlockValues = (overrides = {}) => {
  const offering = sampleOffering();
  return baseValues({
    mode: 'ML_BLOCK',
    poolType: 'STANDARD',
    instanceType: offering.instanceType,
    count: offering.instanceCount,
    commitmentStart: inDays(1),
    commitmentEnd: inDays(10),
    commitmentDurationDays: 1,
    mlBlockCandidates: [{id: 1, instanceType: offering.instanceType, instanceCount: 4}],
    mlBlockOfferings: [offering],
    mlBlockOfferingId: offering.offeringId,
    ...overrides
  });
};

// The boundaries below are pinned to this fixed instant rather than to whenever the
// suite happens to run, so a day offset lands exactly on the fencepost every time,
// independent of the harness's own pinned zone (`moment.utc` is explicit either way).
const FIXED_NOW = '2026-11-20T15:00:00.000Z';
const dayAt = (offsetDays) => moment.utc(FIXED_NOW)
  .add(offsetDays, 'days')
  .format(COMMITMENT_DATE_ONLY_FORMAT);

describe('getCompleteMlBlockCandidates', () => {
  it('keeps a row with a type and a whole count of at least 1', () => {
    const candidates = [{id: 1, instanceType: 'p5.48xlarge', instanceCount: 4}];
    expect(getCompleteMlBlockCandidates(candidates)).toEqual(candidates);
  });

  it.each([
    ['missing type', {id: 1, instanceType: undefined, instanceCount: 4}],
    ['missing count', {id: 1, instanceType: 'p5.48xlarge', instanceCount: undefined}],
    ['zero count', {id: 1, instanceType: 'p5.48xlarge', instanceCount: 0}],
    ['fractional count', {id: 1, instanceType: 'p5.48xlarge', instanceCount: 1.5}]
  ])('drops an incomplete row (%s)', (name, candidate) => {
    expect(getCompleteMlBlockCandidates([candidate])).toEqual([]);
  });

  it('accepts a count given as a numeric string', () => {
    const candidates = [{id: 1, instanceType: 'p5.48xlarge', instanceCount: '4'}];
    expect(getCompleteMlBlockCandidates(candidates)).toEqual(candidates);
  });

  it('is an empty array for undefined or empty input', () => {
    expect(getCompleteMlBlockCandidates(undefined)).toEqual([]);
    expect(getCompleteMlBlockCandidates([])).toEqual([]);
  });
});

describe('formatCloudCommitmentDate', () => {
  afterEach(() => {
    // The harness pins moment-timezone's default to UTC; restore it so this suite's
    // other tests, which rely on that pin, are unaffected by this one.
    moment.tz.setDefault('UTC');
  });

  test('formats a UTC wall time unchanged while the viewer\'s zone is UTC', () => {
    expect(formatCloudCommitmentDate('2026-12-25 11:30:00.000', 'YYYY-MM-DD HH:mm'))
      .toBe('2026-12-25 11:30');
  });

  test('converts a UTC wall time to the viewer\'s local zone, not the UTC one', () => {
    moment.tz.setDefault('Asia/Tokyo');
    expect(formatCloudCommitmentDate('2026-12-25 11:30:00.000', 'YYYY-MM-DD HH:mm'))
      .toBe('2026-12-25 20:30');
  });

  test.each([undefined, null, ''])('is undefined for %p', (value) => {
    expect(formatCloudCommitmentDate(value, 'YYYY-MM-DD HH:mm')).toBeUndefined();
  });

  test('is undefined for an unparseable value', () => {
    expect(formatCloudCommitmentDate('not-a-date', 'YYYY-MM-DD HH:mm')).toBeUndefined();
  });
});

describe('buildCapacityBlockOfferingRequest', () => {
  function values (overrides = {}) {
    return {
      region: 2,
      commitmentStart: inDays(1),
      commitmentEnd: inDays(10),
      commitmentDurationDays: 1,
      mlBlockCandidates: [{id: 1, instanceType: 'p5.48xlarge', instanceCount: 4}],
      ...overrides
    };
  }

  it('sends regionId as a number and the fixed resourceType', () => {
    const request = buildCapacityBlockOfferingRequest(values({region: '2'}));
    expect(request.regionId).toBe(2);
    expect(request.resourceType).toBe('INSTANCE');
  });

  it('sends only the complete candidates, with instanceCount as a number', () => {
    const request = buildCapacityBlockOfferingRequest(values({
      mlBlockCandidates: [
        {id: 1, instanceType: 'p5.48xlarge', instanceCount: '4'},
        {id: 2, instanceType: 'p4d.24xlarge', instanceCount: undefined}
      ]
    }));
    expect(request.candidates).toEqual([{instanceType: 'p5.48xlarge', instanceCount: 4}]);
  });

  it('converts the duration in days to hours', () => {
    const request = buildCapacityBlockOfferingRequest(values({commitmentDurationDays: 3}));
    expect(request.capacityDurationHours).toBe(72);
  });

  it('sends the chosen start day at its start of day and the end day at its end of day',
    () => {
      const request = buildCapacityBlockOfferingRequest(values({
        commitmentStart: '2026-11-26',
        commitmentEnd: '2026-11-30'
      }));
      expect(request.startDateRange).toBe('2026-11-26 00:00:00.000');
      expect(request.endDateRange).toBe('2026-11-30 23:59:59.999');
    });

  it('omits endDateRange when commitmentEnd is empty', () => {
    const request = buildCapacityBlockOfferingRequest(values({commitmentEnd: undefined}));
    expect(Object.keys(request)).not.toContain('endDateRange');
  });

  it('omits endDateRange when commitmentEnd does not parse', () => {
    const request = buildCapacityBlockOfferingRequest(values({commitmentEnd: 'not-a-date'}));
    expect(Object.keys(request)).not.toContain('endDateRange');
  });
});

describe('canSearchCapacityBlockOfferings', () => {
  function values (overrides = {}) {
    return {
      region: 2,
      commitmentStart: inDays(1),
      commitmentDurationDays: 1,
      mlBlockCandidates: [{id: 1, instanceType: 'p5.48xlarge', instanceCount: 4}],
      ...overrides
    };
  }

  it('is true for a region, a valid start date, a sufficient duration and a ' +
    'complete candidate', () => {
    expect(canSearchCapacityBlockOfferings(values())).toBe(true);
  });

  it('is false without a region', () => {
    expect(canSearchCapacityBlockOfferings(values({region: undefined}))).toBe(false);
  });

  it.each([undefined, 'not-a-date'])('is false with an invalid commitmentStart (%s)', (start) => {
    expect(canSearchCapacityBlockOfferings(values({commitmentStart: start}))).toBe(false);
  });

  it.each([0, 0.5])('is false when the duration is below the 1-day floor (%s)', (days) => {
    expect(canSearchCapacityBlockOfferings(values({commitmentDurationDays: days}))).toBe(false);
  });

  it('is false with no complete candidate', () => {
    expect(canSearchCapacityBlockOfferings(values({mlBlockCandidates: []}))).toBe(false);
  });
});

describe('getOfferingsForCandidate', () => {
  const offerings = [
    sampleOffering({offeringId: 'a', instanceType: 'p5.48xlarge'}),
    sampleOffering({offeringId: 'b', instanceType: 'P5.48XLARGE'}),
    sampleOffering({offeringId: 'c', instanceType: 'p4d.24xlarge'})
  ];

  it('matches an offering\'s instanceType to the candidate\'s, case-insensitively', () => {
    const matched = getOfferingsForCandidate(offerings, {instanceType: 'p5.48xlarge'});
    expect(matched.map(o => o.offeringId).sort()).toEqual(['a', 'b']);
  });

  it('is an empty array when nothing matches', () => {
    expect(getOfferingsForCandidate(offerings, {instanceType: 'm5.xlarge'})).toEqual([]);
  });

  it('is an empty array for a candidate with no instanceType', () => {
    expect(getOfferingsForCandidate(offerings, {instanceType: undefined})).toEqual([]);
  });

  it('is an empty array when there are no offerings to search', () => {
    expect(getOfferingsForCandidate(undefined, {instanceType: 'p5.48xlarge'})).toEqual([]);
  });
});

describe('getSelectedOffering', () => {
  const offering = sampleOffering();

  it('is undefined when nothing is selected', () => {
    expect(getSelectedOffering({mlBlockOfferings: [offering], mlBlockOfferingId: undefined}))
      .toBeUndefined();
  });

  it('is undefined when the selected id is no longer in the list', () => {
    expect(getSelectedOffering({mlBlockOfferings: [], mlBlockOfferingId: offering.offeringId}))
      .toBeUndefined();
  });

  it('returns the offering matching the selected id', () => {
    expect(getSelectedOffering({
      mlBlockOfferings: [offering],
      mlBlockOfferingId: offering.offeringId
    })).toEqual(offering);
  });
});

describe('formatCapacityBlockInstanceType', () => {
  it('renders the type, GPU, vCPU and memory (in GB) when every field is present', () => {
    expect(formatCapacityBlockInstanceType({
      instanceType: 'p5.48xlarge',
      vCPU: 192,
      memoryInMiB: 2097152,
      gpuCount: 8,
      gpuDeviceName: 'H100'
    })).toBe('p5.48xlarge, 8 x H100, 192 vCPU, 2048 GB');
  });

  it('keeps a fractional memory value instead of rounding it off', () => {
    expect(formatCapacityBlockInstanceType({
      instanceType: 'p5.48xlarge',
      memoryInMiB: 1180160
    })).toBe('p5.48xlarge, 1152.5 GB');
  });

  it('omits the GPU device name but keeps the count when the name is missing', () => {
    expect(formatCapacityBlockInstanceType({
      instanceType: 'p5.48xlarge',
      gpuCount: 8
    })).toBe('p5.48xlarge, 8 GPU');
  });

  it('leaves out GPU, vCPU and memory entirely when the entry carries none of them', () => {
    expect(formatCapacityBlockInstanceType({instanceType: 'm5.xlarge'})).toBe('m5.xlarge');
  });

  it('never prints "undefined" for a missing field', () => {
    const formatted = formatCapacityBlockInstanceType({instanceType: 'm5.xlarge', vCPU: 4});
    expect(formatted).not.toContain('undefined');
  });

  it('does not throw for an absent entry', () => {
    expect(formatCapacityBlockInstanceType(undefined)).toBe('');
  });
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
      ['a full datetime, not a day', '2026-01-01 10:00:00.000']
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
        .toBe('Search from date should be between 6 and 121 days from now');
    });

    it('rejects a start date with too much lead time', () => {
      const {errors} = getValidationErrors(futureDatedValues({
        commitmentStart: inDays(150),
        commitmentEnd: inDays(200)
      }));
      expect(errors.commitmentStart)
        .toBe('Search from date should be between 6 and 121 days from now');
    });

    // `now` is fixed rather than relative, so a day offset lands exactly on the
    // fencepost every run - `leadDays` floors a fractional diff against a start pinned
    // to 00:00, so the boundary a user hits is one calendar day later than the backend's
    // own 5/120 on both ends (`NodePoolValidator.MINIMUM_LEAD_DAYS`/120).
    describe('the lead-time boundary, pinned to a fixed "now"', () => {
      afterEach(() => {
        useRealTimers();
      });

      it('refuses the day exactly 5 calendar days out and accepts the day 6 days out', () => {
        useFakeTimers();
        setSystemTime(new Date(FIXED_NOW));
        const refused = getValidationErrors(futureDatedValues({
          commitmentStart: dayAt(5),
          commitmentEnd: dayAt(40),
          commitmentDurationDays: 14
        }));
        expect(refused.errors.commitmentStart)
          .toBe('Search from date should be between 6 and 121 days from now');

        const accepted = getValidationErrors(futureDatedValues({
          commitmentStart: dayAt(6),
          commitmentEnd: dayAt(40),
          commitmentDurationDays: 14
        }));
        expect(accepted.errors.commitmentStart).toBeUndefined();
      });

      it('accepts the day 121 calendar days out and refuses the day 122 days out', () => {
        useFakeTimers();
        setSystemTime(new Date(FIXED_NOW));
        const accepted = getValidationErrors(futureDatedValues({
          commitmentStart: dayAt(121),
          commitmentEnd: dayAt(140),
          commitmentDurationDays: 14
        }));
        expect(accepted.errors.commitmentStart).toBeUndefined();

        const refused = getValidationErrors(futureDatedValues({
          commitmentStart: dayAt(122),
          commitmentEnd: dayAt(140),
          commitmentDurationDays: 14
        }));
        expect(refused.errors.commitmentStart)
          .toBe('Search from date should be between 6 and 121 days from now');
      });
    });

    // `commitmentEnd` is sent at 23:59:59.999 of its day, so an N-day duration needs a
    // span of N calendar days *plus one* - `endOf('day')` is deliberate (the plan this
    // phase closes), so this pins the fencepost rather than changing it.
    describe('the exact-fit duration boundary', () => {
      afterEach(() => {
        useRealTimers();
      });

      it('refuses a duration that exactly spans the window, and accepts one day more', () => {
        useFakeTimers();
        setSystemTime(new Date(FIXED_NOW));
        const exact = getValidationErrors(futureDatedValues({
          commitmentStart: dayAt(30),
          commitmentEnd: dayAt(43),
          commitmentDurationDays: 14
        }));
        expect(exact.errors.commitmentDurationDays)
          .toBe('Duration should fit between "Search from" and "Search until"');

        const fits = getValidationErrors(futureDatedValues({
          commitmentStart: dayAt(30),
          commitmentEnd: dayAt(44),
          commitmentDurationDays: 14
        }));
        expect(fits.errors.commitmentDurationDays).toBeUndefined();
      });
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

    it('requires count to be at least 1 when creating a future-dated pool', () => {
      const {valid, errors} = getValidationErrors(
        futureDatedValues({count: 0, isExistingReservation: false})
      );
      expect(errors.count).toBe('Count should be at least 1 for a Future dated pool');
      expect(valid).toBe(false);
    });

    it(
      'does not block an existing future-dated pool whose count legitimately loads as 0',
      () => {
        // `NodePoolManager.create` stores a reservation pool with count 0 until the
        // reservation is activated, so SAVE must not be blocked by it on an existing
        // pool - the Count input is disabled and round-trips the value unchanged.
        const {valid, errors} = getValidationErrors(
          futureDatedValues({count: 0, isExistingReservation: true})
        );
        expect(errors.count).toBeUndefined();
        expect(valid).toBe(true);
      }
    );

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

  describe('ML Block commitment window, candidates and offering', () => {
    it('is valid, every ML Block error undefined, for a fully valid ML Block values object',
      () => {
        const {valid, errors} = getValidationErrors(mlBlockValues());
        expect(valid).toBe(true);
        expect(errors.commitmentStart).toBeUndefined();
        expect(errors.commitmentEnd).toBeUndefined();
        expect(errors.commitmentDurationDays).toBeUndefined();
        expect(errors.mlBlockCandidates).toBeUndefined();
        expect(errors.mlBlockOffering).toBeUndefined();
        expect(errors.count).toBeUndefined();
        expect(errors.instanceType).toBeUndefined();
      });

    it('requires a start date', () => {
      const {errors} = getValidationErrors(mlBlockValues({commitmentStart: undefined}));
      expect(errors.commitmentStart).toBe('Search from date is required');
    });

    it('has no lead-time rule: one day out is enough, unlike future-dated\'s 5-day floor', () => {
      const {errors} = getValidationErrors(mlBlockValues({
        commitmentStart: inDays(1),
        commitmentEnd: inDays(2)
      }));
      expect(errors.commitmentStart).toBeUndefined();
    });

    it('rejects a start date in the past', () => {
      const {errors} = getValidationErrors(mlBlockValues({commitmentStart: inDays(-1)}));
      expect(errors.commitmentStart).toBe('Search from date should be in the future');
    });

    // The payload sends 00:00 of the picked day, so today's own start is already past -
    // a direct consequence of picking by day, with no lead-time rule to mask it here.
    it('rejects today as a start date: its start of day is already past', () => {
      const {errors} = getValidationErrors(mlBlockValues({commitmentStart: inDays(0)}));
      expect(errors.commitmentStart).toBe('Search from date should be in the future');
    });

    // The wire string is read by the backend as UTC (`AwsCapacityReservationService`
    // reads a zone-less `LocalDateTime` with `ZoneOffset.UTC`), so the same picked day
    // must flip from valid to refused at its own UTC midnight - not at local midnight,
    // which is the regression this phase closes (picking "tomorrow" west of UTC could
    // already be past by the time the backend reads it).
    describe('a chosen day flips from valid to refused at its own UTC midnight', () => {
      afterEach(() => {
        useRealTimers();
      });

      it('is valid a minute before that midnight and refused a second after it', () => {
        useFakeTimers();
        setSystemTime(new Date('2026-11-25T23:59:00.000Z'));
        const beforeMidnight = getValidationErrors(mlBlockValues({commitmentStart: '2026-11-26'}));
        expect(beforeMidnight.errors.commitmentStart).toBeUndefined();

        setSystemTime(new Date('2026-11-26T00:00:01.000Z'));
        const afterMidnight = getValidationErrors(mlBlockValues({commitmentStart: '2026-11-26'}));
        expect(afterMidnight.errors.commitmentStart)
          .toBe('Search from date should be in the future');
      });
    });

    // The search never needs an end of window, and the purchase carries the offering's
    // own - requiring it here only blocked CREATE on a field nothing downstream reads.
    it('Search until is optional: the form is valid and the search omits endDateRange', () => {
      const values = mlBlockValues({commitmentEnd: undefined});
      const {valid, errors} = getValidationErrors(values);
      expect(errors.commitmentEnd).toBeUndefined();
      expect(valid).toBe(true);
      const request = buildCapacityBlockOfferingRequest(values);
      expect(Object.keys(request)).not.toContain('endDateRange');
    });

    it('when given, still requires the end date to be after the start date', () => {
      const {errors} = getValidationErrors(mlBlockValues({
        commitmentStart: inDays(10),
        commitmentEnd: inDays(5)
      }));
      expect(errors.commitmentEnd)
        .toBe('Search until date should be after the search from date');
    });

    it('accepts the 1-day duration floor', () => {
      expect(getValidationErrors(mlBlockValues({commitmentDurationDays: 1}))
        .errors.commitmentDurationDays).toBeUndefined();
    });

    it('rejects a non-positive duration', () => {
      expect(getValidationErrors(mlBlockValues({commitmentDurationDays: 0}))
        .errors.commitmentDurationDays).toBe('Duration should be a positive number of days');
    });

    it('rejects a fractional duration', () => {
      expect(getValidationErrors(mlBlockValues({commitmentDurationDays: 0.5}))
        .errors.commitmentDurationDays).toBe('Duration should be a whole number of days');
    });

    it('rejects a duration that does not fit between the search-from and search-until dates',
      () => {
        const {errors} = getValidationErrors(mlBlockValues({
          commitmentStart: inDays(1),
          commitmentEnd: inDays(2),
          commitmentDurationDays: 5
        }));
        expect(errors.commitmentDurationDays)
          .toBe('Duration should fit between "Search from" and "Search until"');
      });

    it('requires at least one complete candidate', () => {
      const {errors, valid} = getValidationErrors(mlBlockValues({mlBlockCandidates: []}));
      expect(errors.mlBlockCandidates).toBe('Add at least one instance type');
      expect(valid).toBe(false);
    });

    it('flags a row with a type but no usable count', () => {
      const {errors} = getValidationErrors(mlBlockValues({
        mlBlockCandidates: [{id: 1, instanceType: 'p5.48xlarge', instanceCount: undefined}]
      }));
      expect(errors.mlBlockCandidates).toBe('Specify the nodes count for every instance type');
    });

    it('is invalid with candidates but no offering selected', () => {
      const {errors, valid} = getValidationErrors(mlBlockValues({
        instanceType: undefined,
        count: undefined,
        mlBlockOfferingId: undefined
      }));
      expect(errors.mlBlockOffering).toBe('Select one of the offerings');
      expect(valid).toBe(false);
    });

    it('is invalid when the selected offering id is no longer in the list', () => {
      const {errors, valid} = getValidationErrors(mlBlockValues({mlBlockOfferings: []}));
      expect(errors.mlBlockOffering).toBe('Select one of the offerings');
      expect(valid).toBe(false);
    });

    it('skips every ML Block check on an existing reservation pool, count 0 included', () => {
      // Mirrors a purchased block not yet activated - the backend stores it with
      // count 0, same as an existing future-dated pool.
      const {valid, errors} = getValidationErrors(mlBlockValues({
        isExistingReservation: true,
        commitmentStart: undefined,
        commitmentEnd: undefined,
        commitmentDurationDays: undefined,
        mlBlockCandidates: [],
        mlBlockOfferings: [],
        mlBlockOfferingId: undefined,
        instanceType: 'p5.48xlarge',
        count: 0
      }));
      expect(errors.commitmentStart).toBeUndefined();
      expect(errors.commitmentEnd).toBeUndefined();
      expect(errors.commitmentDurationDays).toBeUndefined();
      expect(errors.mlBlockCandidates).toBeUndefined();
      expect(errors.mlBlockOffering).toBeUndefined();
      expect(errors.count).toBeUndefined();
      expect(valid).toBe(true);
    });

    it('Future-dated-mode regression: an ML Block-only field never errors in that mode', () => {
      const {errors, valid} = getValidationErrors(futureDatedValues());
      expect(errors.mlBlockCandidates).toBeUndefined();
      expect(errors.mlBlockOffering).toBeUndefined();
      expect(valid).toBe(true);
    });

    it('Default-mode regression: ML Block fields are never validated', () => {
      const {errors, valid} = getValidationErrors(baseValues({mode: 'DEFAULT'}));
      expect(errors.mlBlockCandidates).toBeUndefined();
      expect(errors.mlBlockOffering).toBeUndefined();
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

  it('sends the chosen start day at its start of day and the end day at its end of day',
    () => {
      const request = buildCapacityReservationRequest({
        commitmentStart: '2026-11-26',
        commitmentEnd: '2026-11-30',
        commitmentDurationDays: 14
      });
      expect(request.requestedStartDate).toBe('2026-11-26 00:00:00.000');
      expect(request.requestedEndDate).toBe('2026-11-30 23:59:59.999');
    });

  describe('ML Block mode', () => {
    it('returns the CAPACITY_BLOCK shape, from the selected offering', () => {
      const offering = sampleOffering();
      const request = buildCapacityReservationRequest({
        mode: 'ML_BLOCK',
        mlBlockOfferings: [offering],
        mlBlockOfferingId: offering.offeringId
      });
      expect(request).toEqual({
        reservationType: 'CAPACITY_BLOCK',
        capacityBlockOfferingId: offering.offeringId,
        availabilityZone: offering.availabilityZone,
        requestedStartDate: offering.startDate,
        requestedEndDate: offering.endDate,
        commitmentDuration: offering.capacityDurationHours * 3600
      });
    });

    it('carries the offering\'s own window, not the search window', () => {
      const offering = sampleOffering({
        startDate: fmt(moment().add(45, 'days')),
        endDate: fmt(moment().add(46, 'days'))
      });
      const request = buildCapacityReservationRequest({
        mode: 'ML_BLOCK',
        // A search window far from the offering's own granted window.
        commitmentStart: inDays(1),
        commitmentEnd: inDays(10),
        mlBlockOfferings: [offering],
        mlBlockOfferingId: offering.offeringId
      });
      expect(request.requestedStartDate).toBe(offering.startDate);
      expect(request.requestedEndDate).toBe(offering.endDate);
    });
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

  it.each(['FUTURE_DATED', 'DEFAULT', 'ML_BLOCK'])(
    'never includes mode: it has no backend slot (%s)',
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
      expect(buildPoolPayload(futureDatedPayloadValues({poolType: 'SHAREABLE_NODE'})).poolType)
        .toBe('SHAREABLE_NODE');
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

  describe('ML Block mode', () => {
    function mlBlockPayloadValues (overrides = {}) {
      const offering = sampleOffering();
      return values({
        mode: 'ML_BLOCK',
        poolType: 'STANDARD',
        instanceType: offering.instanceType,
        count: offering.instanceCount,
        mlBlockOfferings: [offering],
        mlBlockOfferingId: offering.offeringId,
        ...overrides
      });
    }

    it('sends the CAPACITY_BLOCK capacityReservationRequest shape built from the offering', () => {
      const offering = sampleOffering();
      const payload = buildPoolPayload(mlBlockPayloadValues({
        mlBlockOfferings: [offering],
        mlBlockOfferingId: offering.offeringId
      }));
      expect(payload.capacityReservationRequest).toEqual({
        reservationType: 'CAPACITY_BLOCK',
        capacityBlockOfferingId: offering.offeringId,
        availabilityZone: offering.availabilityZone,
        requestedStartDate: offering.startDate,
        requestedEndDate: offering.endDate,
        commitmentDuration: offering.capacityDurationHours * 3600
      });
    });

    it('its capacityReservationRequest carries the offering\'s window, not the search one',
      () => {
        const offering = sampleOffering({
          startDate: fmt(moment().add(45, 'days')),
          endDate: fmt(moment().add(46, 'days'))
        });
        const payload = buildPoolPayload(mlBlockPayloadValues({
          commitmentStart: inDays(1),
          commitmentEnd: inDays(10),
          mlBlockOfferings: [offering],
          mlBlockOfferingId: offering.offeringId
        }));
        expect(payload.capacityReservationRequest.requestedStartDate).toBe(offering.startDate);
        expect(payload.capacityReservationRequest.requestedEndDate).toBe(offering.endDate);
      });

    it('sends no capacityReservationRequest on an existing reservation pool', () => {
      const payload = buildPoolPayload(mlBlockPayloadValues({isExistingReservation: true}));
      expect(Object.keys(payload)).not.toContain('capacityReservationRequest');
    });
  });

  it('Default mode sends poolType too (STANDARD by default, SHAREABLE_NODE when chosen), ' +
    'but no capacityReservationRequest even with every commitment field populated', () => {
    const standardPayload = buildPoolPayload(values({mode: 'DEFAULT', poolType: 'STANDARD'}));
    expect(standardPayload.poolType).toBe('STANDARD');
    expect(Object.keys(standardPayload)).not.toContain('capacityReservationRequest');

    const shareablePayload = buildPoolPayload(values({
      mode: 'DEFAULT',
      poolType: 'SHAREABLE_NODE',
      commitmentStart: inDays(30),
      commitmentEnd: inDays(90),
      commitmentDurationDays: 14
    }));
    expect(shareablePayload.poolType).toBe('SHAREABLE_NODE');
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

  it.each([
    'REQUIRED_APPROVE',
    'APPROVED',
    'ASSESSING_BY_CLOUD_PROVIDER',
    'SCHEDULED',
    'ACTIVE',
    'FINALIZING',
    'FINISHED',
    'PURCHASE_FAILED',
    'FAILED',
    'CANCELLED'
  ])('returns the reservation record\'s own status verbatim (%s)', (status) => {
    expect(getCapacityReservationStatus(
      {isCapacityReservation: true},
      {status}
    )).toBe(status);
  });

  it('returns an unrecognised reservation status verbatim too - the record is the source ' +
    'of truth, not a value this helper validates', () => {
    expect(getCapacityReservationStatus(
      {isCapacityReservation: true},
      {status: 'SOME_NEW_STATUS'}
    )).toBe('SOME_NEW_STATUS');
  });

  it('falls back to pool field inference when reservation is undefined', () => {
    expect(getCapacityReservationStatus(
      {
        isCapacityReservation: true,
        startDate: '2026-03-01 10:00:00.000',
        endDate: '2026-04-01 10:00:00.000'
      },
      undefined
    )).toBe(RESERVATION_STATUS_APPROVED);
  });

  it('non-reservation pool returns undefined regardless of reservation arg', () => {
    expect(getCapacityReservationStatus(
      {isCapacityReservation: false},
      {status: 'SCHEDULED'}
    )).toBeUndefined();
  });
});

describe('humanizeReservationStatus', () => {
  it('humanises a multi-word status', () => {
    expect(humanizeReservationStatus('ASSESSING_BY_CLOUD_PROVIDER'))
      .toBe('Assessing by cloud provider');
  });

  it('humanises a single-word status', () => {
    expect(humanizeReservationStatus('CANCELLED')).toBe('Cancelled');
  });

  it('returns an empty string unchanged rather than throwing', () => {
    expect(humanizeReservationStatus('')).toBe('');
  });
});

describe('getReservationDisplayInstanceCount', () => {
  it.each([
    'REQUIRED_APPROVE',
    'APPROVED',
    'ASSESSING_BY_CLOUD_PROVIDER',
    'SCHEDULED',
    'ACTIVE',
    'FINALIZING'
  ])('returns the instance count while the reservation is still live (%s)', (status) => {
    expect(getReservationDisplayInstanceCount({status, instanceCount: 4})).toBe(4);
  });

  it.each([
    'PURCHASE_FAILED',
    'FINISHED',
    'FAILED',
    'CANCELLED'
  ])('is undefined once the reservation reaches a terminal status (%s)', (status) => {
    expect(getReservationDisplayInstanceCount({status, instanceCount: 4})).toBeUndefined();
  });

  it('is undefined when there is no reservation record', () => {
    expect(getReservationDisplayInstanceCount(undefined)).toBeUndefined();
  });

  it.each([null, undefined])(
    'is undefined when the record has no usable instanceCount (%s)',
    (instanceCount) => {
      expect(getReservationDisplayInstanceCount({status: 'ACTIVE', instanceCount}))
        .toBeUndefined();
    }
  );
});

describe('needsForceToDeletePool', () => {
  const futureDatedPool = {isCapacityReservation: true};

  it('is false for a Default pool, whatever its reservation says', () => {
    expect(needsForceToDeletePool(
      {isCapacityReservation: false},
      {status: 'ACTIVE'}
    )).toBe(false);
  });

  it('is false when there is no reservation record to give up', () => {
    expect(needsForceToDeletePool(futureDatedPool, undefined)).toBe(false);
  });

  it.each([
    'PURCHASE_FAILED',
    'FINISHED',
    'FAILED',
    'CANCELLED',
    'REQUIRED_APPROVE'
  ])('is false once the reservation reaches a safe-to-delete status (%s)', (status) => {
    expect(needsForceToDeletePool(futureDatedPool, {status})).toBe(false);
  });

  it.each([
    'APPROVED',
    'ASSESSING_BY_CLOUD_PROVIDER',
    'SCHEDULED',
    'ACTIVE',
    'FINALIZING'
  ])('is true while the reservation still holds, or may hold, capacity (%s)', (status) => {
    expect(needsForceToDeletePool(futureDatedPool, {status})).toBe(true);
  });
});
