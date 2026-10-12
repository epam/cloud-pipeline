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
const unloaded = {loaded: false, pending: false, value: undefined};

// `pools`/`clusterNodes` are module singletons `HotCluster` injects by name, so a
// previous test's resolved value would otherwise leak into the next one.
beforeEach(() => {
  pools.invalidateCache();
  clusterNodes.invalidateCache();
});

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

test('non-admin: Create is present, no edit/remove on the card, click does nothing', async () => {
  const router = {push: fn()};
  await renderCluster(nonAdmin, {router});

  expect(screen.getByText('Create')).toBeInTheDocument();
  const card = screen.getByText('my-pool').closest('.cp-hot-node-pool');
  // Only the "usage" (area-chart) button remains when readOnly hides edit/remove.
  expect(card.querySelectorAll('button').length).toBe(1);

  await click(card);

  expect(router.push).not.toHaveBeenCalled();
});

test('unloaded: no Create button', async () => {
  await renderCluster(unloaded);

  expect(screen.queryByText('Create')).not.toBeInTheDocument();
});

test('non-admin: Create opens the modal forced into GPU mode, with no Mode row', async () => {
  await renderCluster(nonAdmin);

  await click(screen.getByText('Create'));

  await waitFor(() => expect(screen.getByText('Pool type:')).toBeInTheDocument());
  expect(screen.queryByText('Mode:')).not.toBeInTheDocument();

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
  expect(screen.queryByText('Pool type:')).not.toBeInTheDocument();

  await click(screen.getByText('CANCEL'));
});
