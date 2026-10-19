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
  waitFor,
  flushPromises,
  fn,
  stubApi
} from '@test';
import HotCluster from './index';
import pools from '../../../models/cluster/HotNodePools';
import clusterNodes from '../../../models/cluster/ClusterNodes';
import reservations from '../../../models/cluster/CapacityReservations';

const loaded = (value) => observable({loaded: true, pending: false, value});

const regionsList = [
  {id: 1, name: 'eu-central-1', provider: 'aws'}
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

function stores (authenticatedUserInfo) {
  return {
    authenticatedUserInfo,
    awsRegions: awsRegionsStore(),
    cloudRegionsInfo: loaded(regionsList),
    allInstanceTypes: loaded([]),
    dockerRegistries: observable({
      loaded: true,
      pending: false,
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

const myPool = {
  id: 1,
  name: 'my-pool',
  instanceDisk: 50,
  instanceType: 'm5.xlarge',
  dockerImages: [],
  count: 2,
  priceType: 'SPOT',
  regionId: 1,
  autoscaled: false,
  kubeLabels: {}
};

const instanceAllowedPayload = {
  'cluster.allowed.instance.types': [
    {sku: 's1', name: 'm5.xlarge', instanceFamily: 'General purpose'}
  ],
  'cluster.allowed.price.types': ['SPOT', 'ON_DEMAND']
};

const admin = {loaded: true, pending: false, value: {admin: true}};
const nonAdmin = {loaded: true, pending: false, value: {admin: false, roles: []}};
// `NodePoolApiService.create`'s `NODE_POOL_CREATE` ACL expression is
// `ADMIN OR RUN_ADMIN OR NODE_POOL_MANAGER`, so only a user carrying this role (and not
// a plain authenticated non-admin) may create a pool at all.
const nodePoolManager = {
  loaded: true,
  pending: false,
  // `userName` matches the `users` store fixture below, so the Run owner criterion
  // a forced-future-dated Create seeds (`buildRunOwnerFilter(currentUserName)`) has a
  // matching option to render, same as `edit-hot-node-pool/index.test.js` does.
  value: {admin: false, userName: 'USER1', roles: [{name: 'ROLE_NODE_POOL_MANAGER'}]}
};
const unloaded = {loaded: false, pending: false, value: undefined};

// `pools`/`clusterNodes`/`reservations` are module singletons `HotCluster` injects by
// name, so a previous test's resolved value would otherwise leak into the next one.
beforeEach(() => {
  pools.invalidateCache();
  clusterNodes.invalidateCache();
  reservations.invalidateCache();
});

function row (labelText) {
  return screen.getByText(labelText).closest('div');
}

// antd's Select combobox carries no native `disabled` attribute; the state is
// a class on the `.ant-select` ancestor instead.
function selectIsDisabled (labelText) {
  const combobox = within(row(labelText)).getByRole('combobox');
  return combobox.closest('.ant-select').classList.contains('ant-select-disabled');
}

async function renderCluster (authenticatedUserInfo, props = {}, poolsList = myPool) {
  const api = stubApi();
  api.reply(/^\/cluster\/pool\?/, [poolsList]);
  api.reply(/^\/cluster\/node\/loadAll\?/, []);
  api.reply(/^\/cluster\/capacity-reservation/, []);
  api.handle(/\/cluster\/instance\/allowed/, () => ({
    status: 'OK',
    payload: instanceAllowedPayload
  }));
  const result = renderWithStores(
    <HotCluster router={{push: fn()}} {...props} />,
    stores(authenticatedUserInfo)
  );
  await waitFor(() => expect(screen.getByText('my-pool')).toBeInTheDocument());
  await flushPromises();
  return {...result, api};
}

test('admin: Create is present, the pool card shows edit and remove buttons', async () => {
  await renderCluster(admin);

  expect(screen.getByText('Create')).toBeInTheDocument();
  expect(screen.getByText('my-pool').closest('.cp-hot-node-pool').querySelectorAll('button').length)
    .toBeGreaterThanOrEqual(3);
});

test(
  'plain non-admin: no Create button, no edit/remove on the card, click does nothing',
  async () => {
    const router = {push: fn()};
    await renderCluster(nonAdmin, {router});

    // `NODE_POOL_CREATE` is `ADMIN OR RUN_ADMIN OR NODE_POOL_MANAGER` - a plain
    // authenticated user with none of the three gets a 403 from the backend, so Create
    // must not be offered to them at all.
    expect(screen.queryByText('Create')).not.toBeInTheDocument();
    const card = screen.getByText('my-pool').closest('.cp-hot-node-pool');
    // No action at all: edit and remove need ADMIN/RUN_ADMIN or a grant on the pool, and
    // the "usage" (area-chart) button needs ADMIN_OR_CLUSTER_READER, which the usage page
    // behind it is gated on.
    expect(card.querySelectorAll('button').length).toBe(0);

    await click(card);

    expect(router.push).not.toHaveBeenCalled();
  });

test('unloaded: no Create button', async () => {
  await renderCluster(unloaded);

  expect(screen.queryByText('Create')).not.toBeInTheDocument();
});

test('NODE_POOL_MANAGER: Create is present, no action on a pool it does not own',
  async () => {
    const router = {push: fn()};
    await renderCluster(nodePoolManager, {router});

    expect(screen.getByText('Create')).toBeInTheDocument();
    const card = screen.getByText('my-pool').closest('.cp-hot-node-pool');
    // `myPool` carries no owner, so this manager holds nothing on it: `NODE_POOL_ID_WRITE`
    // grants the role itself no write. The usage button is gone too - that page is
    // ADMIN_OR_CLUSTER_READER, with no node-pool-manager exception.
    expect(card.querySelectorAll('button').length).toBe(0);

    await click(card);

    expect(router.push).not.toHaveBeenCalled();
  });

test('NODE_POOL_MANAGER: a pool it owns is editable from the card', async () => {
  const router = {push: fn()};
  await renderCluster(nodePoolManager, {router}, {...myPool, owner: 'USER1'});

  const card = screen.getByText('my-pool').closest('.cp-hot-node-pool');
  expect(card.querySelectorAll('.anticon-edit').length).toBe(1);
  // `NODE_POOL_ID_OWNER` grants the same ownership `NODE_POOL_ID_WRITE` does, so remove
  // is offered too; the usage button stays gone - that page is ADMIN_OR_CLUSTER_READER,
  // with no owner exception.
  expect(card.querySelectorAll('.anticon-area-chart').length).toBe(0);
  expect(card.querySelectorAll('.anticon-delete').length).toBe(1);

  // The card itself is clickable only where editing is allowed, so an owned pool now
  // opens its state view for a manager, where it did nothing before.
  await click(card);

  expect(router.push).toHaveBeenCalledWith('/cluster?pool_id=1');
});

test('NODE_POOL_MANAGER: the edit button on an owned pool opens the pool form', async () => {
  await renderCluster(nodePoolManager, {}, {...myPool, owner: 'USER1'});

  const card = screen.getByText('my-pool').closest('.cp-hot-node-pool');
  await click(card.querySelector('.anticon-edit').closest('button'));

  await waitFor(() => expect(screen.getByText('Pool type:')).toBeInTheDocument());
});

test(
  'NODE_POOL_MANAGER: Create opens the modal forced into future-dated mode end to end - ' +
  'no Mode row, an enabled Pool type, the seeded Run owner criterion with Filters enabled, ' +
  'and the search-window controls',
  async () => {
    await renderCluster(nodePoolManager);

    await click(screen.getByText('Create'));

    await waitFor(() => expect(screen.getByText('Pool type:')).toBeInTheDocument());
    expect(screen.queryByText('Mode:')).not.toBeInTheDocument();
    // Pool type is not locked by a single-mode caller: a ROLE_NODE_POOL_MANAGER's
    // forced-future-dated Create chooses it like anyone else.
    expect(selectIsDisabled('Pool type:')).toBe(false);
    // The creator's own run-owner criterion is seeded, editable, in an unlocked
    // Filters panel.
    expect(screen.getByText('Run owner')).toBeInTheDocument();
    expect(screen.getByText('user1')).toBeInTheDocument();
    expect(selectIsDisabled('Condition:')).toBe(false);
    expect(screen.getByRole('button', {name: 'Add filter'})).not.toBeDisabled();
    // The reservation's search-window controls render under their current names.
    expect(screen.getByText('Search from:')).toBeInTheDocument();
    expect(screen.getByText('Duration (days):')).toBeInTheDocument();

    // Closes the modal before the test ends: antd's Dialog only removes its
    // body-level portal node synchronously on unmount when `visible` is already
    // false — left open, it defers removal to a CSS transition's `afterClose`
    // that jsdom never fires, leaking this test's DOM into the next one.
    await click(screen.getByText('CANCEL'));
  });

test('admin: Create opens the modal in Default mode, with the Mode row shown', async () => {
  await renderCluster(admin);

  await click(screen.getByText('Create'));

  await waitFor(() => expect(within(
    screen.getByText('Mode:').closest('div')
  ).getByText('Default')).toBeInTheDocument());
  // Pool type applies to every pool, so it is offered in Default mode too.
  expect(screen.getByText('Pool type:')).toBeInTheDocument();

  await click(screen.getByText('CANCEL'));
});

test('a Default-mode pool shows no reservation badge', async () => {
  await renderCluster(admin);

  expect(screen.queryByText('Pending approval')).not.toBeInTheDocument();
  expect(screen.queryByText('Approved')).not.toBeInTheDocument();
});

async function renderClusterWithPool (pool, reservationsList = [], authenticatedUserInfo = admin) {
  const api = stubApi();
  api.reply(/^\/cluster\/pool\?/, [pool]);
  api.reply(/^\/cluster\/node\/loadAll\?/, []);
  api.reply(/^\/cluster\/capacity-reservation/, reservationsList);
  api.handle(/\/cluster\/instance\/allowed/, () => ({
    status: 'OK',
    payload: instanceAllowedPayload
  }));
  // No route for DELETE is registered here: the delete flow's own tests register
  // whichever response - success or failure - the specific test needs, since a route
  // baked in here would always win over one a test tries to add afterward (`stubApi`'s
  // `handlers.find` returns the first match, in registration order).
  renderWithStores(
    <HotCluster router={{push: fn()}} />,
    stores(authenticatedUserInfo)
  );
  await waitFor(() => expect(screen.getByText(pool.name)).toBeInTheDocument());
  await flushPromises();
  return {api};
}

test('a future-dated reservation pool with no granted window yet shows Pending approval',
  async () => {
    await renderClusterWithPool({
      ...myPool,
      id: 2,
      name: 'gpu-pool-pending',
      isCapacityReservation: true,
      startDate: undefined,
      endDate: undefined
    });

    expect(screen.getByText('Pending approval')).toBeInTheDocument();
    expect(screen.queryByText('Approved')).not.toBeInTheDocument();
  });

test('a future-dated reservation pool with a granted window shows Approved', async () => {
  await renderClusterWithPool({
    ...myPool,
    id: 3,
    name: 'gpu-pool-approved',
    isCapacityReservation: true,
    startDate: '2026-03-01 10:00:00.000',
    endDate: '2026-04-01 10:00:00.000'
  });

  expect(screen.getByText('Approved')).toBeInTheDocument();
  expect(screen.queryByText('Pending approval')).not.toBeInTheDocument();
});

test(
  'a future-dated pool with no nodes shows no (disabled) marker, unlike a Default pool',
  async () => {
    await renderClusterWithPool({
      ...myPool,
      id: 4,
      name: 'reservation-pool-no-nodes',
      count: 0,
      isCapacityReservation: true,
      startDate: undefined,
      endDate: undefined
    });

    // A future-dated pool legitimately has no nodes until its reservation is
    // activated, so it must not read as a fault - its reservation badge already
    // carries the real state.
    expect(screen.getByText('Pending approval')).toBeInTheDocument();
    expect(screen.queryByText('(disabled)')).not.toBeInTheDocument();
    expect(
      screen.getByText('reservation-pool-no-nodes').closest('.cp-hot-node-pool')
    ).not.toHaveClass('cp-disabled');
  }
);

test('a Default-mode pool with no nodes still shows (disabled)', async () => {
  await renderClusterWithPool({
    ...myPool,
    id: 5,
    name: 'default-pool-no-nodes',
    count: 0
  });

  expect(screen.getByText('(disabled)')).toBeInTheDocument();
  expect(
    screen.getByText('default-pool-no-nodes').closest('.cp-hot-node-pool')
  ).toHaveClass('cp-disabled');
});

test(
  'a future-dated pool with a reservation record shows instanceCount as effective total',
  async () => {
    const pool = {
      ...myPool,
      id: 6,
      name: 'reservation-pool-count',
      count: 0,
      isCapacityReservation: true,
      startDate: undefined,
      endDate: undefined
    };
    await renderClusterWithPool(pool, [
      {nodePoolId: 6, instanceCount: 4, status: 'PENDING'}
    ]);

    const card = screen.getByText('reservation-pool-count').closest('.cp-hot-node-pool');
    expect(within(card).getByText('4')).toBeInTheDocument();
    expect(screen.queryByText('(disabled)')).not.toBeInTheDocument();
  }
);

test(
  'a FINISHED reservation shows its badge but not its instanceCount as the card total',
  async () => {
    const pool = {
      ...myPool,
      id: 12,
      name: 'reservation-pool-finished',
      count: 0,
      isCapacityReservation: true,
      startDate: undefined,
      endDate: undefined
    };
    await renderClusterWithPool(pool, [
      {nodePoolId: 12, instanceCount: 4, status: 'FINISHED'}
    ]);

    const card = screen.getByText('reservation-pool-finished').closest('.cp-hot-node-pool');
    expect(screen.getByText('Finished')).toBeInTheDocument();
    // A finished reservation never again holds capacity, so its instanceCount must not
    // be shown as the card's total - unlike the live-reservation case above.
    expect(within(card).queryByText('4')).not.toBeInTheDocument();
  }
);

// The badge now shows the reservation record's own status, translated to the backend's
// ten-state `CapacityReservationStatus` lifecycle - not collapsed into the old two-bucket
// Approved/Pending approval reading.
function statusPool (id, name) {
  return {
    ...myPool,
    id,
    name,
    count: 0,
    isCapacityReservation: true,
    startDate: undefined,
    endDate: undefined
  };
}

test('badge status comes from reservation.status: SCHEDULED shows Scheduled', async () => {
  const pool = statusPool(7, 'status-scheduled');
  await renderClusterWithPool(pool, [
    {nodePoolId: 7, instanceCount: 2, status: 'SCHEDULED'}
  ]);

  expect(screen.getByText('Scheduled')).toBeInTheDocument();
  expect(screen.queryByText('Approved')).not.toBeInTheDocument();
  expect(screen.queryByText('Pending approval')).not.toBeInTheDocument();
});

test('badge status comes from reservation.status: ACTIVE shows Active', async () => {
  const pool = statusPool(8, 'status-active');
  await renderClusterWithPool(pool, [
    {nodePoolId: 8, instanceCount: 2, status: 'ACTIVE'}
  ]);

  expect(screen.getByText('Active')).toBeInTheDocument();
});

test('badge status comes from reservation.status: FAILED shows Failed', async () => {
  const pool = statusPool(9, 'status-failed');
  await renderClusterWithPool(pool, [
    {nodePoolId: 9, instanceCount: 2, status: 'FAILED'}
  ]);

  expect(screen.getByText('Failed')).toBeInTheDocument();
});

test(
  'badge status comes from reservation.status: REQUIRED_APPROVE shows Requires approval',
  async () => {
    const pool = statusPool(10, 'status-required-approve');
    await renderClusterWithPool(pool, [
      {nodePoolId: 10, instanceCount: 2, status: 'REQUIRED_APPROVE'}
    ]);

    expect(screen.getByText('Requires approval')).toBeInTheDocument();
  }
);

test(
  'an unknown reservation status renders a humanised label instead of crashing the card',
  async () => {
    const pool = statusPool(11, 'status-unknown');
    await renderClusterWithPool(pool, [
      {nodePoolId: 11, instanceCount: 2, status: 'SOME_NEW_STATUS'}
    ]);

    expect(screen.getByText('Some new status')).toBeInTheDocument();
  }
);

function deleteCallOf (api) {
  return api.calls.find(c => c.method === 'DELETE');
}

test('admin: deleting a Default pool needs no force', async () => {
  const pool = {...myPool, id: 20, name: 'to-delete-default'};
  const {api} = await renderClusterWithPool(pool);
  api.reply(/^\/cluster\/pool\/\d+\?force=/, pool);

  const card = screen.getByText('to-delete-default').closest('.cp-hot-node-pool');
  await click(card.querySelector('.anticon-delete').closest('button'));

  expect(screen.getByText('Are you sure you want to delete "to-delete-default" pool?'))
    .toBeInTheDocument();
  expect(screen.queryByText('Force deletion')).toBeNull();

  await click(screen.getByText('DELETE').closest('button'));
  await flushPromises();

  expect(deleteCallOf(api).url).toContain(`/cluster/pool/${pool.id}?force=false`);
  await waitFor(() => expect(
    screen.queryByText('Are you sure you want to delete "to-delete-default" pool?')
  ).not.toBeInTheDocument());
});

test(
  'admin: deleting a pool whose reservation is still open needs "Force deletion" checked',
  async () => {
    const pool = {
      ...myPool,
      id: 21,
      name: 'to-delete-active',
      isCapacityReservation: true,
      startDate: undefined,
      endDate: undefined
    };
    const {api} = await renderClusterWithPool(pool, [
      {nodePoolId: 21, instanceCount: 2, status: 'ACTIVE'}
    ]);
    api.reply(/^\/cluster\/pool\/\d+\?force=/, pool);

    const card = screen.getByText('to-delete-active').closest('.cp-hot-node-pool');
    await click(card.querySelector('.anticon-delete').closest('button'));

    expect(screen.getByText(/active and has not been given up yet/)).toBeInTheDocument();
    const okButton = screen.getByText('DELETE').closest('button');
    expect(okButton).toBeDisabled();

    await click(screen.getByText('Force deletion'));
    expect(okButton).not.toBeDisabled();

    await click(okButton);
    await flushPromises();

    expect(deleteCallOf(api).url).toContain(`/cluster/pool/${pool.id}?force=true`);
  }
);

test(
  'admin: deleting a pool whose reservation is REQUIRED_APPROVE needs no force',
  async () => {
    const pool = {
      ...myPool,
      id: 22,
      name: 'to-delete-required-approve',
      isCapacityReservation: true,
      startDate: undefined,
      endDate: undefined
    };
    const {api} = await renderClusterWithPool(pool, [
      {nodePoolId: 22, instanceCount: 2, status: 'REQUIRED_APPROVE'}
    ]);
    api.reply(/^\/cluster\/pool\/\d+\?force=/, pool);

    const card = screen.getByText('to-delete-required-approve').closest('.cp-hot-node-pool');
    await click(card.querySelector('.anticon-delete').closest('button'));

    expect(screen.queryByText('Force deletion')).toBeNull();
    const okButton = screen.getByText('DELETE').closest('button');
    expect(okButton).not.toBeDisabled();

    await click(okButton);
    await flushPromises();

    expect(deleteCallOf(api).url).toContain(`/cluster/pool/${pool.id}?force=false`);
  }
);

test(
  'NODE_POOL_MANAGER: the disclaimer applies to a pool it owns too, not just to admins',
  async () => {
    const pool = {
      ...myPool,
      id: 23,
      name: 'manager-owned-active',
      owner: 'USER1',
      isCapacityReservation: true,
      startDate: undefined,
      endDate: undefined
    };
    await renderClusterWithPool(
      pool,
      [{nodePoolId: 23, instanceCount: 2, status: 'ACTIVE'}],
      nodePoolManager
    );

    const card = screen.getByText('manager-owned-active').closest('.cp-hot-node-pool');
    expect(card.querySelectorAll('.anticon-delete').length).toBe(1);
    await click(card.querySelector('.anticon-delete').closest('button'));

    expect(screen.getByText(/active and has not been given up yet/)).toBeInTheDocument();
    expect(screen.getByText('DELETE').closest('button')).toBeDisabled();

    // Closes the modal before the test ends - see the identical note on the edit modal
    // above: left open, antd's Dialog leaks its portal node into whatever test runs next.
    await click(screen.getByText('CANCEL').closest('button'));
  }
);

test(
  'admin: a failed delete leaves the modal open, re-enables its controls, and reports the error',
  async () => {
    const pool = {...myPool, id: 24, name: 'to-delete-failing'};
    const {api} = await renderClusterWithPool(pool);
    api.fail(/^\/cluster\/pool\/\d+\?force=/, 'Pool is in use');

    const card = screen.getByText('to-delete-failing').closest('.cp-hot-node-pool');
    await click(card.querySelector('.anticon-delete').closest('button'));

    const okButton = screen.getByText('DELETE').closest('button');
    await click(okButton);
    await flushPromises();

    // `closeRemovePoolModal()` is only reached on the success path, so `poolToRemove`
    // is still set: the confirmation stays on screen rather than silently vanishing
    // alongside the error, and `operationWrapper`'s `finally` still resets `pending`,
    // so the button is clickable again rather than stuck mid-request.
    expect(screen.getByText('Are you sure you want to delete "to-delete-failing" pool?'))
      .toBeInTheDocument();
    expect(okButton).not.toBeDisabled();

    await click(screen.getByText('CANCEL').closest('button'));
  }
);
