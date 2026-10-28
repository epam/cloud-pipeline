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

import {action, computed, observable} from 'mobx';
import HotNodePoolsFilter from '../HotNodePoolsFilter';
import {findCapacityBlockPool} from './utilities';

// The one filter the client sends: every pool backing an active capacity
// reservation. Region is left out on purpose - the launch form's region
// changes while the form stays open, and the whole list is small enough that
// filtering by region is done client-side instead of re-requesting per region.
const CAPACITY_BLOCKS_FILTER = {
  isCapacityReservation: true,
  active: true
};

/**
 * `HotNodePoolsFilter` is a `RemotePost`, which - unlike `Remote` - has no lazy
 * fetch of its own: nothing schedules a request just because something read
 * `.value`. This wraps one request in exactly that laziness, the way `Remote`'s
 * own `value` getter works, so a component can read `capacityBlocks.pools`
 * from `render` and get a load scheduled for it, re-rendering once the list
 * arrives, instead of every caller remembering to kick off a fetch itself.
 *
 * The list is kept in its own observable field, because `RemotePost.value` is
 * not observable and `loaded` stays `true` across a second request: without
 * it, a reload would never re-render anything.
 */
class CapacityBlocks {
  request = new HotNodePoolsFilter();

  @observable.ref _pools = [];

  _loadRequired = true;
  _loadFailed = false;
  _loadPromise = null;

  @computed
  get pools () {
    const fetchIfNeeded = this._fetchIfNeeded.bind(this);
    setTimeout(fetchIfNeeded, 0);
    return this._pools;
  }

  get loaded () {
    return this.request.loaded;
  }

  async _fetchIfNeeded () {
    if (this._loadRequired) {
      await this.load();
    }
  }

  /**
   * Fetches the pools, unless a request is already in flight or has already
   * succeeded - two readers calling this (or reading `.pools`) at once share
   * the one request. `invalidateCache()` is what makes the next call fetch
   * again. After a failed request, the next `load()` fetches again too - but
   * a lazy read of `.pools` does not, so a failing API is not asked again on
   * every render.
   * @returns {Promise}
   */
  load () {
    if (this._loadRequired || this._loadFailed) {
      this._queueRequest();
    }
    return this._loadPromise || Promise.resolve();
  }

  /**
   * Fetches the pools again now - after a pool is created, changed or
   * removed, or when a launch form opens.
   * @returns {Promise}
   */
  refresh () {
    this._queueRequest();
    return this._loadPromise;
  }

  invalidateCache () {
    this._loadRequired = true;
  }

  // `RemotePost.send` ignores a call made while a request is in flight, so a
  // new request waits for the previous one instead of being dropped - even
  // one that failed.
  _queueRequest () {
    this._loadRequired = false;
    this._loadFailed = false;
    const previous = this._loadPromise || Promise.resolve();
    this._loadPromise = previous
      .catch(() => {})
      .then(() => this.request.send(CAPACITY_BLOCKS_FILTER))
      .then(() => this._onLoaded());
  }

  @action
  _onLoaded () {
    // A failed request keeps the last list, and is retried by the next
    // `load()` instead of leaving capacity blocks off for the session.
    this._loadFailed = !!this.request.error;
    if (!this._loadFailed) {
      this._pools = this.request.value || [];
    }
  }
}

const capacityBlocks = new CapacityBlocks();

/**
 * True when a pool backs `instanceType` - with or without a `launchConfig`.
 * Drives the pool-name tag, the suggestion block and hiding the price
 * estimate: a capacity block changes those even when it injects nothing into
 * the launch payload.
 *
 * @param {string} instanceType
 * @param {Object} [options]
 * @param {number} [options.regionId]
 * @returns {boolean}
 */
export function isCapacityBlockInstanceType (instanceType, options) {
  return !!findCapacityBlockPool(capacityBlocks.pools, instanceType, options);
}

/**
 * The pool's `launchConfig` for `instanceType`, camelCase, as the backend
 * sends it - or `undefined` when no matching pool carries one. Drives the
 * reservation-requests panel, the launch payload and `podAssignPolicy`.
 *
 * @param {string} instanceType
 * @param {Object} [options]
 * @param {number} [options.regionId]
 * @returns {Object|undefined}
 */
export function findCapacityBlockConfig (instanceType, options) {
  const pool = findCapacityBlockPool(capacityBlocks.pools, instanceType, options);
  return pool && pool.launchConfig ? pool.launchConfig : undefined;
}

export {CapacityBlocks};
export default capacityBlocks;
