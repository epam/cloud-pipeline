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
