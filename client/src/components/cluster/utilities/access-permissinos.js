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

import roleModel from '../../../utils/roleModel';

const ROLE_CLUSTER_READER = 'ROLE_CLUSTER_READER';

export function isAdmin (user) {
  if (user) {
    return user.admin;
  }
  return false;
}

export function isClusterReader (user) {
  if (isAdmin(user)) {
    return true;
  }
  if (user) {
    const {roles = []} = user;
    return roles.some((role) => role.name.toLowerCase() === ROLE_CLUSTER_READER.toLowerCase());
  }
  return false;
}

// `roleModel.userIs.*` resolves through `Array.find`, returning the matching role object
// rather than a strict boolean - coerced here so these read as real booleans. Two quirks
// of `userHasRole` underneath are worth knowing before relying on either alone: it
// answers `true` for an admin that carries at least one role of any kind, and `false` for
// any user whose `roles` is empty, admin or not. Both are why the admin case belongs on
// `isAdmin` rather than on these.
export function isNodePoolManager (user) {
  return Boolean(roleModel.userIs.nodePoolManager(user));
}

export function isRunAdmin (user) {
  return Boolean(roleModel.userIs.runAdmin(user));
}

// Who may open the "Hot Node Pools" tab. `NodePoolApiService.loadAll` post-filters on
// `NODE_POOL_READ_FILTER` - ADMIN, RUN_ADMIN, ROLE_CLUSTER_READER, or a per-pool READ
// grant - and `NODE_POOL_CREATE` additionally admits a ROLE_NODE_POOL_MANAGER whose
// request carries a capacity reservation. So a ROLE_NODE_POOL_MANAGER and a non-admin
// ROLE_RUN_ADMIN both have something to do on that tab, and must be able to reach it at
// all: the former to create a future dated pool, the latter to manage every pool, which
// `hot-node-pool/permissions.js` already grants it.
//
// RUN_ADMIN is named here rather than folded into `isClusterReader` - which the backend's
// ADMIN_OR_CLUSTER_READER does admit - because that helper also gates the "Core nodes"
// page and the "Pools Usage" tab, and neither is this tab's question.
//
// `POST /report/pools`, which the "Pools Usage" tab reads, is ADMIN_OR_CLUSTER_READER
// with no node-pool-manager exception, so that tab deliberately keeps using
// `isClusterReader`.
export function canReadNodePools (user) {
  return isClusterReader(user) || isRunAdmin(user) || isNodePoolManager(user);
}
