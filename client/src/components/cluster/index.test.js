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
import {renderWithRouter, screen} from '@test';
import ClusterRoot from './index';

const info = (value) => observable({loaded: true, pending: false, value});

const admin = info({admin: true});
const plainUser = info({admin: false, roles: []});
const clusterReader = info({admin: false, roles: [{name: 'ROLE_CLUSTER_READER'}]});
const nodePoolManager = info({admin: false, roles: [{name: 'ROLE_NODE_POOL_MANAGER'}]});
const runAdmin = info({admin: false, roles: [{name: 'ROLE_RUN_ADMIN'}]});

const everyTab = ['All nodes', 'Cloud nodes', 'Core nodes', 'Hot Node Pools', 'Pools Usage'];

function renderNavigation (authenticatedUserInfo) {
  return renderWithRouter(
    <ClusterRoot />,
    {
      route: '/cluster/hot',
      path: '/cluster/hot',
      stores: {authenticatedUserInfo}
    }
  );
}

function visibleTabs () {
  return everyTab.filter(title => screen.queryByText(title) !== null);
}

test('an admin sees every tab', () => {
  renderNavigation(admin);
  expect(visibleTabs()).toEqual(everyTab);
});

test('a cluster reader sees the pool tabs, not the admin-only node tabs', () => {
  renderNavigation(clusterReader);
  expect(visibleTabs()).toEqual(['All nodes', 'Hot Node Pools', 'Pools Usage']);
});

// The role may create a future dated pool, but "+ Create" lives on the "Hot Node Pools"
// tab, which was gated on `isClusterReader` and so unreachable for it. "Pools Usage"
// stays hidden - `canReadNodePools` carries both backend ACLs.
test('a NODE_POOL_MANAGER sees the Hot Node Pools tab, and not Pools Usage', () => {
  renderNavigation(nodePoolManager);
  expect(visibleTabs()).toEqual(['All nodes', 'Hot Node Pools']);
});

// A non-admin RUN_ADMIN manages every pool (`hot-node-pool/permissions.js`) and was
// shown no tab to manage one from. "Pools Usage" would answer it, but stays on
// `isClusterReader` - see the comment on `canReadNodePools`.
test('a RUN_ADMIN sees the Hot Node Pools tab', () => {
  renderNavigation(runAdmin);
  expect(visibleTabs()).toEqual(['All nodes', 'Hot Node Pools']);
});

// One tab is no navigation: `renderClusterNavigation` renders nothing below two, so a
// plain user gets the node list with no menu above it, as before.
test('a plain user gets no navigation at all', () => {
  renderNavigation(plainUser);
  expect(visibleTabs()).toEqual([]);
});

test('an unloaded user gets no navigation at all', () => {
  renderNavigation(observable({loaded: false, pending: false, value: undefined}));
  expect(visibleTabs()).toEqual([]);
});
