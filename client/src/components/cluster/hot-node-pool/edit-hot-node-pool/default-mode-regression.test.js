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

import React from 'react';
import {observable} from 'mobx';
import moment from 'moment-timezone';
import {
  renderWithStores,
  screen,
  within,
  click,
  change,
  waitFor,
  flushPromises,
  fn,
  stubApi
} from '@test';
import EditHotNodePool from './index';
import {getValidationErrors, buildPoolPayload, buildSchedulePayload} from './helpers';
import {COMMITMENT_DATE_TIME_FORMAT} from './constants';

// Now-relative fixtures: see helpers.test.js for why a fixed past date no longer works.
const inDays = (days) => moment().add(days, 'days').format(COMMITMENT_DATE_TIME_FORMAT);

const loaded = (value) => observable({loaded: true, pending: false, value});

const regionsList = [
  {id: 1, name: 'eu-central-1', provider: 'aws'},
  {id: 2, name: 'westeurope', provider: 'azure'}
];

function awsRegionsStore (regionsValue = regionsList) {
  return observable({
    loaded: true,
    pending: false,
    value: regionsValue,
    getRegion: (id) => regionsValue.find(r => r.id === id),
    getRegionByUID: (id) => regionsValue.find(r => r.regionId === id)
  });
}

const instanceAllowedPayload = {
  'cluster.allowed.instance.types': [
    {sku: 's1', name: 'm5.xlarge', instanceFamily: 'General purpose'}
  ],
  'cluster.allowed.price.types': ['SPOT', 'ON_DEMAND']
};

function stores () {
  return {
    awsRegions: awsRegionsStore(),
    allInstanceTypes: loaded([]),
    dockerRegistries: observable({
      loaded: true,
      value: {registries: []},
      fetchIfNeededOrWait: () => Promise.resolve()
    }),
    roles: loaded([]),
    users: loaded([{userName: 'USER1'}]),
    usersInfo: loaded([]),
    pipelines: loaded([]),
    allConfigurations: loaded([])
  };
}

function fullPool (overrides = {}) {
  return {
    instanceDisk: 50,
    instanceType: 'm5.xlarge',
    dockerImages: ['registry.com/group/image:1'],
    schedule: {
      scheduleEntries: [
        {from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'}
      ]
    },
    count: 2,
    priceType: 'SPOT',
    instanceImage: 'ami-1',
    name: 'my-pool',
    regionId: 1,
    filter: undefined,
    autoscaled: false,
    minSize: undefined,
    maxSize: undefined,
    scaleDownThreshold: undefined,
    scaleUpThreshold: undefined,
    scaleStep: undefined,
    kubeLabels: {
      env: {value: 'prod', monitored: true}
    },
    ...overrides
  };
}

// Same shape as `helpers.test.js`'s own `baseValues()` plus the four new client-only
// fields, so `getValidationErrors`/`buildPoolPayload` see a values object exactly like
// the one `EditHotNodePool`'s state produces.
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
    mode: 'DEFAULT',
    poolType: 'STANDARD',
    commitmentStart: undefined,
    commitmentEnd: undefined,
    commitmentDuration: undefined,
    ...overrides
  };
}

// Mounting always fires an AllowedInstanceTypes request (prevRegion starts at -1),
// including in create mode with no region yet — this waits for it to settle before the
// test's assertions and cleanup run, so the pending `.then()` cannot resolve after
// `stubApi()` has been torn down and leak a "Network access in a unit test" rejection
// into a later test. A single `handle()` for the whole test, rather than `reply()` per
// case: `stubApi` matches the first-registered handler for a pattern, so a second
// `reply()` for the same regex would be shadowed and never reached.
async function renderPool (props = {}, instanceTypesForCall = () => instanceAllowedPayload) {
  const api = stubApi();
  api.handle(/\/cluster\/instance\/allowed/, (call) => ({
    status: 'OK',
    payload: instanceTypesForCall(call)
  }));
  const result = renderWithStores(
    <EditHotNodePool visible disabled={false} currentUserName="USER1" {...props} />,
    stores()
  );
  await waitFor(() => expect(api.calls.length).toBeGreaterThan(0));
  await flushPromises();
  return {...result, api};
}

function row (labelText) {
  return screen.getByText(labelText).closest('div');
}

describe('payload shape is closed (Default mode)', () => {
  it('buildPoolPayload exposes exactly the 16 pre-existing backend keys', () => {
    const payload = buildPoolPayload(baseValues({
      mode: 'DEFAULT',
      poolType: 'SHARABLE_NODE',
      commitmentStart: '2026-01-01 10:00:00.000',
      commitmentDuration: 3600
    }));
    expect(Object.keys(payload).sort()).toEqual([
      'autoscaled',
      'count',
      'dockerImages',
      'filter',
      'instanceDisk',
      'instanceImage',
      'instanceType',
      'kubeLabels',
      'maxSize',
      'minSize',
      'name',
      'priceType',
      'regionId',
      'scaleDownThreshold',
      'scaleStep',
      'scaleUpThreshold'
    ].sort());
  });

  it('buildPoolPayload adds exactly two keys in future-dated mode', () => {
    const payload = buildPoolPayload(baseValues({
      mode: 'FUTURE_DATED',
      poolType: 'SHARABLE_NODE',
      count: 1,
      commitmentStart: inDays(30),
      commitmentEnd: inDays(90),
      commitmentDuration: 1209600
    }));
    expect(Object.keys(payload).sort()).toEqual([
      'autoscaled',
      'capacityReservationRequest',
      'count',
      'dockerImages',
      'filter',
      'instanceDisk',
      'instanceImage',
      'instanceType',
      'kubeLabels',
      'maxSize',
      'minSize',
      'name',
      'poolType',
      'priceType',
      'regionId',
      'scaleDownThreshold',
      'scaleStep',
      'scaleUpThreshold'
    ].sort());
  });

  it(
    'the Default-mode payload shape is unchanged while future-dated mode adds exactly ' +
    'poolType and capacityReservationRequest',
    () => {
      const defaultPayload = buildPoolPayload(baseValues({mode: 'DEFAULT'}));
      const futureDatedPayload = buildPoolPayload(baseValues({
        mode: 'FUTURE_DATED',
        poolType: 'STANDARD',
        count: 1,
        commitmentStart: inDays(30),
        commitmentEnd: inDays(90),
        commitmentDuration: 1209600
      }));

      const sixteenKeys = [
        'autoscaled',
        'count',
        'dockerImages',
        'filter',
        'instanceDisk',
        'instanceImage',
        'instanceType',
        'kubeLabels',
        'maxSize',
        'minSize',
        'name',
        'priceType',
        'regionId',
        'scaleDownThreshold',
        'scaleStep',
        'scaleUpThreshold'
      ].sort();

      expect(Object.keys(defaultPayload).sort()).toEqual(sixteenKeys);
      expect(Object.keys(futureDatedPayload).sort()).toEqual(
        [...sixteenKeys, 'capacityReservationRequest', 'poolType'].sort()
      );
      expect(futureDatedPayload.capacityReservationRequest.reservationType).toBe('FUTURE_DATED');
      ['mode', 'commitmentStart', 'commitmentEnd', 'commitmentDuration'].forEach((key) => {
        expect(Object.keys(futureDatedPayload)).not.toContain(key);
      });
    }
  );

  it('buildSchedulePayload exposes exactly scheduleEntries and name', () => {
    const payload = buildSchedulePayload(baseValues({
      schedule: [{from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'}],
      mode: 'DEFAULT',
      poolType: 'SHARABLE_NODE',
      commitmentStart: '2026-01-01 10:00:00.000',
      commitmentDuration: 3600
    }));
    expect(Object.keys(payload).sort()).toEqual(['name', 'scheduleEntries'].sort());
  });
});

describe('validation key set and result are unchanged in Default mode', () => {
  it('a minimal valid Default-mode values object is valid with every error undefined', () => {
    const {valid, errors} = getValidationErrors(baseValues());
    expect(valid).toBe(true);
    Object.keys(errors).forEach((key) => {
      expect(errors[key]).toBeUndefined();
    });
  });

  it('the new commitment errors are undefined in Default mode, whatever garbage they hold', () => {
    const {valid, errors} = getValidationErrors(baseValues({
      mode: 'DEFAULT',
      commitmentStart: 'tomorrow',
      commitmentDuration: -5
    }));
    expect(errors.commitmentStart).toBeUndefined();
    expect(errors.commitmentDuration).toBeUndefined();
    expect(valid).toBe(true);
  });

  it('still reports "Name is required"', () => {
    const {errors} = getValidationErrors(baseValues({name: undefined}));
    expect(errors.name).toBe('Name is required');
  });

  it('still reports "Region is required"', () => {
    const {errors} = getValidationErrors(baseValues({region: undefined}));
    expect(errors.region).toBe('Region is required');
  });

  it('still reports "Instance type is required"', () => {
    const {errors} = getValidationErrors(baseValues({instanceType: undefined}));
    expect(errors.instanceType).toBe('Instance type is required');
  });

  it('still reports the disk range message verbatim', () => {
    const {errors} = getValidationErrors(baseValues({disk: 14}));
    expect(errors.disk).toBe('Disk should be a positive integer (15...15360)');
  });

  it('still reports the count message verbatim', () => {
    const {errors} = getValidationErrors(baseValues({count: -1}));
    expect(errors.count).toBe('Count should be a positive value or zero (pool is disabled)');
  });
});

describe('rendered Default-mode control set is unchanged', () => {
  it('shows exactly the pre-existing controls plus the new Mode: row', async () => {
    await renderPool();

    [
      'Mode:',
      'Pool name:',
      'Autoscaled:',
      'Nodes count:',
      'Region:',
      'Price type:',
      'Instance Type:',
      'AMI:',
      'Disk:',
      'Add schedule',
      'Condition:',
      'Filters:',
      'Schedule',
      'Configuration',
      'Filters'
    ].forEach((text) => {
      expect(screen.getByText(text)).toBeInTheDocument();
    });
    expect(screen.getByText('CREATE')).toBeInTheDocument();
    expect(screen.getByText('CANCEL')).toBeInTheDocument();

    [
      'Pool type:',
      'Starts on:',
      'Duration (sec):',
      'Ends on:'
    ].forEach((text) => {
      expect(screen.queryByText(text)).not.toBeInTheDocument();
    });
  });
});

describe('edit-mode round trip is byte-identical', () => {
  it('SAVE produces the exact pre-existing pool and schedule payloads', async () => {
    const onSave = fn();
    await renderPool({pool: fullPool(), onSave});

    await click(screen.getByText('SAVE'));

    expect(onSave).toHaveBeenCalledTimes(1);
    const [payload, schedulePayload] = onSave.mock.calls[0];
    expect(payload).toEqual({
      count: 2,
      instanceDisk: 50,
      dockerImages: ['registry.com/group/image:1'],
      instanceType: 'm5.xlarge',
      name: 'my-pool',
      priceType: 'SPOT',
      regionId: 1,
      instanceImage: 'ami-1',
      filter: {},
      autoscaled: false,
      minSize: undefined,
      maxSize: undefined,
      scaleDownThreshold: undefined,
      scaleUpThreshold: undefined,
      scaleStep: undefined,
      kubeLabels: {env: {value: 'prod', monitored: true}}
    });
    expect(schedulePayload).toEqual({
      scheduleEntries: [
        {from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'}
      ],
      name: 'my-pool'
    });
    expect(schedulePayload.scheduleEntries.every(e => !('id' in e))).toBe(true);
  });
});

describe('no new required field in Default mode', () => {
  it('CREATE succeeds once only the pre-existing required fields are filled', async () => {
    const onSave = fn();
    const {api} = await renderPool({onSave});

    await change(within(row('Pool name:')).getByRole('textbox'), 'default-pool');

    const callsBeforeRegion = api.calls
      .filter(c => /cluster\/instance\/allowed/.test(c.path)).length;
    await click(within(row('Region:')).getByRole('combobox'));
    await click(screen.getByText('eu-central-1'));
    await waitFor(() => {
      const callsAfterRegion = api.calls
        .filter(c => /cluster\/instance\/allowed/.test(c.path)).length;
      expect(callsAfterRegion).toBeGreaterThan(callsBeforeRegion);
    });
    await flushPromises();
    await waitFor(() => {
      const select = within(row('Instance Type:')).getByRole('combobox').closest('.ant-select');
      expect(select).not.toHaveClass('ant-select-disabled');
    });

    await click(within(row('Instance Type:')).getByRole('combobox'));
    await click(screen.getByText('m5.xlarge'));

    await change(within(row('Disk:')).getByRole('textbox'), '50');
    await change(within(row('Nodes count:')).getByRole('textbox'), '1');

    await click(screen.getByText('CREATE'));

    expect(onSave).toHaveBeenCalledTimes(1);
  });
});
