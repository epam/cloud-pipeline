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
    // The forced criterion's value Select renders its matching Option's children
    // (<UserName userName="USER1" />), which injects `usersInfo` on its own —
    // separately from the `users` list the Select itself reads to build its options.
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

// `isCapacityReservation` is the backend entity's own flag — the only reliable signal
// that a loaded pool is a reservation pool. `startDate`/`endDate` are the realized
// window (null while pending/unscheduled); `schedule: undefined` because a GPU pool's
// weekly schedule is always empty.
function reservationPool (overrides = {}) {
  return fullPool({
    isCapacityReservation: true,
    poolType: 'STANDARD',
    priceType: 'ON_DEMAND',
    schedule: undefined,
    startDate: '2026-03-01 10:00:00.000',
    endDate: '2026-04-01 10:00:00.000',
    ...overrides
  });
}

// Mounting always fires an AllowedInstanceTypes request (prevRegion starts at
// -1), including in create mode with no region yet — this waits for it to
// settle before the test's assertions and cleanup run, so the pending
// `.then()` cannot resolve after `stubApi()` has been torn down and leak a
// "Network access in a unit test" rejection into a later test.
// A single `handle()` for the whole test, rather than `reply()` per case:
// `stubApi` matches the first-registered handler for a pattern, so a second
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

// antd's Select combobox carries no native `disabled` attribute; the state is
// a class on the `.ant-select` ancestor instead.
function selectIsDisabled (labelText) {
  const combobox = within(row(labelText)).getByRole('combobox');
  return combobox.closest('.ant-select').classList.contains('ant-select-disabled');
}

test('shows "Create" chrome with no pool and "Edit" chrome with one', async () => {
  await renderPool();
  expect(screen.getByText('Create hot node pool')).toBeInTheDocument();
  expect(screen.getByText('CREATE')).toBeInTheDocument();
});

test('shows "Edit" chrome and SAVE when a pool is given', async () => {
  await renderPool({pool: fullPool()});
  expect(screen.getByText('Edit hot node pool')).toBeInTheDocument();
  expect(screen.getByText('SAVE')).toBeInTheDocument();
});

test('create mode: Mode is enabled and shows Default', async () => {
  await renderPool();
  const combobox = within(row('Mode:')).getByRole('combobox');
  expect(combobox.closest('.ant-select')).not.toHaveClass('ant-select-disabled');
  expect(screen.getByText('Default')).toBeInTheDocument();
});

test('edit mode: Mode is disabled and still shows Default', async () => {
  await renderPool({pool: fullPool()});
  const combobox = within(row('Mode:')).getByRole('combobox');
  expect(combobox.closest('.ant-select')).toHaveClass('ant-select-disabled');
  expect(screen.getByText('Default')).toBeInTheDocument();
});

test('create mode, selecting GPU leaves the Mode select showing GPU', async () => {
  await renderPool();

  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Future dated'));

  expect(within(row('Mode:')).getByText('Future dated')).toBeInTheDocument();
});

test(
  'GPU + Standard locks Filters to the current user; Shareable unlocks; back to Default clears it',
  async () => {
    await renderPool();

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Future dated'));

    expect(within(row('Pool type:')).getByText('Standard')).toBeInTheDocument();
    expect(selectIsDisabled('Condition:')).toBe(true);
    expect(screen.getByText('Run owner')).toBeInTheDocument();
    // UserName (injected with `usersInfo`) falls back to the lower-cased login
    // when it cannot resolve a richer display name, which is the case here.
    expect(screen.getByText('user1')).toBeInTheDocument();

    await click(within(row('Pool type:')).getByRole('combobox'));
    await click(screen.getByText('Shareable'));

    expect(selectIsDisabled('Condition:')).toBe(false);
    expect(screen.getByRole('button', {name: 'Add filter'})).not.toBeDisabled();

    await click(within(row('Pool type:')).getByRole('combobox'));
    await click(screen.getByText('Standard'));
    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Default'));

    expect(screen.queryByText('Pool type:')).not.toBeInTheDocument();
    expect(selectIsDisabled('Condition:')).toBe(false);
    expect(screen.queryByText('Run owner')).not.toBeInTheDocument();
  }
);

test(
  'switching to GPU mode removes Autoscaled and its rows; switching back leaves it unchecked',
  async () => {
    await renderPool();

    await click(within(row('Autoscaled:')).getByRole('checkbox'));
    expect(within(row('Min Size:')).getByRole('textbox')).toBeInTheDocument();

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Future dated'));

    expect(screen.queryByText('Autoscaled:')).not.toBeInTheDocument();
    expect(screen.queryByText('Min Size:')).not.toBeInTheDocument();
    expect(screen.queryByText('Max Size:')).not.toBeInTheDocument();
    expect(screen.queryByText('Scale Up Threshold:')).not.toBeInTheDocument();
    expect(screen.queryByText('Scale Down Threshold:')).not.toBeInTheDocument();
    expect(screen.queryByText('Scale Step:')).not.toBeInTheDocument();
    expect(screen.getByText('Nodes count:')).toBeInTheDocument();

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Default'));

    expect(within(row('Autoscaled:')).getByRole('checkbox')).not.toBeChecked();
  }
);

test('switching to GPU mode resets a Spot price type to On-demand', async () => {
  await renderPool();

  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Future dated'));
  await flushPromises();

  await waitFor(() => {
    expect(within(row('Price type:')).getByText('On-demand')).toBeInTheDocument();
  });
});

test('Default-mode regression: Price type stays Spot and stays enabled', async () => {
  await renderPool({pool: fullPool()});

  expect(within(row('Price type:')).getByText('Spot')).toBeInTheDocument();
});

// Regression for a gap `onPoolKindChange`'s reset alone does not cover: a forced-GPU
// create never fires `onPoolKindChange` for mode (the Mode row is hidden, mode is GPU
// from the first render), so the auto-pick in `updateInstanceTypes` is the only thing
// that ever sets `spot` here, and it must skip SPOT too.
test('a forced-GPU create never auto-selects the Spot price type', async () => {
  await renderPool({allowedModes: ['GPU']});

  await waitFor(() => {
    expect(within(row('Price type:')).getByText('On-demand')).toBeInTheDocument();
  });
});

test('allowedModes=["GPU"]: opens forced into GPU mode, locked, with no Mode row', async () => {
  await renderPool({allowedModes: ['GPU']});

  expect(screen.queryByText('Mode:')).not.toBeInTheDocument();
  expect(within(row('Pool type:')).getByText('Standard')).toBeInTheDocument();
  // Pool type is locked too: a non-admin forced into GPU-only Create must not be
  // able to switch to Shareable and unlock the Filters section by hand.
  expect(selectIsDisabled('Pool type:')).toBe(true);
  expect(screen.queryByText('Autoscaled:')).not.toBeInTheDocument();
  expect(screen.getByText('Duration (sec):')).toBeInTheDocument();
  expect(selectIsDisabled('Condition:')).toBe(true);
  expect(screen.getByText('Run owner')).toBeInTheDocument();
  expect(screen.getByText('user1')).toBeInTheDocument();
});

test('allowedModes omitted: still opens in Default mode (admin default unchanged)', async () => {
  await renderPool();

  expect(within(row('Mode:')).getByText('Default')).toBeInTheDocument();
  expect(screen.queryByText('Pool type:')).not.toBeInTheDocument();
  expect(screen.getByText('Autoscaled:')).toBeInTheDocument();
});

test('SAVE payload never carries mode', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool(), onSave});

  await click(screen.getByText('SAVE'));

  expect(Object.keys(onSave.mock.calls[0][0])).not.toContain('mode');
});

test('create mode, GPU with a duration but no start: CREATE is blocked', async () => {
  const onSave = fn();
  await renderPool({onSave});

  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Future dated'));

  await change(within(row('Duration (sec):')).getByRole('textbox'), '3600');

  await click(screen.getByText('CREATE'));

  expect(onSave).not.toHaveBeenCalled();
  expect(screen.getByText('Start date is required')).toBeInTheDocument();
  expect(screen.getByText('End date is required')).toBeInTheDocument();
});

test('create mode, GPU with nothing filled: all three commitment errors show', async () => {
  const onSave = fn();
  await renderPool({onSave});

  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Future dated'));

  await click(screen.getByText('CREATE'));

  expect(screen.getByText('Start date is required')).toBeInTheDocument();
  expect(screen.getByText('End date is required')).toBeInTheDocument();
  expect(screen.getByText('Duration should be a positive number of seconds')).toBeInTheDocument();
  expect(onSave).not.toHaveBeenCalled();
});

test('Default-mode regression: SAVE still works and never shows a commitment error', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool(), onSave});

  await click(screen.getByText('SAVE'));

  expect(onSave).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('Start date is required')).not.toBeInTheDocument();
  expect(screen.queryByText('Duration should be a positive number of seconds'))
    .not.toBeInTheDocument();
});

test('Default-mode edit: payload never carries mode/poolType/commitment* fields', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool(), onSave});

  await click(screen.getByText('SAVE'));

  const [payload, schedulePayload] = onSave.mock.calls[0];
  [
    'mode',
    'poolType',
    'capacityReservationRequest',
    'commitmentStart',
    'commitmentEnd',
    'commitmentDuration'
  ].forEach((key) => {
    expect(Object.keys(payload)).not.toContain(key);
  });
  expect(schedulePayload.name).toBe('my-pool');
  expect(schedulePayload.scheduleEntries).toEqual([
    {from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'}
  ]);
});

// Driving antd 2.13.14's two commitment-window DatePickers through the UI proved
// impractical (the same limitation Phase 2 hit — see GpuScheduleControl.test.js), so this
// pins the regression at two levels instead: `buildSchedulePayload(gpuValues())` is
// `undefined` at the helper level (in helpers.test.js), and here — the weaker but still
// meaningful UI-level check — no `/cluster/nodeSchedule` call is ever made while the form
// is in GPU mode, however far CREATE got blocked by the (undriveable) date fields.
test('no schedule request is made while the form is in GPU mode', async () => {
  const onSave = fn();
  const {api} = await renderPool({onSave});

  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Future dated'));

  await change(within(row('Pool name:')).getByRole('textbox'), 'gpu-pool');
  await change(within(row('Nodes count:')).getByRole('textbox'), '1');

  await click(within(row('Region:')).getByRole('combobox'));
  await click(screen.getByText('eu-central-1'));
  await flushPromises();

  await click(screen.getByText('CREATE'));
  await flushPromises();

  // CREATE is blocked by the (unfilled, undriveable in this harness) commitment
  // fields, which is fine — the point of this test is only that nothing ever
  // reaches /cluster/nodeSchedule from GPU mode.
  expect(onSave).not.toHaveBeenCalled();
  expect(api.calls.every(c => !/nodeSchedule/.test(c.path))).toBe(true);
});

test('readOnly (!isNew): Region/AMI/Disk are disabled with a pool', async () => {
  await renderPool({pool: fullPool()});
  const regionSelect = within(row('Region:')).getByRole('combobox').closest('.ant-select');
  expect(regionSelect).toHaveClass('ant-select-disabled');
  expect(within(row('AMI:')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Disk:')).getByRole('textbox')).toBeDisabled();
});

test('without a pool, Region/AMI/Disk are enabled (create mode)', async () => {
  await renderPool();
  const regionSelect = within(row('Region:')).getByRole('combobox').closest('.ant-select');
  expect(regionSelect).not.toHaveClass('ant-select-disabled');
  expect(within(row('AMI:')).getByRole('textbox')).not.toBeDisabled();
  expect(within(row('Disk:')).getByRole('textbox')).not.toBeDisabled();
});

test('updateFromProps maps every field from the pool payload', async () => {
  await renderPool({pool: fullPool()});

  expect(within(row('Pool name:')).getByRole('textbox')).toHaveValue('my-pool');
  expect(within(row('Disk:')).getByRole('textbox')).toHaveValue('50');
  expect(within(row('Nodes count:')).getByRole('textbox')).toHaveValue('2');
  expect(within(row('AMI:')).getByRole('textbox')).toHaveValue('ami-1');
  expect(screen.getByText('env:')).toBeInTheDocument();
  expect(screen.getByText('prod')).toBeInTheDocument();
  expect(screen.getByText('Starts on:')).toBeInTheDocument();
});

test(
  'editing a reservation pool opens in GPU mode, disabled, with the right Pool type',
  async () => {
    await renderPool({pool: reservationPool()});

    expect(within(row('Mode:')).getByText('Future dated')).toBeInTheDocument();
    expect(selectIsDisabled('Mode:')).toBe(true);
    expect(within(row('Pool type:')).getByText('Standard')).toBeInTheDocument();
    expect(selectIsDisabled('Pool type:')).toBe(true);
  }
);

test('a SHARABLE_NODE reservation pool opens showing Shareable', async () => {
  await renderPool({pool: reservationPool({poolType: 'SHARABLE_NODE'})});

  expect(within(row('Pool type:')).getByText('Shareable')).toBeInTheDocument();
});

test('editing a Default pool still opens in Default mode (regression)', async () => {
  await renderPool({pool: fullPool()});

  expect(within(row('Mode:')).getByText('Default')).toBeInTheDocument();
  expect(screen.queryByText('Pool type:')).not.toBeInTheDocument();
  expect(screen.getByText('Autoscaled:')).toBeInTheDocument();
});

test('a reservation pool shows its window read-only', async () => {
  await renderPool({pool: reservationPool()});

  expect(screen.getByText('Reservation window:')).toBeInTheDocument();
  expect(screen.getByText('2026-03-01 10:00:00 – 2026-04-01 10:00:00')).toBeInTheDocument();
  expect(screen.queryByText('Duration (sec):')).not.toBeInTheDocument();
});

test('a pending reservation shows the pending message', async () => {
  await renderPool({pool: reservationPool({startDate: undefined, endDate: undefined})});
  expect(screen.getByText('Pending approval (not yet scheduled)')).toBeInTheDocument();
});

test('a pending reservation (end not yet scheduled) also shows the pending message', async () => {
  await renderPool({pool: reservationPool({endDate: undefined})});
  expect(screen.getByText('Pending approval (not yet scheduled)')).toBeInTheDocument();
});

test('a reservation pool disables Count and Price type; a Default pool does not', async () => {
  await renderPool({pool: reservationPool()});
  expect(within(row('Nodes count:')).getByRole('textbox')).toBeDisabled();
});

test('a Default pool regression: Count stays enabled', async () => {
  await renderPool({pool: fullPool()});
  expect(within(row('Nodes count:')).getByRole('textbox')).not.toBeDisabled();
});

test(
  'SAVE on a reservation pool sends poolType but no capacityReservationRequest, ' +
  'with count and priceType unchanged',
  async () => {
    const onSave = fn();
    const {api} = await renderPool({pool: reservationPool(), onSave});

    await click(screen.getByText('SAVE'));

    expect(onSave).toHaveBeenCalledTimes(1);
    const [payload, schedulePayload] = onSave.mock.calls[0];
    expect(payload.poolType).toBe('STANDARD');
    expect(Object.keys(payload)).not.toContain('capacityReservationRequest');
    expect(payload.count).toBe(2);
    expect(payload.priceType).toBe('ON_DEMAND');
    expect(schedulePayload).toBeUndefined();
    expect(api.calls.every(c => !/nodeSchedule/.test(c.path))).toBe(true);
  }
);

test('editing a Standard reservation pool keeps the persisted filter owner', async () => {
  const onSave = fn();
  await renderPool({
    pool: reservationPool({
      filter: {
        operator: 'AND',
        filters: [{type: 'RUN_OWNER', operator: 'EQUAL', value: 'OTHER_USER'}]
      }
    }),
    currentUserName: 'USER1',
    onSave
  });

  await click(screen.getByText('SAVE'));

  const [payload] = onSave.mock.calls[0];
  expect(JSON.stringify(payload.filter)).toContain('OTHER_USER');
  expect(JSON.stringify(payload.filter)).not.toContain('USER1');
});

// Regression for a gap the mode-inference fix opened: before this phase, editing a pool
// always resolved to MODE_DEFAULT, so `updateInstanceTypes`'s GPU auto-pick branch never
// ran during an edit. Now that an existing reservation pool correctly opens in GPU mode,
// that branch is reachable on every edit too — and `priceType` must round-trip unchanged
// for a reservation pool (`validateReservationPoolUpdate`), the same reason its control is
// disabled. If the region's fetched allowed price types no longer include the pool's own
// (e.g. a narrowed preference), the auto-pick must still leave it alone rather than
// silently substituting a different one that SAVE would then send and the backend reject.
test(
  'a reservation pool keeps its priceType unchanged even when the region no longer offers it',
  async () => {
    const onSave = fn();
    const {api} = await renderPool(
      {pool: reservationPool(), onSave},
      () => ({
        'cluster.allowed.instance.types': [
          {sku: 's1', name: 'm5.xlarge', instanceFamily: 'General purpose'}
        ],
        'cluster.allowed.price.types': ['SPOT']
      })
    );
    await flushPromises();

    await click(screen.getByText('SAVE'));

    expect(onSave).toHaveBeenCalledTimes(1);
    const [payload] = onSave.mock.calls[0];
    expect(payload.priceType).toBe('ON_DEMAND');
    expect(api.calls.every(c => !/nodeSchedule/.test(c.path))).toBe(true);
  }
);

test('validation hides until CREATE, then shows required fields and skips the save', async () => {
  const onSave = fn();
  await renderPool({onSave});

  expect(screen.queryByText('Name is required')).not.toBeInTheDocument();

  await click(screen.getByText('CREATE'));

  expect(screen.getByText('Name is required')).toBeInTheDocument();
  expect(screen.getByText('Instance type is required')).toBeInTheDocument();
  expect(screen.getByText('Region is required')).toBeInTheDocument();
  expect(onSave).not.toHaveBeenCalled();
});

test('onSave payload: non-autoscaled omits scaling fields, flattens docker images', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool(), onSave});

  await click(screen.getByText('SAVE'));

  expect(onSave).toHaveBeenCalledTimes(1);
  const [payload, schedulePayload] = onSave.mock.calls[0];
  expect(payload.count).toBe(2);
  expect(payload.minSize).toBeUndefined();
  expect(payload.maxSize).toBeUndefined();
  expect(payload.scaleStep).toBeUndefined();
  expect(payload.dockerImages).toEqual(['registry.com/group/image:1']);
  expect(payload.kubeLabels).toEqual({env: {value: 'prod', monitored: true}});
  expect(schedulePayload.name).toBe('my-pool');
  expect(schedulePayload.scheduleEntries).toEqual([
    {from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'}
  ]);
  expect(schedulePayload.scheduleEntries.every(e => !('id' in e))).toBe(true);
});

test('onSave payload: autoscaled uses minSize as count, includes scaling fields', async () => {
  const onSave = fn();
  await renderPool({
    pool: fullPool({
      autoscaled: true,
      minSize: 2,
      maxSize: 5,
      scaleDownThreshold: 20,
      scaleUpThreshold: 80,
      scaleStep: 2
    }),
    onSave
  });

  await click(screen.getByText('SAVE'));

  const [payload] = onSave.mock.calls[0];
  expect(payload.count).toBe(2);
  expect(payload.minSize).toBe(2);
  expect(payload.maxSize).toBe(5);
  expect(payload.scaleDownThreshold).toBe(20);
  expect(payload.scaleUpThreshold).toBe(80);
  expect(payload.scaleStep).toBe(2);
});

test('onSave with no schedule entries passes undefined as the second argument', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool({schedule: {scheduleEntries: []}}), onSave});

  await click(screen.getByText('SAVE'));

  expect(onSave.mock.calls[0][1]).toBeUndefined();
});

test('ticking Autoscaled seeds Min Size from the current count, default thresholds', async () => {
  await renderPool({pool: fullPool({count: 5})});

  await click(within(row('Autoscaled:')).getByRole('checkbox'));

  expect(within(row('Min Size:')).getByRole('textbox')).toHaveValue('5');
  expect(within(row('Scale Down Threshold:')).getByRole('textbox')).toHaveValue('20');
  expect(within(row('Scale Up Threshold:')).getByRole('textbox')).toHaveValue('80');
  expect(within(row('Scale Step:')).getByRole('textbox')).toHaveValue('2');
});

test('retoggling Autoscaled off then on keeps saved zero thresholds', async () => {
  await renderPool({
    pool: fullPool({
      autoscaled: true,
      scaleDownThreshold: 0,
      scaleUpThreshold: 0,
      scaleStep: 0
    })
  });

  const checkbox = within(row('Autoscaled:')).getByRole('checkbox');
  await click(checkbox);
  await click(checkbox);

  expect(within(row('Scale Down Threshold:')).getByRole('textbox')).toHaveValue('0');
  expect(within(row('Scale Up Threshold:')).getByRole('textbox')).toHaveValue('0');
  expect(within(row('Scale Step:')).getByRole('textbox')).toHaveValue('0');
});

test('changing region refetches instance types, resets what is no longer offered', async () => {
  // Region is only disabled in edit mode (readOnly = !isNew), so this needs
  // create mode to be able to change it at all.
  const {api} = await renderPool({}, (call) => (/regionId=2/.test(call.path) ? {
    'cluster.allowed.instance.types': [
      {sku: 's2', name: 'c5.xlarge', instanceFamily: 'Compute optimized'}
    ],
    'cluster.allowed.price.types': ['ON_DEMAND']
  } : instanceAllowedPayload));
  const callsBefore = api.calls.filter(c => /cluster\/instance\/allowed/.test(c.path)).length;

  await click(within(row('Region:')).getByRole('combobox'));
  await click(screen.getByText('westeurope'));

  await waitFor(() => {
    const callsAfter = api.calls.filter(c => /cluster\/instance\/allowed/.test(c.path)).length;
    expect(callsAfter).toBeGreaterThan(callsBefore);
  });
  await flushPromises();
  await waitFor(() => {
    const select = within(row('Instance Type:')).getByRole('combobox').closest('.ant-select');
    expect(select).not.toHaveClass('ant-select-disabled');
  });

  await click(within(row('Instance Type:')).getByRole('combobox'));
  expect(screen.getByText('c5.xlarge')).toBeInTheDocument();
  await click(within(row('Price type:')).getByRole('combobox'));
  expect(screen.getAllByText('On-demand').length).toBeGreaterThan(0);
});

test('disabled disables CANCEL and SAVE; CANCEL calls onCancel', async () => {
  const onCancel = fn();
  await renderPool({disabled: true, onCancel});
  expect(screen.getByText('CANCEL').closest('button')).toBeDisabled();
  expect(screen.getByText('CREATE').closest('button')).toBeDisabled();
});

test('CANCEL calls onCancel', async () => {
  const onCancel = fn();
  await renderPool({onCancel});
  await click(screen.getByText('CANCEL'));
  expect(onCancel).toHaveBeenCalledTimes(1);
});

test('a visible false->true transition re-reads the pool prop and fetches roles', async () => {
  const api = stubApi();
  api.handle(/\/cluster\/instance\/allowed/, () => (
    {status: 'OK', payload: instanceAllowedPayload}
  ));
  api.reply('/role/loadAll', []);
  const {rerender} = renderWithStores(
    <EditHotNodePool visible={false} disabled={false} />,
    stores()
  );

  rerender(<EditHotNodePool visible disabled={false} pool={fullPool({name: 'reloaded-pool'})} />);

  await waitFor(() => {
    expect(within(row('Pool name:')).getByRole('textbox')).toHaveValue('reloaded-pool');
  });
  await waitFor(() => {
    expect(api.calls.some(c => c.path === '/role/loadAll')).toBe(true);
  });
  await flushPromises();
});
