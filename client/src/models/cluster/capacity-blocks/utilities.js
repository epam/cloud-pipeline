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

// Pure helpers over the node pool list `capacity-blocks/index.js` fetches - no
// network, no mobx, which is why the tests for this file do their work.

/**
 * Finds the pool backing `instanceType`, matched exactly and case-insensitively.
 * `regionId`, when given, must match too; when it is not given, a pool is not
 * filtered by region at all. Several pools can match one instance type (and
 * region) at once - the one with a `launchConfig` wins, then the one with the
 * lowest `id`, so the choice stays stable across renders rather than depending
 * on the array's own order.
 *
 * @param {Object[]} pools
 * @param {string} instanceType
 * @param {Object} [options]
 * @param {number} [options.regionId]
 * @returns {Object|undefined} the matching pool, or `undefined`
 */
export function findCapacityBlockPool (pools, instanceType, options = {}) {
  const {regionId} = options || {};
  if (!instanceType || !Array.isArray(pools) || !pools.length) {
    return undefined;
  }
  const type = instanceType.toLowerCase();
  const matched = pools.filter((pool) => pool &&
    typeof pool.instanceType === 'string' &&
    pool.instanceType.toLowerCase() === type &&
    (regionId === undefined || regionId === null || pool.regionId === regionId)
  );
  if (!matched.length) {
    return undefined;
  }
  const withConfigFirst = (pool) => (pool.launchConfig ? 0 : 1);
  return matched
    .slice()
    .sort((a, b) => {
      const configOrder = withConfigFirst(a) - withConfigFirst(b);
      if (configOrder !== 0) {
        return configOrder;
      }
      return (a.id || 0) - (b.id || 0);
    })[0];
}

/**
 * Every `additionalParameters` key across all pools, deduped. This is the
 * successor to the preference's `parameters`: a pool with no `launchConfig`,
 * or no `additionalParameters` on it, contributes nothing.
 *
 * @param {Object[]} pools
 * @returns {string[]}
 */
export function getCapacityBlockAdditionalParameterNames (pools) {
  if (!Array.isArray(pools)) {
    return [];
  }
  const names = new Set();
  pools.forEach((pool) => {
    const additionalParameters = pool &&
      pool.launchConfig &&
      pool.launchConfig.additionalParameters;
    if (additionalParameters && typeof additionalParameters === 'object') {
      Object.keys(additionalParameters).forEach((name) => names.add(name));
    }
  });
  return [...names];
}

// The whole contract between a node-type selector and the readers of its
// value: a capacity-block option's value is this prefix plus the pool's id,
// and a plain instance type's value is its own name - never this prefix.
const CAPACITY_BLOCK_VALUE_PREFIX = 'pool:';

/**
 * Encodes `pool` as the value a capacity-block select option carries. Picking
 * it must mean *this pool*, not just the instance type it backs - two pools
 * can serve one instance type with different launch configurations.
 *
 * @param {Object} pool
 * @returns {string|undefined} `undefined` when `pool` has no usable `id`
 */
export function encodeCapacityBlockValue (pool) {
  if (!pool || pool.id === undefined || pool.id === null) {
    return undefined;
  }
  return `${CAPACITY_BLOCK_VALUE_PREFIX}${pool.id}`;
}

/**
 * The inverse of `encodeCapacityBlockValue`, and the only place a node-type
 * selector's raw value should be decoded. A plain instance type name decodes
 * to itself, with no `poolId`. An encoded value decodes to the `poolId` it
 * names plus the `instanceType` that pool backs - resolved against `pools`,
 * since the value alone does not carry it. Nonsense (not a string, an empty
 * value, a `pool:` with no numeric id, or an id no longer in `pools`) decodes
 * to `{instanceType: undefined, poolId: undefined}` rather than throwing.
 *
 * @param {string} value
 * @param {Object[]} [pools]
 * @returns {{instanceType: string|undefined, poolId: number|undefined}}
 */
export function decodeInstanceTypeValue (value, pools = []) {
  if (!value || typeof value !== 'string') {
    return {instanceType: undefined, poolId: undefined};
  }
  if (!value.startsWith(CAPACITY_BLOCK_VALUE_PREFIX)) {
    return {instanceType: value, poolId: undefined};
  }
  const idPart = value.slice(CAPACITY_BLOCK_VALUE_PREFIX.length);
  const poolId = idPart ? Number(idPart) : NaN;
  if (!idPart || Number.isNaN(poolId)) {
    return {instanceType: undefined, poolId: undefined};
  }
  const pool = (Array.isArray(pools) ? pools : [])
    .find((aPool) => aPool && aPool.id === poolId);
  if (!pool) {
    return {instanceType: undefined, poolId: undefined};
  }
  return {instanceType: pool.instanceType, poolId: pool.id};
}

/**
 * The pools worth offering as capacity-block options for a node-type
 * selector: region-matched (when `regionId` is given) and backing an
 * instance type that is actually allowed (`instanceTypes`, matched
 * case-insensitively by name) - the allowed list is the authority on what
 * this user may launch, and a pool cannot widen it. Two pools backing the
 * same instance type both appear, once each: the selector lists one option
 * per pool, not per type, and the user is meant to choose between them.
 * Ordered by the pool's `id` so the dropdown's order does not reshuffle
 * between renders.
 *
 * @param {Object[]} pools
 * @param {Object[]} instanceTypes
 * @param {Object} [options]
 * @param {number} [options.regionId]
 * @returns {{pool: Object, instanceType: Object}[]}
 */
export function getCapacityBlockOptions (pools, instanceTypes, options = {}) {
  const {regionId} = options || {};
  if (
    !Array.isArray(pools) || !pools.length ||
    !Array.isArray(instanceTypes) || !instanceTypes.length
  ) {
    return [];
  }
  const instanceTypeByName = new Map();
  instanceTypes.forEach((instanceType) => {
    if (instanceType && typeof instanceType.name === 'string') {
      instanceTypeByName.set(instanceType.name.toLowerCase(), instanceType);
    }
  });
  return pools
    .filter((pool) => pool &&
      typeof pool.instanceType === 'string' &&
      (regionId === undefined || regionId === null || pool.regionId === regionId) &&
      instanceTypeByName.has(pool.instanceType.toLowerCase())
    )
    .slice()
    .sort((a, b) => (a.id || 0) - (b.id || 0))
    .map((pool) => ({
      pool,
      instanceType: instanceTypeByName.get(pool.instanceType.toLowerCase())
    }));
}
