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
  isAdmin,
  isClusterReader,
  isRunAdmin,
  isNodePoolManager
} from '../utilities/access-permissions';

// `isAdmin` returns the user's raw `admin` field, not a strict boolean.
function hasBlanketManageRights (user) {
  return Boolean(isAdmin(user) || isRunAdmin(user));
}

// `NODE_POOL_CREATE`: ADMIN, RUN_ADMIN, or a NODE_POOL_MANAGER whose request carries a
// capacity reservation (`#vo.capacityReservationRequest != null`).
function canCreatePools (user) {
  return hasBlanketManageRights(user) || isNodePoolManager(user);
}

// Two distinct capabilities, not one: a blanket manager handles every pool outright, while a
// NODE_POOL_MANAGER can create but only owns what it created (`canEditPool`/`canDeletePool`).
export function getPoolPermissions (authenticatedUserInfo) {
  const loaded = !!authenticatedUserInfo &&
    !!authenticatedUserInfo.loaded &&
    !!authenticatedUserInfo.value;
  if (!loaded) {
    return {
      canManagePools: false,
      canCreateAnyMode: false,
      canCreate: false,
      readOnly: true
    };
  }
  const user = authenticatedUserInfo.value;
  const blanketManager = hasBlanketManageRights(user);
  const create = canCreatePools(user);
  return {
    canManagePools: blanketManager, // open / edit / remove any pool outright
    canCreateAnyMode: blanketManager, // choose among Default, future-dated and ML Block
    canCreate: create,
    readOnly: !blanketManager
  };
}

function isPoolOwner (pool, user) {
  const {owner} = pool || {};
  const {userName} = user || {};
  if (!owner || !userName) {
    return false;
  }
  return `${owner}`.toLowerCase() === `${userName}`.toLowerCase();
}

// `NODE_POOL_ID_WRITE`: ADMIN or RUN_ADMIN outright, or a per-pool WRITE grant. A
// NODE_POOL_MANAGER has none, so ownership of the pool it created is what decides.
//
// `owner` rather than `mask`: `GET /cluster/pool` sends every pool's `mask` as the
// all-permissions default whatever the viewer's grant, so the mask cannot be trusted.
export function canEditPool (pool, authenticatedUserInfo) {
  const user = authenticatedUserInfo && authenticatedUserInfo.value;
  if (hasBlanketManageRights(user)) {
    return true;
  }
  return isNodePoolManager(user) && isPoolOwner(pool, user);
}

// `/report/pools` requires `ADMIN_OR_CLUSTER_READER`: ADMIN, RUN_ADMIN or ROLE_CLUSTER_READER,
// with no node-pool-manager exception. `cluster/index.js`'s tab check misses RUN_ADMIN.
export function canViewPoolUsage (authenticatedUserInfo) {
  const user = authenticatedUserInfo && authenticatedUserInfo.value;
  return isClusterReader(user) || isRunAdmin(user);
}

// `NODE_POOL_ID_OWNER`: the backend sends no separate OWNER flag, so this reads the same
// ownership `canEditPool` does.
export function canDeletePool (pool, authenticatedUserInfo) {
  const user = authenticatedUserInfo && authenticatedUserInfo.value;
  if (hasBlanketManageRights(user)) {
    return true;
  }
  return isNodePoolManager(user) && isPoolOwner(pool, user);
}
