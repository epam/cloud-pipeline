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
  await click(screen.getByText('GPU'));

  expect(within(row('Mode:')).getByText('GPU')).toBeInTheDocument();
});

test(
  'GPU + Standard locks Filters to the current user; Shareable unlocks; back to Default clears it',
  async () => {
    await renderPool();

    await click(within(row('Mode:')).getByRole('combobox'));
    await click(screen.getByText('GPU'));

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
    await click(screen.getByText('GPU'));

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

test('SAVE payload never carries mode', async () => {
  const onSave = fn();
  await renderPool({pool: fullPool(), onSave});

  await click(screen.getByText('SAVE'));

  expect(Object.keys(onSave.mock.calls[0][0])).not.toContain('mode');
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
