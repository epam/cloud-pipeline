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

import {stubApi, flushPromises} from '@test';
import Remote from './Remote';
import {authorization} from './Authorization';

class TestRemote extends Remote {
  constructor (url = '/test') {
    super();
    this.url = url;
  }
}

class AutoTestRemote extends Remote {
  static auto = true;
  constructor (url = '/test') {
    super();
    this.url = url;
  }
}

beforeEach(() => {
  authorization.setAuthorized(true);
});

test('reading .value is lazy: it schedules a fetch, not firing one synchronously', async () => {
  const api = stubApi({'/test': {id: 1}});
  const model = new TestRemote();

  void model.value;
  expect(api.calls).toHaveLength(0);

  // The getters trigger the fetch through a `setTimeout(fn, 0)`, so waiting on
  // a microtask queue (flushPromises) is not enough here; a real macrotask has
  // to elapse.
  await new Promise(resolve => setTimeout(resolve, 30));
  expect(api.calls).toHaveLength(1);
  expect(model.value).toEqual({id: 1});
  expect(model.loaded).toBe(true);
});

test('static auto = true fetches in the constructor, with no property read needed', async () => {
  const api = stubApi({'/test': {id: 2}});
  const model = new AutoTestRemote();

  await flushPromises();
  expect(api.calls).toHaveLength(1);
  expect(model.value).toEqual({id: 2});
});

test('postprocess(value) returns value.payload by default', async () => {
  stubApi({'/test': {name: 'root'}});
  const model = new TestRemote();

  await model.fetch();
  expect(model.value).toEqual({name: 'root'});
});

test('update(): a non-OK status fails the model without throwing', async () => {
  stubApi().fail('/test', 'No such folder');
  const model = new TestRemote();

  await model.fetch();
  expect(model.failed).toBe(true);
  expect(model.error).toBe('No such folder');
  expect(model.loaded).toBe(false);
});

test('update(): a 401 logs the user out, but leaves .loaded at its previous value', async () => {
  stubApi({'/test': {id: 4}});
  const model = new TestRemote();
  await model.fetch();
  expect(model.loaded).toBe(true);

  const api = stubApi();
  api.envelope('/test', {status: 401, message: 'Unauthorized'});
  model.invalidateCache();
  await model.fetch();

  expect(model.failed).toBe(true);
  expect(model.error).toBe('Unauthorized');
  expect(authorization.isAuthorized()).toBe(false);
  // Quirk: unlike the OK and non-OK branches, the 401 branch in update() never
  // assigns `_loaded`, so it survives from the fetch before this one.
  expect(model.loaded).toBe(true);
});

test('invalidateCache() marks for refetch; fetchIfNeededOrWait() alone will not', async () => {
  const api = stubApi({'/test': {id: 5}});
  const model = new TestRemote();

  await model.fetchIfNeededOrWait();
  expect(api.calls).toHaveLength(1);

  await model.fetchIfNeededOrWait();
  expect(api.calls).toHaveLength(1);

  model.invalidateCache();
  await model.fetchIfNeededOrWait();
  expect(api.calls).toHaveLength(2);
});

test('fetchIfNeededOrWait() called twice while in flight only fetches once', async () => {
  const api = stubApi({'/test': {id: 6}});
  const model = new TestRemote();

  await Promise.all([
    model.fetchIfNeededOrWait(),
    model.fetchIfNeededOrWait()
  ]);
  expect(api.calls).toHaveLength(1);
});

test('silentFetch() refreshes the value without ever setting .pending', async () => {
  stubApi({'/test': {id: 7}});
  const model = new TestRemote();
  await model.fetch();
  expect(model.pending).toBe(false);

  const api = stubApi({'/test': {id: 8}});
  await model.silentFetch();

  expect(api.calls).toHaveLength(1);
  expect(model.value).toEqual({id: 8});
  expect(model.pending).toBe(false);
});
