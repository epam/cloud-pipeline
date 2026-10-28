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

import {autorun} from 'mobx';
import {stubApi} from '@test';
import capacityBlocks, {CapacityBlocks, isCapacityBlockRun} from './index';

const macrotask = () => new Promise((resolve) => setTimeout(resolve, 30));

// The store is a module-level singleton, so every test starts it off the same
// way: as if nothing had been fetched yet.
beforeEach(() => {
  capacityBlocks.invalidateCache();
});

test('reading .pools is lazy: it schedules a load, not firing one synchronously', async () => {
  const api = stubApi({
    '/cluster/pool/filter': [{id: 1, name: 'pool-1', instanceType: 'm5.large'}]
  });

  void capacityBlocks.pools;
  expect(api.calls).toHaveLength(0);

  // `.pools` schedules the load through a `setTimeout(fn, 0)`, mirroring
  // `Remote`'s own `value` getter - a real macrotask has to elapse, so a
  // microtask-only wait is not enough here.
  await new Promise((resolve) => setTimeout(resolve, 30));

  expect(api.calls).toHaveLength(1);
  expect(capacityBlocks.pools).toEqual([
    {id: 1, name: 'pool-1', instanceType: 'm5.large'}
  ]);
});

test('sends the fixed capacity-block filter', async () => {
  const api = stubApi({'/cluster/pool/filter': []});

  await capacityBlocks.load();

  expect(api.calls).toHaveLength(1);
  expect(JSON.parse(api.calls[0].body)).toEqual({
    isCapacityReservation: true,
    active: true
  });
});

test('one request serves two readers calling load() at once', async () => {
  const api = stubApi({
    '/cluster/pool/filter': [{id: 1, name: 'pool-1'}]
  });

  await Promise.all([capacityBlocks.load(), capacityBlocks.load()]);

  expect(api.calls).toHaveLength(1);
  expect(capacityBlocks.pools).toEqual([{id: 1, name: 'pool-1'}]);
});

test('invalidateCache() makes the next load() refetch', async () => {
  stubApi({'/cluster/pool/filter': [{id: 1, name: 'pool-1'}]});
  await capacityBlocks.load();
  expect(capacityBlocks.pools).toEqual([{id: 1, name: 'pool-1'}]);

  const api = stubApi({'/cluster/pool/filter': [{id: 2, name: 'pool-2'}]});
  capacityBlocks.invalidateCache();
  await capacityBlocks.load();

  expect(api.calls).toHaveLength(1);
  expect(capacityBlocks.pools).toEqual([{id: 2, name: 'pool-2'}]);
});

test('without invalidateCache(), load() does not fetch again once loaded', async () => {
  const first = stubApi({'/cluster/pool/filter': [{id: 1, name: 'pool-1'}]});
  await capacityBlocks.load();
  expect(first.calls).toHaveLength(1);

  const second = stubApi({'/cluster/pool/filter': [{id: 2, name: 'pool-2'}]});
  await capacityBlocks.load();

  expect(second.calls).toHaveLength(0);
  expect(capacityBlocks.pools).toEqual([{id: 1, name: 'pool-1'}]);
});

test('a failed first request leaves pools empty rather than throwing', async () => {
  // A fresh store: the shared one still holds a previous test's list, which a
  // failure is meant to keep.
  const store = new CapacityBlocks();
  stubApi().fail('/cluster/pool/filter', 'Something went wrong');

  await store.load();

  expect(store.pools).toEqual([]);
});

test('refresh() refetches, and an observer of .pools sees the new list', async () => {
  stubApi({'/cluster/pool/filter': [{id: 1, name: 'pool-1'}]});
  await capacityBlocks.load();
  const seen = [];
  const dispose = autorun(() => seen.push(capacityBlocks.pools.map((pool) => pool.id)));

  const api = stubApi({'/cluster/pool/filter': [{id: 2, name: 'pool-2'}]});
  await capacityBlocks.refresh();
  dispose();

  expect(api.calls).toHaveLength(1);
  expect(seen[seen.length - 1]).toEqual([2]);
});

test('refresh() while a load is in flight still sends its own request', async () => {
  const api = stubApi({'/cluster/pool/filter': [{id: 1, name: 'pool-1'}]});

  const first = capacityBlocks.load();
  const second = capacityBlocks.refresh();
  await Promise.all([first, second]);

  expect(api.calls).toHaveLength(2);
});

test('a failed request is retried by the next load()', async () => {
  const failing = stubApi().fail('/cluster/pool/filter', 'Something went wrong');
  await capacityBlocks.load();
  expect(failing.calls).toHaveLength(1);

  const api = stubApi({'/cluster/pool/filter': [{id: 1, name: 'pool-1'}]});
  await capacityBlocks.load();

  expect(api.calls).toHaveLength(1);
  expect(capacityBlocks.pools).toEqual([{id: 1, name: 'pool-1'}]);
});

test('a failed request does not retry in a loop while .pools is observed', async () => {
  const api = stubApi().fail('/cluster/pool/filter', 'Something went wrong');
  const dispose = autorun(() => capacityBlocks.pools);

  await macrotask();
  await macrotask();
  dispose();

  expect(api.calls).toHaveLength(1);
});

test('load() during an in-flight refresh() shares it instead of sending again', async () => {
  const api = stubApi({'/cluster/pool/filter': [{id: 1, name: 'pool-1'}]});

  await Promise.all([capacityBlocks.refresh(), capacityBlocks.load()]);

  expect(api.calls).toHaveLength(1);
});

test('a refresh that fails in the response keeps the last list', async () => {
  stubApi({'/cluster/pool/filter': [{id: 1, name: 'pool-1'}]});
  await capacityBlocks.refresh();

  stubApi().fail('/cluster/pool/filter', 'Something went wrong');
  await capacityBlocks.refresh();

  expect(capacityBlocks.pools).toEqual([{id: 1, name: 'pool-1'}]);
});

test('a refresh that fails in transport keeps the last list', async () => {
  stubApi({'/cluster/pool/filter': [{id: 1, name: 'pool-1'}]});
  await capacityBlocks.refresh();

  stubApi().status('/cluster/pool/filter', 500);
  await capacityBlocks.refresh();

  expect(capacityBlocks.pools).toEqual([{id: 1, name: 'pool-1'}]);
});

describe('isCapacityBlockRun', () => {
  const run = (instance, pipelineRunParameters) => ({instance, pipelineRunParameters});

  it('is true for a run on a pool node', () => {
    expect(isCapacityBlockRun(run({nodeType: 'm5.large', poolId: 5}))).toBe(true);
  });

  it('is false for a run whose poolId is 0, as the API sends for no pool', () => {
    expect(isCapacityBlockRun(run({nodeType: 'm5.large', poolId: 0}))).toBe(false);
  });

  it('is true for a run with any of the reservation request parameters', () => {
    for (const name of ['CP_CAP_REQUESTS_CPU', 'CP_CAP_REQUESTS_GPU', 'CP_CAP_REQUESTS_RAM']) {
      expect(isCapacityBlockRun(run({poolId: 0}, [{name, value: '1'}]))).toBe(true);
    }
  });

  it('is false for a run with other parameters only, or none', () => {
    expect(isCapacityBlockRun(run({poolId: 0}, [{name: 'CP_CAP_GPU', value: '1'}]))).toBe(false);
    expect(isCapacityBlockRun(run({}))).toBe(false);
    expect(isCapacityBlockRun(undefined)).toBe(false);
  });
});
