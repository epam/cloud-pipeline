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
