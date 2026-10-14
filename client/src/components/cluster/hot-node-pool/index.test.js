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

// `pools`/`clusterNodes` are module singletons `HotCluster` injects by name, so a
// previous test's resolved value would otherwise leak into the next one.
beforeEach(() => {
  pools.invalidateCache();
  clusterNodes.invalidateCache();
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

async function renderCluster (authenticatedUserInfo, props = {}) {
  const api = stubApi();
  api.reply(/^\/cluster\/pool\?/, [myPool]);
  api.reply(/^\/cluster\/node\/loadAll\?/, []);
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
    // Only the "usage" (area-chart) button remains when canEdit/canDelete are both false.
    expect(card.querySelectorAll('button').length).toBe(1);

    await click(card);

    expect(router.push).not.toHaveBeenCalled();
  });

test('unloaded: no Create button', async () => {
  await renderCluster(unloaded);

  expect(screen.queryByText('Create')).not.toBeInTheDocument();
});

test('NODE_POOL_MANAGER: Create is present, no edit/remove on the card, click does nothing',
  async () => {
    const router = {push: fn()};
    await renderCluster(nodePoolManager, {router});

    expect(screen.getByText('Create')).toBeInTheDocument();
    const card = screen.getByText('my-pool').closest('.cp-hot-node-pool');
    // Only the "usage" (area-chart) button remains: a NODE_POOL_MANAGER may create a
    // reservation pool, but holds no WRITE/OWNER grant on this pre-existing one.
    expect(card.querySelectorAll('button').length).toBe(1);

    await click(card);

    expect(router.push).not.toHaveBeenCalled();
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

async function renderClusterWithPool (pool) {
  const api = stubApi();
  api.reply(/^\/cluster\/pool\?/, [pool]);
  api.reply(/^\/cluster\/node\/loadAll\?/, []);
  api.handle(/\/cluster\/instance\/allowed/, () => ({
    status: 'OK',
    payload: instanceAllowedPayload
  }));
  renderWithStores(
    <HotCluster router={{push: fn()}} />,
    stores(admin)
  );
  await waitFor(() => expect(screen.getByText(pool.name)).toBeInTheDocument());
  await flushPromises();
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
