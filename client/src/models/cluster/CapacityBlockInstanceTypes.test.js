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

import {stubApi} from '@test';
import CapacityBlockInstanceTypes from './CapacityBlockInstanceTypes';

test('CapacityBlockInstanceTypes requests /cluster/capacity-reservation/instance-types' +
  ' with the given regionId and exposes the payload list as .value', async () => {
  const types = [{instanceType: 'p5.48xlarge', vCPU: 192, gpuCount: 8}];
  const api = stubApi({
    '/cluster/capacity-reservation/instance-types?regionId=2': types
  });
  const model = new CapacityBlockInstanceTypes(2);

  await model.fetch();

  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({
    method: 'GET',
    path: '/cluster/capacity-reservation/instance-types?regionId=2'
  }));
  // MobX 3 stores an array value as an ObservableArray; .slice() compares a plain array.
  expect(model.value.slice()).toEqual(types);
  expect(model.loaded).toBe(true);
});

test('CapacityBlockInstanceTypes builds its url from the given regionId', () => {
  const model = new CapacityBlockInstanceTypes(7);

  expect(model.url).toBe('/cluster/capacity-reservation/instance-types?regionId=7');
  expect(CapacityBlockInstanceTypes.defaultValue).toEqual([]);
});

test('forRegion fetches a region once and reuses the resolved instance after', async () => {
  const types = [{instanceType: 'p5.48xlarge', vCPU: 192, gpuCount: 8}];
  const api = stubApi({
    '/cluster/capacity-reservation/instance-types?regionId=3': types
  });

  const first = await CapacityBlockInstanceTypes.forRegion(3);
  const second = await CapacityBlockInstanceTypes.forRegion(3);

  expect(api.calls).toHaveLength(1);
  expect(first).toBe(second);
  expect(second.loaded).toBe(true);
  expect(second.value.slice()).toEqual(types);
});

test('forRegion does not cache a failed fetch - the next call retries it', async () => {
  const failing = stubApi();
  failing.fail('/cluster/capacity-reservation/instance-types?regionId=4', 'Region not found');

  const first = await CapacityBlockInstanceTypes.forRegion(4);
  expect(first.loaded).toBe(false);
  expect(failing.calls).toHaveLength(1);

  const types = [{instanceType: 'p4d.24xlarge', vCPU: 96, gpuCount: 8}];
  const retrying = stubApi({
    '/cluster/capacity-reservation/instance-types?regionId=4': types
  });
  const second = await CapacityBlockInstanceTypes.forRegion(4);

  expect(retrying.calls).toHaveLength(1);
  expect(second.loaded).toBe(true);
  expect(second.value.slice()).toEqual(types);
});

test('clearCache makes the next forRegion call fetch again', async () => {
  const first = stubApi({
    '/cluster/capacity-reservation/instance-types?regionId=8': [{instanceType: 'a'}]
  });
  await CapacityBlockInstanceTypes.forRegion(8);
  expect(first.calls).toHaveLength(1);

  CapacityBlockInstanceTypes.clearCache();
  const second = stubApi({
    '/cluster/capacity-reservation/instance-types?regionId=8': [{instanceType: 'b'}]
  });
  const request = await CapacityBlockInstanceTypes.forRegion(8);

  expect(second.calls).toHaveLength(1);
  expect(request.value.slice()).toEqual([{instanceType: 'b'}]);
});

test('forRegion shares one cache entry for a numeric and a string form of one region',
  async () => {
    const api = stubApi({
      '/cluster/capacity-reservation/instance-types?regionId=9': [{instanceType: 'a'}]
    });

    const numeric = await CapacityBlockInstanceTypes.forRegion(9);
    const string = await CapacityBlockInstanceTypes.forRegion('9');

    expect(api.calls).toHaveLength(1);
    expect(numeric).toBe(string);
  });

test('forRegion caches independently per regionId', async () => {
  const api = stubApi({
    '/cluster/capacity-reservation/instance-types?regionId=5': [{instanceType: 'a'}],
    '/cluster/capacity-reservation/instance-types?regionId=6': [{instanceType: 'b'}]
  });

  const regionFive = await CapacityBlockInstanceTypes.forRegion(5);
  const regionSix = await CapacityBlockInstanceTypes.forRegion(6);

  expect(api.calls).toHaveLength(2);
  expect(regionFive).not.toBe(regionSix);
  expect(regionFive.value.slice()).toEqual([{instanceType: 'a'}]);
  expect(regionSix.value.slice()).toEqual([{instanceType: 'b'}]);
});
