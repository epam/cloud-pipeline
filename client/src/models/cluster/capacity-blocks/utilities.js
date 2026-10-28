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
    // Coerced, not `===`: callers hold `regionId` as a number in some places
    // (`+this.capacityBlockRegionId`) and pass it straight through from a
    // run/payload object (a string, over the wire) in others.
    (regionId === undefined || regionId === null || `${pool.regionId}` === `${regionId}`)
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
 * The matched pool itself comes back as `pool`, so a caller never has to
 * look it up again.
 *
 * @param {string} value
 * @param {Object[]} [pools]
 * @returns {{instanceType: string|undefined, poolId: number|undefined, pool: Object|undefined}}
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
  return {instanceType: pool.instanceType, poolId: pool.id, pool};
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
      (regionId === undefined || regionId === null || `${pool.regionId}` === `${regionId}`) &&
      instanceTypeByName.has(pool.instanceType.toLowerCase())
    )
    .slice()
    .sort((a, b) => (a.id || 0) - (b.id || 0))
    .map((pool) => ({
      pool,
      instanceType: instanceTypeByName.get(pool.instanceType.toLowerCase())
    }));
}

/**
 * The pool-aware half of a node-type selector's own `correctInstanceTypeValue`:
 * given a raw field value that names a pool (`pool:<id>`), decides whether it
 * is still valid. `undefined` when `value` does not name a pool at all - a
 * plain instance type, or nonsense - which tells the caller to fall back to
 * its own plain-value correction (checked against whichever allowed-types
 * list that caller holds; callers differ on that, which is why this helper
 * does not do it itself).
 *
 * A pool's value is valid only while it is still among `getCapacityBlockOptions`
 * for the current allowed types and region - the same two conditions that put
 * it in the dropdown in the first place. When it is not (typically a region
 * switch dropping it), this falls back exactly the way a plain instance type
 * does when it leaves the allowed list: the plain instance type the pool
 * backed, if that is still allowed, otherwise `null` to clear the field. The
 * result is never the encoded `pool:<id>` form unless `value` already was -
 * this never promotes a plain value into one.
 *
 * @param {string} value
 * @param {Object[]} pools
 * @param {Object[]} instanceTypes
 * @param {Object} [options]
 * @param {number} [options.regionId]
 * @returns {string|null|undefined}
 */
export function correctCapacityBlockValue (value, pools, instanceTypes, options = {}) {
  const {instanceType: decodedType, poolId} = decodeInstanceTypeValue(value, pools);
  if (poolId === undefined) {
    return undefined;
  }
  const stillOffered = getCapacityBlockOptions(pools, instanceTypes, options)
    .some(({pool}) => pool && pool.id === poolId);
  if (stillOffered) {
    return value;
  }
  const lowerType = (decodedType || '').toLowerCase();
  const plain = (instanceTypes || [])
    .find((t) => t && typeof t.name === 'string' && t.name.toLowerCase() === lowerType);
  return plain ? plain.name : null;
}

/**
 * The raw value a node-type selector should be seeded with when the pool
 * behind it was already chosen elsewhere - the launch confirmation dialog's
 * own case, seeded from the payload the launch form built rather than from
 * any interaction with its own `Select`. Encodes `poolId` over `instanceType`
 * exactly as `encodeCapacityBlockValue` would, but only once that pool is
 * confirmed to still be in `pools` - the same check `decodeInstanceTypeValue`
 * already makes of an encoded value.
 *
 * Unlike `correctCapacityBlockValue`, this never clears the field: with no
 * `poolId`, or a pool no longer in `pools` (not loaded yet, or gone), the
 * result is `instanceType` itself, unchanged - exactly as if no pool were
 * known at all. A caller seeding a dialog from an instance type the user
 * already chose should never lose it merely because the pool behind it
 * could not be confirmed.
 *
 * @param {string} instanceType
 * @param {number|string} [poolId]
 * @param {Object[]} pools
 * @returns {string|undefined}
 */
export function seedInstanceTypeValue (instanceType, poolId, pools) {
  if (poolId === undefined || poolId === null) {
    return instanceType;
  }
  const encoded = encodeCapacityBlockValue({id: poolId});
  const {instanceType: resolved} = decodeInstanceTypeValue(encoded, pools);
  return resolved ? encoded : instanceType;
}
