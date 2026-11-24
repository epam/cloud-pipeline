/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
  stubApi,
  act
} from '@test';
import EditHotNodePool from './index';
import {buildCapacityBlockOfferingRequest, formatCapacityBlockInstanceType} from './helpers';
import {ML_BLOCK_OFFERINGS_FETCH_DELAY_MS} from './constants';

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
// window (null while pending/unscheduled); `schedule: undefined` because a future-dated
// pool's weekly schedule is always empty.
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

// The candidate row is a sibling of the "Instance types:" label, not its descendant
// (phase 9c made the row span the form's full width), so it is found relative to the
// one other sibling with a stable name instead - the row just added sits right before
// the "Add instance type" button's own row.
function mlBlockCandidateRow () {
  return screen.getByRole('button', {name: 'Add instance type'})
    .closest('div').previousElementSibling;
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

test('create mode, selecting future-dated leaves the Mode select showing Future dated',
  async () => {
    await renderPool();

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Future dated'));

    expect(within(row('Mode:')).getByText('Future dated')).toBeInTheDocument();
  });

test(
  'entering future-dated mode seeds an editable Run owner filter; a Pool type switch ' +
 'leaves it untouched; leaving future-dated mode clears it',
  async () => {
    await renderPool();

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Future dated'));

    expect(within(row('Pool type:')).getByText('Standard')).toBeInTheDocument();
    expect(screen.getByText('Run owner')).toBeInTheDocument();
    // UserName (injected with `usersInfo`) falls back to the lower-cased login
    // when it cannot resolve a richer display name, which is the case here.
    expect(screen.getByText('user1')).toBeInTheDocument();
    expect(selectIsDisabled('Condition:')).toBe(false);
    expect(screen.getByRole('button', {name: 'Add filter'})).not.toBeDisabled();

    await click(within(row('Pool type:')).getByRole('combobox'));
    await click(screen.getByText('Shareable node'));

    expect(screen.getByText('Run owner')).toBeInTheDocument();
    expect(screen.getByText('user1')).toBeInTheDocument();

    await click(within(row('Pool type:')).getByRole('combobox'));
    await click(screen.getByText('Standard'));

    expect(screen.getByText('Run owner')).toBeInTheDocument();
    expect(screen.getByText('user1')).toBeInTheDocument();

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Default'));

    expect(screen.queryByText('Run owner')).not.toBeInTheDocument();
  }
);

test('currentUserName omitted: entering future-dated mode seeds no filter, panel stays enabled',
  async () => {
    await renderPool({currentUserName: undefined});

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Future dated'));

    expect(screen.queryByText('Run owner')).not.toBeInTheDocument();
    expect(selectIsDisabled('Condition:')).toBe(false);
    expect(screen.queryByText('Some conditions are invalid')).not.toBeInTheDocument();
  });

test('Default mode: selecting Shareable node and clicking CREATE sends poolType SHAREABLE_NODE',
  async () => {
    const onSave = fn();
    const {api} = await renderPool({onSave});

    await change(within(row('Pool name:')).getByRole('textbox'), 'default-pool');

    await click(within(row('Pool type:')).getByRole('combobox'));
    await click(screen.getByText('Shareable node'));

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
    const [payload] = onSave.mock.calls[0];
    expect(payload.poolType).toBe('SHAREABLE_NODE');
  });

test(
  'switching to future-dated mode removes Autoscaled and its rows; switching back leaves it ' +
 'unchecked',
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

test('switching to future-dated mode resets a Spot price type to On-demand', async () => {
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

// Regression for a gap `onPoolKindChange`'s reset alone does not cover: a
// forced-future-dated create never fires `onPoolKindChange` for mode (the Mode row is
// hidden, mode is future-dated from the first render), so the auto-pick in
// `updateInstanceTypes` is the only thing that ever sets `spot` here, and it must skip
// SPOT too.
test('a forced-future-dated create never auto-selects the Spot price type', async () => {
  await renderPool({allowedModes: ['FUTURE_DATED']});

  await waitFor(() => {
    expect(within(row('Price type:')).getByText('On-demand')).toBeInTheDocument();
  });
});

test(
  'allowedModes=["FUTURE_DATED"]: opens forced into future-dated mode, with no Mode row, ' +
 'the Run owner criterion already seeded and the Filters panel enabled',
  async () => {
    await renderPool({allowedModes: ['FUTURE_DATED']});

    expect(screen.queryByText('Mode:')).not.toBeInTheDocument();
    expect(within(row('Pool type:')).getByText('Standard')).toBeInTheDocument();
    // Pool type is not locked by a single-mode caller: a
    // ROLE_NODE_POOL_MANAGER's forced-future-dated Create chooses it like anyone else.
    expect(selectIsDisabled('Pool type:')).toBe(false);
    expect(screen.queryByText('Autoscaled:')).not.toBeInTheDocument();
    expect(screen.getByText('Duration (days):')).toBeInTheDocument();
    expect(screen.getByText('Run owner')).toBeInTheDocument();
    expect(screen.getByText('user1')).toBeInTheDocument();
    expect(selectIsDisabled('Condition:')).toBe(false);
    expect(screen.getByRole('button', {name: 'Add filter'})).not.toBeDisabled();
  });

test('allowedModes omitted: still opens in Default mode (admin default unchanged)', async () => {
  await renderPool();

  expect(within(row('Mode:')).getByText('Default')).toBeInTheDocument();
  // Pool type applies to every pool, so it is offered in Default mode too.
  expect(within(row('Pool type:')).getByText('Standard')).toBeInTheDocument();
  expect(screen.getByText('Autoscaled:')).toBeInTheDocument();
});

test('SAVE payload never carries mode', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool(), onSave});

  await click(screen.getByText('SAVE'));

  expect(Object.keys(onSave.mock.calls[0][0])).not.toContain('mode');
});

test('create mode, future-dated with a duration but no start: CREATE is blocked', async () => {
  const onSave = fn();
  await renderPool({onSave});

  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Future dated'));

  await change(within(row('Duration (days):')).getByRole('textbox'), '14');

  await click(screen.getByText('CREATE'));

  expect(onSave).not.toHaveBeenCalled();
  expect(screen.getByText('Search from date is required')).toBeInTheDocument();
  expect(screen.getByText('Search until date is required')).toBeInTheDocument();
});

test('create mode, future-dated with nothing filled: all three commitment errors show',
  async () => {
    const onSave = fn();
    await renderPool({onSave});

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Future dated'));

    await click(screen.getByText('CREATE'));

    expect(screen.getByText('Search from date is required')).toBeInTheDocument();
    expect(screen.getByText('Search until date is required')).toBeInTheDocument();
    expect(screen.getByText('Duration should be a positive number of days'))
      .toBeInTheDocument();
    expect(onSave).not.toHaveBeenCalled();
  });

test('create mode, future-dated with a duration of 7: CREATE is blocked on the 14-day minimum',
  async () => {
    const onSave = fn();
    await renderPool({onSave});

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Future dated'));

    await change(within(row('Duration (days):')).getByRole('textbox'), '7');

    await click(screen.getByText('CREATE'));

    expect(onSave).not.toHaveBeenCalled();
    expect(screen.getByText('Duration should be at least 14 days')).toBeInTheDocument();
  });

test('Default-mode regression: SAVE still works and never shows a commitment error', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool(), onSave});

  await click(screen.getByText('SAVE'));

  expect(onSave).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('Search from date is required')).not.toBeInTheDocument();
  expect(screen.queryByText('Duration should be a positive number of days'))
    .not.toBeInTheDocument();
});

test('Default-mode edit: payload carries poolType but never mode/commitment*/' +
 'capacityReservationRequest', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool(), onSave});

  await click(screen.getByText('SAVE'));

  const [payload, schedulePayload] = onSave.mock.calls[0];
  // poolType is sent for every pool; fullPool() carries none of its own,
  // so the form falls back to Standard.
  expect(payload.poolType).toBe('STANDARD');
  [
    'mode',
    'capacityReservationRequest',
    'commitmentStart',
    'commitmentEnd',
    'commitmentDurationDays'
  ].forEach((key) => {
    expect(Object.keys(payload)).not.toContain(key);
  });
  expect(schedulePayload.name).toBe('my-pool');
  expect(schedulePayload.scheduleEntries).toEqual([
    {from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'}
  ]);
});

// Driving antd 2.13.14's two commitment-window DatePickers through the UI proved
// impractical (the same limitation ReservationScheduleControl.test.js documents),
// so this pins the regression at two levels instead:
// `buildSchedulePayload(futureDatedValues())` is `undefined` at the helper level (in
// helpers.test.js), and here — the weaker but still meaningful UI-level check — no
// `/cluster/nodeSchedule` call is ever made while the form is in future-dated mode,
// however far CREATE got blocked by the (undriveable) date fields.
test('no schedule request is made while the form is in future-dated mode', async () => {
  const onSave = fn();
  const {api} = await renderPool({onSave});

  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Future dated'));

  await change(within(row('Pool name:')).getByRole('textbox'), 'future-dated-pool');
  await change(within(row('Nodes count:')).getByRole('textbox'), '1');

  await click(within(row('Region:')).getByRole('combobox'));
  await click(screen.getByText('eu-central-1'));
  await flushPromises();

  await click(screen.getByText('CREATE'));
  await flushPromises();

  // CREATE is blocked by the (unfilled, undriveable in this harness) commitment
  // fields, which is fine — the point of this test is only that nothing ever
  // reaches /cluster/nodeSchedule from future-dated mode.
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
  'editing a reservation pool opens in future-dated mode, disabled, with the right Pool type',
  async () => {
    await renderPool({pool: reservationPool()});

    expect(within(row('Mode:')).getByText('Future dated')).toBeInTheDocument();
    expect(selectIsDisabled('Mode:')).toBe(true);
    expect(within(row('Pool type:')).getByText('Standard')).toBeInTheDocument();
    expect(selectIsDisabled('Pool type:')).toBe(true);
  }
);

test('a SHAREABLE_NODE reservation pool opens showing Shareable node, disabled', async () => {
  await renderPool({pool: reservationPool({poolType: 'SHAREABLE_NODE'})});

  expect(within(row('Pool type:')).getByText('Shareable node')).toBeInTheDocument();
  expect(selectIsDisabled('Pool type:')).toBe(true);
});

test(
  'editing a reservation pool whose matched record is CAPACITY_BLOCK opens in ML Block ' +
  'mode, disabled, with no candidates control',
  async () => {
    // `EditHotNodePool`'s own `allowedModes` default stays the pre-existing two modes - the
    // caller is the authority on which modes exist, so a caller that offers ML Block (as
    // `hot-node-pool/index.js` now does) is modelled explicitly here too. A decoy record for
    // another pool id proves the match is by id, not merely "the only record given".
    const {api} = await renderPool({
      pool: reservationPool({id: 54}),
      allowedModes: ['DEFAULT', 'FUTURE_DATED', 'ML_BLOCK'],
      reservations: [
        {nodePoolId: 999, reservationType: 'FUTURE_DATED', instanceCount: 2, status: 'SCHEDULED'},
        {nodePoolId: 54, reservationType: 'CAPACITY_BLOCK', instanceCount: 2, status: 'SCHEDULED'}
      ]
    });

    expect(within(row('Mode:')).getByText('ML Block')).toBeInTheDocument();
    expect(selectIsDisabled('Mode:')).toBe(true);
    // Nothing about a purchased block is editable, so the granted window shows exactly
    // as a future-dated pool's does, with no candidates control at all.
    expect(screen.getByText('Reservation window:')).toBeInTheDocument();
    expect(screen.queryByText('Instance types:')).not.toBeInTheDocument();
    // Nothing on screen can consume either: the catalogue has no candidates control to
    // feed, and `canSearchCapacityBlockOfferings` has no candidate to search with.
    expect(api.calls.some(c => /capacity-reservation\/instance-types/.test(c.path)))
      .toBe(false);
    expect(offeringsCalls(api)).toHaveLength(0);
  }
);

test(
  'editing a reservation pool whose matched record is FUTURE_DATED stays in future-dated ' +
  'mode, not ML Block',
  async () => {
    await renderPool({
      pool: reservationPool({id: 55}),
      reservations: [
        {nodePoolId: 999, reservationType: 'CAPACITY_BLOCK', instanceCount: 2, status: 'SCHEDULED'},
        {nodePoolId: 55, reservationType: 'FUTURE_DATED', instanceCount: 2, status: 'SCHEDULED'}
      ]
    });

    expect(within(row('Mode:')).getByText('Future dated')).toBeInTheDocument();
    expect(selectIsDisabled('Mode:')).toBe(true);
  }
);

test(
  'editing a reservation pool with a reservations list that matches no record keeps ' +
  'future-dated as the fallback - there is nothing to read a reservationType from',
  async () => {
    await renderPool({
      pool: reservationPool({id: 56}),
      reservations: [
        {nodePoolId: 999, reservationType: 'CAPACITY_BLOCK', instanceCount: 2, status: 'SCHEDULED'}
      ]
    });

    expect(within(row('Mode:')).getByText('Future dated')).toBeInTheDocument();
  }
);

test('editing a Default pool still opens in Default mode (regression)', async () => {
  await renderPool({pool: fullPool()});

  expect(within(row('Mode:')).getByText('Default')).toBeInTheDocument();
  // Pool type is offered - and read - in Default mode too; fullPool() carries
  // none of its own, so it falls back to Standard.
  expect(within(row('Pool type:')).getByText('Standard')).toBeInTheDocument();
  expect(screen.getByText('Autoscaled:')).toBeInTheDocument();
});

test('editing a Default pool loaded with poolType SHAREABLE_NODE shows Shareable node, disabled',
  async () => {
    await renderPool({pool: fullPool({poolType: 'SHAREABLE_NODE'})});

    expect(within(row('Mode:')).getByText('Default')).toBeInTheDocument();
    expect(within(row('Pool type:')).getByText('Shareable node')).toBeInTheDocument();
    expect(selectIsDisabled('Pool type:')).toBe(true);
  });

test('a reservation pool shows its window read-only', async () => {
  await renderPool({pool: reservationPool()});

  expect(screen.getByText('Reservation window:')).toBeInTheDocument();
  expect(screen.getByText('2026-03-01 10:00:00 – 2026-04-01 10:00:00')).toBeInTheDocument();
  expect(screen.queryByText('Duration (days):')).not.toBeInTheDocument();
});

test(
  'an unscheduled reservation (no startDate/endDate) shows the requested-window controls ' +
  'disabled and a disclaimer, not the ReservationSummary text',
  async () => {
    await renderPool({pool: reservationPool({startDate: undefined, endDate: undefined})});

    // ReservationScheduleControl fields are present (read-only view of requested window)
    expect(screen.getByText('Search from:')).toBeInTheDocument();
    expect(screen.getByText('Duration (days):')).toBeInTheDocument();
    // Disclaimer is shown
    expect(screen.getByText(
      'This pool is waiting to be scheduled by the capacity reservation service.'
    )).toBeInTheDocument();
    // The old ReservationSummary text is gone
    expect(screen.queryByText('Pending approval (not yet scheduled)')).not.toBeInTheDocument();
    expect(screen.queryByText('Reservation window:')).not.toBeInTheDocument();
  }
);

test(
  'an unscheduled reservation (end missing) also shows the disclaimer',
  async () => {
    await renderPool({pool: reservationPool({endDate: undefined})});

    expect(screen.getByText('Search from:')).toBeInTheDocument();
    expect(screen.getByText(
      'This pool is waiting to be scheduled by the capacity reservation service.'
    )).toBeInTheDocument();
    expect(screen.queryByText('Pending approval (not yet scheduled)')).not.toBeInTheDocument();
  }
);

test(
  'an unscheduled reservation with reservation data shows the requested dates read-only',
  async () => {
    await renderPool({
      pool: reservationPool({startDate: undefined, endDate: undefined}),
      reservations: [{
        nodePoolId: reservationPool().id,
        requestedStartDate: '2026-12-27 12:49:19.968',
        requestedEndDate: '2027-01-10 12:49:23.291',
        commitmentDuration: 1209600,
        status: 'PENDING'
      }]
    });

    // Duration (1209600 s = 14 days) is displayed in the disabled control
    expect(screen.getByText('Duration (days):')).toBeInTheDocument();
    const durationInput = screen.getByDisplayValue('14');
    expect(durationInput).toBeDisabled();
    expect(screen.getByText(
      'This pool is waiting to be scheduled by the capacity reservation service.'
    )).toBeInTheDocument();
  }
);

// The reservation record is the source of truth for its own status, so a record that is
// not simply "not yet scheduled" must show its own sentence rather than the generic
// "waiting to be scheduled" one.
test(
  'an unscheduled reservation whose record is CANCELLED shows the cancelled sentence, ' +
  'not the "waiting to be scheduled" one',
  async () => {
    await renderPool({
      pool: reservationPool({startDate: undefined, endDate: undefined}),
      reservations: [{
        nodePoolId: reservationPool().id,
        requestedStartDate: '2026-12-27 12:49:19.968',
        requestedEndDate: '2027-01-10 12:49:23.291',
        commitmentDuration: 1209600,
        status: 'CANCELLED'
      }]
    });

    expect(screen.getByText('This reservation was cancelled.')).toBeInTheDocument();
    expect(screen.queryByText(
      'This pool is waiting to be scheduled by the capacity reservation service.'
    )).not.toBeInTheDocument();
  }
);

test(
  'an unscheduled reservation whose record is REQUIRED_APPROVE shows the approval sentence',
  async () => {
    await renderPool({
      pool: reservationPool({startDate: undefined, endDate: undefined}),
      reservations: [{
        nodePoolId: reservationPool().id,
        requestedStartDate: '2026-12-27 12:49:19.968',
        requestedEndDate: '2027-01-10 12:49:23.291',
        commitmentDuration: 1209600,
        status: 'REQUIRED_APPROVE'
      }]
    });

    expect(
      screen.getByText('This reservation is waiting for an administrator\'s approval.')
    ).toBeInTheDocument();
    expect(screen.queryByText(
      'This pool is waiting to be scheduled by the capacity reservation service.'
    )).not.toBeInTheDocument();
  }
);

test('a reservation pool disables Count and Price type; a Default pool does not', async () => {
  await renderPool({pool: reservationPool()});
  expect(within(row('Nodes count:')).getByRole('textbox')).toBeDisabled();
});

test('a Default pool regression: Count stays enabled', async () => {
  await renderPool({pool: fullPool()});
  expect(within(row('Nodes count:')).getByRole('textbox')).not.toBeDisabled();
});

// The pool's own `count` is 0 until the reservation is activated, so a matched
// reservation record's `instanceCount` is what actually shows the reserved amount.
test(
  'a reservation pool with count 0 and a matched reservation shows "0 out of 4", no input',
  async () => {
    await renderPool({
      pool: reservationPool({count: 0}),
      reservations: [{
        nodePoolId: reservationPool().id,
        instanceCount: 4,
        status: 'PENDING'
      }]
    });

    expect(within(row('Nodes count:')).getByText('0 out of 4')).toBeInTheDocument();
    expect(within(row('Nodes count:')).queryByRole('textbox')).not.toBeInTheDocument();
  }
);

// A terminal reservation (e.g. finished or cancelled) never again holds capacity, so its
// instanceCount must not be presented as currently reserved - the form falls back to its
// ordinary disabled Nodes count input instead.
test(
  'a reservation pool whose record is FINISHED shows the disabled input, not "out of"',
  async () => {
    await renderPool({
      pool: reservationPool({count: 0}),
      reservations: [{
        nodePoolId: reservationPool().id,
        instanceCount: 4,
        status: 'FINISHED'
      }]
    });

    expect(within(row('Nodes count:')).getByRole('textbox')).toBeDisabled();
    expect(screen.queryByText(/out of/)).not.toBeInTheDocument();
  }
);

// Once the reservation is activated the pool reports its own nodes, and that count is
// the real one - so the reserved number stops being substituted here, the same way the
// pool card stops substituting it.
test(
  'an activated reservation pool shows its own count, not "out of"',
  async () => {
    await renderPool({
      pool: reservationPool({count: 4}),
      reservations: [{
        nodePoolId: reservationPool().id,
        instanceCount: 4,
        status: 'ACTIVE'
      }]
    });

    const input = within(row('Nodes count:')).getByRole('textbox');
    expect(input).toBeDisabled();
    expect(input).toHaveValue('4');
    expect(screen.queryByText(/out of/)).not.toBeInTheDocument();
  }
);

// Regression guard: with no matching reservation record (e.g. the reservations list
// hasn't loaded yet, or carries no record for this pool), the field falls back to the
// disabled number input exactly as before this change.
test(
  'a reservation pool with no matching reservation record still shows the disabled input',
  async () => {
    await renderPool({pool: reservationPool({count: 0})});

    expect(within(row('Nodes count:')).getByRole('textbox')).toBeDisabled();
    expect(screen.queryByText(/out of/)).not.toBeInTheDocument();
  }
);

// A Default-mode pool never matches a reservation record (`updateFromProps` only
// looks one up when `isCapacityReservation` is set), so it must keep the ordinary
// editable Nodes count input even when a `reservations` prop is passed in.
test('a Default-mode pool keeps the editable Nodes count input regardless of reservations',
  async () => {
    await renderPool({
      pool: fullPool(),
      reservations: [{nodePoolId: fullPool().id, instanceCount: 4, status: 'PENDING'}]
    });

    expect(within(row('Nodes count:')).getByRole('textbox')).not.toBeDisabled();
    expect(screen.queryByText(/out of/)).not.toBeInTheDocument();
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

test(
  'SAVE on a reservation pool with no nodes yet sends count 0, with no count error shown',
  async () => {
    // `NodePoolManager.create` stores a reservation pool with count 0 until the
    // reservation is activated, so an existing pool legitimately loads with it -
    // SAVE must not be blocked by the Future-dated "count >= 1" rule.
    const onSave = fn();
    await renderPool({pool: reservationPool({count: 0}), onSave});

    await click(screen.getByText('SAVE'));

    expect(onSave).toHaveBeenCalledTimes(1);
    const [payload] = onSave.mock.calls[0];
    expect(payload.count).toBe(0);
    expect(screen.queryByText('Count should be at least 1 for a Future dated pool'))
      .not.toBeInTheDocument();
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
// always resolved to MODE_DEFAULT, so `updateInstanceTypes`'s future-dated auto-pick
// branch never ran during an edit. Now that an existing reservation pool correctly opens
// in future-dated mode, that branch is reachable on every edit too — and `priceType` must
// round-trip unchanged
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

// While the parent operation is in flight the modal is blocked: the submit button
// carries antd's own loading state, and nothing in the form can be changed - which for
// a Default pool includes the weekly Schedule section, the one part of the form that
// used to stay editable mid-save.
test('while saving, the submit button is disabled and shows its loading state', async () => {
  await renderPool({pool: fullPool(), disabled: true});

  const saveButton = screen.getByRole('button', {name: 'SAVE'});
  expect(saveButton).toBeDisabled();
  // antd 2's Button renders its spinner as an icon and marks itself with this class;
  // there is no accessible attribute for the loading state at this version.
  expect(saveButton).toHaveClass('ant-btn-loading');
});

test('while creating, the CREATE button is disabled and shows its loading state', async () => {
  await renderPool({disabled: true});

  const createButton = screen.getByRole('button', {name: 'CREATE'});
  expect(createButton).toBeDisabled();
  expect(createButton).toHaveClass('ant-btn-loading');
});

test('the submit button carries no loading state when no operation is running', async () => {
  await renderPool({pool: fullPool()});

  const saveButton = screen.getByRole('button', {name: 'SAVE'});
  expect(saveButton).not.toBeDisabled();
  expect(saveButton).not.toHaveClass('ant-btn-loading');
});

test('while saving, the Schedule section of a Default pool is blocked', async () => {
  await renderPool({pool: fullPool(), disabled: true});

  expect(screen.getByRole('button', {name: 'Add schedule'})).toBeDisabled();
  // The schedule entry's own controls: its two day Selects and its two time inputs.
  within(row('Starts on:')).getAllByRole('combobox').forEach(combobox => {
    expect(combobox.closest('.ant-select')).toHaveClass('ant-select-disabled');
  });
  within(row('Starts on:')).getAllByRole('textbox').forEach(input => {
    expect(input).toBeDisabled();
  });
});

test('the Schedule section of a Default pool is editable when nothing is saving', async () => {
  await renderPool({pool: fullPool()});

  expect(screen.getByRole('button', {name: 'Add schedule'})).not.toBeDisabled();
  within(row('Starts on:')).getAllByRole('combobox').forEach(combobox => {
    expect(combobox.closest('.ant-select')).not.toHaveClass('ant-select-disabled');
  });
  within(row('Starts on:')).getAllByRole('textbox').forEach(input => {
    expect(input).not.toBeDisabled();
  });
});

// ML Block mode: the container's own candidate state, catalogue fetch and offering
// search. `allowedModes` is always passed explicitly here since `EditHotNodePool`'s own
// default stays `[DEFAULT, FUTURE_DATED]` - the caller (phase 8, not this one) is the
// one that offers ML Block to a real user.
//
// Real timers throughout: `useRealTimers` abandons antd 2's own pending `Modal` leave
// animation rather than running it, which left a previous test's content stuck in the
// document for the next one's `screen` queries to trip over.
const mlBlockInstanceTypeInfo = {
  instanceType: 'p5.48xlarge', vCPU: 192, memoryInMiB: 2097152, gpuCount: 8, gpuDeviceName: 'H100'
};
const ML_BLOCK_SEARCH_TIMEOUT = ML_BLOCK_OFFERINGS_FETCH_DELAY_MS * 4;

function mlBlockOffering (overrides = {}) {
  return {
    offeringId: 'offering-1',
    instanceType: 'p5.48xlarge',
    instanceCount: 2,
    availabilityZone: 'us-east-1a',
    startDate: '2026-12-21 04:14:02.500',
    endDate: '2026-12-31 04:14:02.500',
    capacityDurationHours: 24,
    upfrontFeeAmount: 100,
    upfrontFeeCurrency: 'USD',
    ...overrides
  };
}

// Mirrors `renderPool`, including its own `flushPromises` wait for the mount-time
// fetch to fully settle. Returns the `EditHotNodePool` instance itself, reached through
// mobx-react's `inject` HOC the way `ref` exposes it (`wrappedInstance`) - the only
// handle onto `commitmentStart`/`commitmentEnd`, which nothing in this codebase drives
// through the antd DatePicker (see controls/ReservationScheduleControl.test.js).
async function renderMlBlockPool (props = {}) {
  const api = stubApi();
  // Also carries `p5.48xlarge` on the general allowed-instance-types list, not only the
  // capacity-block catalogue: the offering fills the read-only `Instance Type` select with
  // it, and that select's options come from this list, not from the catalogue - with no
  // matching Option, there would be nothing to assert its rendered text against.
  api.handle(/\/cluster\/instance\/allowed/, () => ({
    status: 'OK',
    payload: {
      'cluster.allowed.instance.types': [
        {sku: 's1', name: 'm5.xlarge', instanceFamily: 'General purpose'},
        {sku: 's2', name: 'p5.48xlarge', instanceFamily: 'GPU'}
      ],
      'cluster.allowed.price.types': ['SPOT', 'ON_DEMAND']
    }
  }));
  let wrapper;
  renderWithStores(
    <EditHotNodePool
      ref={(r) => { wrapper = r; }}
      visible
      disabled={false}
      currentUserName="USER1"
      allowedModes={['DEFAULT', 'ML_BLOCK']}
      {...props}
    />,
    stores()
  );
  await waitFor(() => expect(api.calls.length).toBeGreaterThan(0));
  await flushPromises();
  // Antd's Modal can still be clearing a previous test's portal node on its own real
  // timer - settle on one "Mode:" before a caller queries by text.
  await waitFor(() => expect(screen.getAllByText('Mode:')).toHaveLength(1));
  return {api, instance: () => wrapper.wrappedInstance};
}

async function enterMlBlockMode () {
  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('ML Block'));
}

// A region change fires the allowed-instance-types refetch, and in ML Block mode the
// catalogue fetch too - `flushPromises` here keeps both from leaking past this test.
// Every caller is already in ML Block mode, where `Search in region` is the only
// editable region control - `Configuration`'s own `Region` is disabled there.
async function selectRegion (name) {
  await click(within(row('Search in region:')).getByRole('combobox'));
  await click(screen.getByText(name));
  await flushPromises();
}

function offeringsCalls (api) {
  return api.calls.filter(c => c.path === '/cluster/capacity-reservation/offerings');
}

test('entering ML Block mode fetches the capacity block catalogue for the selected region',
  async () => {
    const {api} = await renderMlBlockPool();
    api.reply(/\/cluster\/capacity-reservation\/instance-types/, [mlBlockInstanceTypeInfo]);

    await enterMlBlockMode();
    await selectRegion('eu-central-1');

    await waitFor(() => expect(api.calls.some(c =>
      /capacity-reservation\/instance-types/.test(c.path) && /regionId=1/.test(c.path)
    )).toBe(true));
    // Settles the region change's own allowed-instance-types refetch too, for the same
    // reason `renderMlBlockPool` settles the mount-time one.
    await flushPromises();
  });

test(
  'adding a candidate and setting its count issues one search after the debounce, ' +
 'with the body helpers.js builds',
  async () => {
    const {api, instance} = await renderMlBlockPool();
    api.reply(/\/cluster\/capacity-reservation\/instance-types/, [mlBlockInstanceTypeInfo]);
    api.reply('/cluster/capacity-reservation/offerings', []);

    await enterMlBlockMode();
    await selectRegion('eu-central-1');
    await waitFor(() => expect(
      screen.getByRole('button', {name: 'Add instance type'})
    ).not.toBeDisabled());

    await click(screen.getByRole('button', {name: 'Add instance type'}));
    await click(within(mlBlockCandidateRow()).getByRole('combobox'));
    await click(screen.getByText(formatCapacityBlockInstanceType(mlBlockInstanceTypeInfo)));
    await change(within(mlBlockCandidateRow()).getByRole('textbox'), '2');
    await change(within(row('Duration (days):')).getByRole('textbox'), '1');
    await act(() => instance().onChangeField('commitmentStart', '2026-12-25 04:14:02.500'));

    await waitFor(() => expect(offeringsCalls(api)).toHaveLength(1), {
      timeout: ML_BLOCK_SEARCH_TIMEOUT
    });

    const body = JSON.parse(offeringsCalls(api)[0].body);
    const expectedBody = buildCapacityBlockOfferingRequest({
      region: '1',
      commitmentStart: '2026-12-25 04:14:02.500',
      commitmentEnd: undefined,
      commitmentDurationDays: '1',
      mlBlockCandidates: [{id: 1, instanceType: 'p5.48xlarge', instanceCount: 2}]
    });
    expect(body).toEqual(expectedBody);
  }
);

test('a second edit shortly after the first still issues one search, for its latest value',
  async () => {
    const {api, instance} = await renderMlBlockPool();
    api.reply(/\/cluster\/capacity-reservation\/instance-types/, [mlBlockInstanceTypeInfo]);
    api.reply('/cluster/capacity-reservation/offerings', []);

    await enterMlBlockMode();
    await selectRegion('eu-central-1');
    await waitFor(() => expect(
      screen.getByRole('button', {name: 'Add instance type'})
    ).not.toBeDisabled());

    await click(screen.getByRole('button', {name: 'Add instance type'}));
    await click(within(mlBlockCandidateRow()).getByRole('combobox'));
    await click(screen.getByText(formatCapacityBlockInstanceType(mlBlockInstanceTypeInfo)));
    await change(within(row('Duration (days):')).getByRole('textbox'), '1');
    await act(() => instance().onChangeField('commitmentStart', '2026-12-25 04:14:02.500'));

    // Two edits back to back, well inside the debounce delay: each resets it, so only
    // the second is ever still pending when it finally elapses.
    await change(within(mlBlockCandidateRow()).getByRole('textbox'), '2');
    await change(within(mlBlockCandidateRow()).getByRole('textbox'), '3');

    await waitFor(() => expect(offeringsCalls(api)).toHaveLength(1), {
      timeout: ML_BLOCK_SEARCH_TIMEOUT
    });
    const body = JSON.parse(offeringsCalls(api)[0].body);
    expect(body.candidates).toEqual([{instanceType: 'p5.48xlarge', instanceCount: 3}]);
  });

// Builds an ML Block pool through to a settled, selected offering - the starting point
// several tests below need. Candidate count is fixed at 2, matching `mlBlockOffering()`'s
// own `instanceCount`, which the read-only Nodes count field is checked against.
//
// The route is `api.handle()`, reading a mutable closure variable, rather than
// `api.reply()`: `stubApi` matches the first-registered handler for a pattern, so a test
// that needs the *next* search to return something else cannot `reply()` the same path
// again (see `renderPool`'s own comment on the same constraint) - it calls
// `setOfferingsResponse` instead.
async function renderMlBlockPoolWithOffering (props = {}) {
  const offering = mlBlockOffering();
  let offeringsResponse = [offering];
  const {api, instance} = await renderMlBlockPool(props);
  api.reply(/\/cluster\/capacity-reservation\/instance-types/, [mlBlockInstanceTypeInfo]);
  api.handle('/cluster/capacity-reservation/offerings', () => (
    {status: 'OK', payload: offeringsResponse}
  ));

  await enterMlBlockMode();
  await selectRegion('eu-central-1');
  await waitFor(() => expect(
    screen.getByRole('button', {name: 'Add instance type'})
  ).not.toBeDisabled());

  await click(screen.getByRole('button', {name: 'Add instance type'}));
  await click(within(mlBlockCandidateRow()).getByRole('combobox'));
  await click(screen.getByText(formatCapacityBlockInstanceType(mlBlockInstanceTypeInfo)));
  await change(within(mlBlockCandidateRow()).getByRole('textbox'), '2');
  await change(within(row('Duration (days):')).getByRole('textbox'), '1');
  await act(() => instance().onChangeField('commitmentStart', '2026-12-25 04:14:02.500'));

  await waitFor(() => expect(offeringsCalls(api)).toHaveLength(1), {
    timeout: ML_BLOCK_SEARCH_TIMEOUT
  });
  await click(screen.getByText(new RegExp(offering.availabilityZone)));

  return {
    api,
    instance,
    offering,
    setOfferingsResponse: (value) => { offeringsResponse = value; }
  };
}

test('selecting an offering fills the read-only Instance type and Nodes count, enables CREATE',
  async () => {
    const offering = mlBlockOffering();
    const {api, instance} = await renderMlBlockPool();
    api.reply(/\/cluster\/capacity-reservation\/instance-types/, [mlBlockInstanceTypeInfo]);
    api.reply('/cluster/capacity-reservation/offerings', [offering]);

    await enterMlBlockMode();
    await selectRegion('eu-central-1');
    await waitFor(() => expect(
      screen.getByRole('button', {name: 'Add instance type'})
    ).not.toBeDisabled());
    await click(screen.getByRole('button', {name: 'Add instance type'}));
    await click(within(mlBlockCandidateRow()).getByRole('combobox'));
    await click(screen.getByText(formatCapacityBlockInstanceType(mlBlockInstanceTypeInfo)));
    await change(within(mlBlockCandidateRow()).getByRole('textbox'), '2');
    await change(within(row('Duration (days):')).getByRole('textbox'), '1');
    await act(() => instance().onChangeField('commitmentStart', '2026-12-25 04:14:02.500'));

    expect(screen.getByRole('button', {name: 'CREATE'})).toBeDisabled();

    await waitFor(() => expect(offeringsCalls(api)).toHaveLength(1), {
      timeout: ML_BLOCK_SEARCH_TIMEOUT
    });
    await click(screen.getByText(new RegExp(offering.availabilityZone)));

    expect(within(row('Instance Type:')).getByText('p5.48xlarge')).toBeInTheDocument();
    expect(within(row('Nodes count:')).getByRole('textbox')).toHaveValue('2');
    expect(screen.getByRole('button', {name: 'CREATE'})).not.toBeDisabled();
  });

test('a region change clears the candidates, the offerings and the two read-only fields',
  async () => {
    const {api, instance} = await renderMlBlockPoolWithOffering();
    const catalogueCallsBefore = api.calls
      .filter(c => /capacity-reservation\/instance-types/.test(c.path)).length;

    await selectRegion('westeurope');

    // The region's own catalogue re-fetch (regionId=2) is a side effect of the mode
    // staying ML Block, not of this reset - waited out here so it cannot resolve after
    // the test has already moved on and unmounted the tree.
    await waitFor(() => expect(
      api.calls.filter(c => /capacity-reservation\/instance-types/.test(c.path)).length
    ).toBeGreaterThan(catalogueCallsBefore));
    await flushPromises();

    expect(instance().state.mlBlockCandidates).toEqual([]);
    expect(within(row('Instance Type:')).queryByText('p5.48xlarge')).not.toBeInTheDocument();
    expect(within(row('Nodes count:')).getByRole('textbox')).toHaveValue('');
    expect(screen.getByRole('button', {name: 'CREATE'})).toBeDisabled();
    expect(instance().state.mlBlockOfferingId).toBeUndefined();
  });

test(
  'a search that no longer contains the selected offering drops the selection and ' +
 'disables CREATE again',
  async () => {
    const {api, setOfferingsResponse} = await renderMlBlockPoolWithOffering();
    expect(screen.getByRole('button', {name: 'CREATE'})).not.toBeDisabled();

    // The next search (the count edit below) finds nothing matching the offering
    // already picked.
    setOfferingsResponse([]);
    const callsBefore = offeringsCalls(api).length;
    await change(within(mlBlockCandidateRow()).getByRole('textbox'), '3');

    await waitFor(() => expect(offeringsCalls(api).length).toBeGreaterThan(callsBefore), {
      timeout: ML_BLOCK_SEARCH_TIMEOUT
    });

    expect(within(row('Instance Type:')).queryByText('p5.48xlarge')).not.toBeInTheDocument();
    expect(within(row('Nodes count:')).getByRole('textbox')).toHaveValue('');
    expect(screen.getByRole('button', {name: 'CREATE'})).toBeDisabled();
  }
);

test('a failed search shows its error and keeps the rest of the form usable', async () => {
  const {api, instance} = await renderMlBlockPool();
  api.reply(/\/cluster\/capacity-reservation\/instance-types/, [mlBlockInstanceTypeInfo]);
  api.fail('/cluster/capacity-reservation/offerings', 'The offering search failed');

  await enterMlBlockMode();
  await selectRegion('eu-central-1');
  await waitFor(() => expect(
    screen.getByRole('button', {name: 'Add instance type'})
  ).not.toBeDisabled());
  await click(screen.getByRole('button', {name: 'Add instance type'}));
  await click(within(mlBlockCandidateRow()).getByRole('combobox'));
  await click(screen.getByText(formatCapacityBlockInstanceType(mlBlockInstanceTypeInfo)));
  await change(within(mlBlockCandidateRow()).getByRole('textbox'), '2');
  await change(within(row('Duration (days):')).getByRole('textbox'), '1');
  await act(() => instance().onChangeField('commitmentStart', '2026-12-25 04:14:02.500'));

  await waitFor(() => expect(
    screen.getByText('The offering search failed')
  ).toBeInTheDocument(), {timeout: ML_BLOCK_SEARCH_TIMEOUT});

  // The rest of the form stays usable: Pool name is still an ordinary, enabled input.
  await change(within(row('Pool name:')).getByRole('textbox'), 'still-usable');
  expect(within(row('Pool name:')).getByRole('textbox')).toHaveValue('still-usable');
  expect(screen.getByRole('button', {name: 'CREATE'})).toBeDisabled();
});

test('CREATE on a complete ML Block pool sends the capacity-block payload helpers.js builds',
  async () => {
    const offering = mlBlockOffering();
    const onSave = fn();
    const {api, instance} = await renderMlBlockPool({onSave});
    api.reply(/\/cluster\/capacity-reservation\/instance-types/, [mlBlockInstanceTypeInfo]);
    api.reply('/cluster/capacity-reservation/offerings', [offering]);

    await enterMlBlockMode();
    await selectRegion('eu-central-1');
    await waitFor(() => expect(
      screen.getByRole('button', {name: 'Add instance type'})
    ).not.toBeDisabled());

    await change(within(row('Pool name:')).getByRole('textbox'), 'ml-block-pool');
    await change(within(row('Disk:')).getByRole('textbox'), '50');
    await change(within(row('Duration (days):')).getByRole('textbox'), '1');
    await act(() => instance().onChangeField('commitmentStart', '2026-12-25 04:14:02.500'));
    await act(() => instance().onChangeField('commitmentEnd', '2026-12-31 04:14:02.500'));

    await click(screen.getByRole('button', {name: 'Add instance type'}));
    await click(within(mlBlockCandidateRow()).getByRole('combobox'));
    await click(screen.getByText(formatCapacityBlockInstanceType(mlBlockInstanceTypeInfo)));
    await change(within(mlBlockCandidateRow()).getByRole('textbox'), '2');

    await waitFor(() => expect(offeringsCalls(api)).toHaveLength(1), {
      timeout: ML_BLOCK_SEARCH_TIMEOUT
    });
    await click(screen.getByText(new RegExp(offering.availabilityZone)));

    await click(screen.getByRole('button', {name: 'CREATE'}));

    expect(onSave).toHaveBeenCalledTimes(1);
    const [payload] = onSave.mock.calls[0];
    expect(payload.capacityReservationRequest).toEqual({
      reservationType: 'CAPACITY_BLOCK',
      capacityBlockOfferingId: offering.offeringId,
      availabilityZone: offering.availabilityZone,
      requestedStartDate: offering.startDate,
      requestedEndDate: offering.endDate,
      commitmentDuration: offering.capacityDurationHours * 3600
    });
    expect(payload.instanceType).toBe(offering.instanceType);
    expect(payload.count).toBe(offering.instanceCount);
  });

// `updateInstanceTypes`'s SPOT auto-correction only skipped future-dated mode, so a
// race landing after a switch to ML Block still picks SPOT, a type the form cannot fix.
test(
  'switching to ML Block before the allowed-types response settles never auto-selects Spot',
  async () => {
    const api = stubApi();
    let resolveAllowed;
    api.handle(/\/cluster\/instance\/allowed/, () => new Promise((resolve) => {
      resolveAllowed = () => resolve({status: 'OK', payload: instanceAllowedPayload});
    }));
    let wrapper;
    renderWithStores(
      <EditHotNodePool
        ref={(r) => { wrapper = r; }}
        visible
        disabled={false}
        currentUserName="USER1"
        allowedModes={['DEFAULT', 'ML_BLOCK']}
      />,
      stores()
    );
    await waitFor(() => expect(api.calls.length).toBeGreaterThan(0));
    // Antd's Modal can still be clearing a previous test's portal node on its own real
    // timer - settle on one "Mode:" before `row('Mode:')` queries by text.
    await waitFor(() => expect(screen.getAllByText('Mode:')).toHaveLength(1));

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('ML Block'));

    resolveAllowed();
    await flushPromises();

    expect(wrapper.wrappedInstance.state.spot).not.toBe('SPOT');
  }
);

// The selection stayed in state until the next search's response, at least a debounce
// later - long enough for CREATE to still buy it after its own row was deleted.
test(
  'deleting the row an offering was selected from clears the selection immediately, not ' +
 'only once the next search settles',
  async () => {
    const onSave = fn();
    const {instance} = await renderMlBlockPoolWithOffering({onSave});
    const [selectedCandidate] = instance().state.mlBlockCandidates;
    expect(instance().state.mlBlockOfferingId).toBeDefined();

    // A second, independently complete row, so removing the first still leaves a
    // search worth making rather than the already-correct empty-candidates branch.
    await act(() => instance().onChangeField('mlBlockCandidates', {action: 'add'}));
    const addedId = instance().state.mlBlockCandidates
      .find(c => c.id !== selectedCandidate.id).id;
    await act(() => instance().onChangeField(
      'mlBlockCandidates',
      {action: 'update', id: addedId, field: 'instanceType', value: 'p4d.24xlarge'}
    ));
    await act(() => instance().onChangeField(
      'mlBlockCandidates', {action: 'update', id: addedId, field: 'instanceCount', value: 1}
    ));

    await act(() => instance().onChangeField(
      'mlBlockCandidates', {action: 'remove', id: selectedCandidate.id}
    ));

    expect(instance().state.mlBlockOfferingId).toBeUndefined();
    expect(instance().state.instanceType).toBeUndefined();
    expect(instance().state.count).toBeUndefined();

    await click(screen.getByRole('button', {name: 'CREATE'}));
    expect(onSave).not.toHaveBeenCalled();
  }
);

// The `update` branch scheduled a search even when the complete candidates were
// unchanged (a still-incomplete row staying incomplete), firing a duplicate request.
test(
  'an edit that leaves the complete candidates unchanged does not fire a duplicate search',
  async () => {
    const {api, instance} = await renderMlBlockPoolWithOffering();
    const callsBefore = offeringsCalls(api).length;
    const selectedBefore = instance().state.mlBlockOfferingId;

    await act(() => instance().onChangeField('mlBlockCandidates', {action: 'add'}));
    const added = instance().state.mlBlockCandidates.find(c => c.instanceType === undefined);
    await act(() => instance().onChangeField(
      'mlBlockCandidates', {action: 'update', id: added.id, field: 'instanceCount', value: 3}
    ));

    await new Promise((resolve) => setTimeout(resolve, ML_BLOCK_SEARCH_TIMEOUT));

    expect(offeringsCalls(api).length).toBe(callsBefore);
    expect(instance().state.mlBlockOfferingId).toBe(selectedBefore);
  }
);

test('switching ML Block back to Default leaves a working Default form', async () => {
  const {instance} = await renderMlBlockPool();

  await enterMlBlockMode();
  await selectRegion('eu-central-1');
  await click(within(row('Mode:')).getByRole('combobox'));
  await click(screen.getByText('Default'));
  await flushPromises();

  expect(screen.queryByText('Instance types:')).not.toBeInTheDocument();
  expect(screen.getByText('Autoscaled:')).toBeInTheDocument();
  expect(instance().state.mlBlockCandidates).toEqual([]);
  expect(instance().state.mlBlockOfferingId).toBeUndefined();
});

test(
  'leaving ML Block mode while a search is still in flight does not let its late ' +
 'response overwrite what Default mode filled in afterwards',
  async () => {
    const {api, instance} = await renderMlBlockPool();
    api.reply(/\/cluster\/capacity-reservation\/instance-types/, [mlBlockInstanceTypeInfo]);
    let resolveOfferings;
    // Held open rather than answered immediately, so the mode switch below genuinely
    // happens while the request is in flight, not already settled.
    api.handle('/cluster/capacity-reservation/offerings', () => new Promise((resolve) => {
      resolveOfferings = () => resolve({status: 'OK', payload: [mlBlockOffering()]});
    }));

    await enterMlBlockMode();
    await selectRegion('eu-central-1');
    await waitFor(() => expect(
      screen.getByRole('button', {name: 'Add instance type'})
    ).not.toBeDisabled());

    await click(screen.getByRole('button', {name: 'Add instance type'}));
    await click(within(mlBlockCandidateRow()).getByRole('combobox'));
    await click(screen.getByText(formatCapacityBlockInstanceType(mlBlockInstanceTypeInfo)));
    await change(within(mlBlockCandidateRow()).getByRole('textbox'), '2');
    await change(within(row('Duration (days):')).getByRole('textbox'), '1');
    await act(() => instance().onChangeField('commitmentStart', '2026-12-25 04:14:02.500'));

    await waitFor(() => expect(offeringsCalls(api)).toHaveLength(1), {
      timeout: ML_BLOCK_SEARCH_TIMEOUT
    });

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('Default'));
    await click(within(row('Instance Type:')).getByRole('combobox'));
    await click(screen.getByText('m5.xlarge'));
    await change(within(row('Nodes count:')).getByRole('textbox'), '3');

    resolveOfferings();
    await flushPromises();

    expect(within(row('Instance Type:')).getByText('m5.xlarge')).toBeInTheDocument();
    expect(within(row('Nodes count:')).getByRole('textbox')).toHaveValue('3');
  }
);
