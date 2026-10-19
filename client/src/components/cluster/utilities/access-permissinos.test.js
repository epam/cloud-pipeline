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

import {
  isClusterReader,
  isRunAdmin,
  isNodePoolManager,
  canReadNodePools
} from './access-permissinos';

const plainUser = {admin: false, roles: []};
const clusterReader = {admin: false, roles: [{name: 'ROLE_CLUSTER_READER'}]};
const nodePoolManager = {admin: false, roles: [{name: 'ROLE_NODE_POOL_MANAGER'}]};
const runAdmin = {admin: false, roles: [{name: 'ROLE_RUN_ADMIN'}]};

describe('isNodePoolManager', () => {
  test('a NODE_POOL_MANAGER is one', () => {
    expect(isNodePoolManager(nodePoolManager)).toBe(true);
  });

  test('a plain user, a cluster reader and an unloaded user are not', () => {
    expect(isNodePoolManager(plainUser)).toBe(false);
    expect(isNodePoolManager(clusterReader)).toBe(false);
    expect(isNodePoolManager(undefined)).toBe(false);
  });
});

describe('isRunAdmin', () => {
  test('a RUN_ADMIN is one, and nobody else is', () => {
    expect(isRunAdmin(runAdmin)).toBe(true);
    expect(isRunAdmin(nodePoolManager)).toBe(false);
    expect(isRunAdmin(plainUser)).toBe(false);
    expect(isRunAdmin(undefined)).toBe(false);
  });
});

// The "Hot Node Pools" tab's gate - see the comment on `canReadNodePools` for the
// backend ACL it mirrors, and for what it deliberately leaves out.
describe('canReadNodePools', () => {
  // `isNodePoolManager` alone answers `false` for an admin carrying no roles, so this
  // case rests entirely on `isClusterReader` being evaluated first.
  test('an admin may, with or without roles of their own', () => {
    expect(canReadNodePools({admin: true, roles: []})).toBe(true);
    expect(canReadNodePools({admin: true, roles: [{name: 'ROLE_CLUSTER_READER'}]})).toBe(true);
  });

  test('a cluster reader may', () => {
    expect(canReadNodePools(clusterReader)).toBe(true);
  });

  test('a NODE_POOL_MANAGER may, without being a cluster reader', () => {
    expect(canReadNodePools(nodePoolManager)).toBe(true);
    expect(isClusterReader(nodePoolManager)).toBe(false);
  });

  test('a RUN_ADMIN may, without being a cluster reader', () => {
    expect(canReadNodePools(runAdmin)).toBe(true);
    expect(isClusterReader(runAdmin)).toBe(false);
  });

  test('a plain user and an unloaded user may not', () => {
    expect(canReadNodePools(plainUser)).toBe(false);
    expect(canReadNodePools(undefined)).toBe(false);
  });
});
