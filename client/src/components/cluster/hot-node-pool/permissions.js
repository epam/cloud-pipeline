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

// `isAdmin` hands back the user's own `admin` field rather than a strict boolean, so this
// is coerced to keep every capability below a real boolean.
function hasBlanketManageRights (user) {
  return Boolean(isAdmin(user) || isRunAdmin(user));
}

// `NODE_POOL_CREATE`: ADMIN, RUN_ADMIN, or a NODE_POOL_MANAGER whose request carries a
// capacity reservation (`#vo.capacityReservationRequest != null`).
function canCreatePools (user) {
  return hasBlanketManageRights(user) || isNodePoolManager(user);
}

// Two distinct capabilities, not one boolean: a blanket manager handles every pool
// outright; a NODE_POOL_MANAGER only owns what it created (`canEditPool`/`canDeletePool`).
//
// `NodePool` accepts a per-pool WRITE/OWNER grant (`NODE_POOL_ID_WRITE`/`_OWNER`), not just
// ADMIN/RUN_ADMIN - but `GET /cluster/pool` sends no real per-pool mask yet.
//
// Every pool's `mask` is the all-permissions default regardless of the viewer's grant, so
// keying Edit/Delete off it would show both to anyone who can merely list the pool.
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

// `NODE_POOL_ID_WRITE`: ADMIN or RUN_ADMIN outright, or a per-pool WRITE grant - a
// NODE_POOL_MANAGER has none of its own, so ownership of the pool it created decides.
//
// `owner` is what this reads rather than `mask` - see the note on `getPoolPermissions`
// above for why the mask this endpoint sends cannot be trusted.
export function canEditPool (pool, authenticatedUserInfo) {
  const user = authenticatedUserInfo && authenticatedUserInfo.value;
  if (hasBlanketManageRights(user)) {
    return true;
  }
  return isNodePoolManager(user) && isPoolOwner(pool, user);
}

// `/report/pools` requires `ADMIN_OR_CLUSTER_READER` - ADMIN, RUN_ADMIN or
// ROLE_CLUSTER_READER, with no node-pool-manager exception.
//
// `cluster/index.js`'s own "Pools Usage" tab check reads `isClusterReader` alone, missing
// the RUN_ADMIN case this reads correctly - a pre-existing gap, not one to repeat.
export function canViewPoolUsage (authenticatedUserInfo) {
  const user = authenticatedUserInfo && authenticatedUserInfo.value;
  return isClusterReader(user) || isRunAdmin(user);
}

// `NODE_POOL_ID_OWNER`: ADMIN or RUN_ADMIN outright, or a per-pool OWNER grant - the same
// ownership `canEditPool` reads, since the backend sends no separate OWNER flag.
export function canDeletePool (pool, authenticatedUserInfo) {
  const user = authenticatedUserInfo && authenticatedUserInfo.value;
  if (hasBlanketManageRights(user)) {
    return true;
  }
  return isNodePoolManager(user) && isPoolOwner(pool, user);
}
