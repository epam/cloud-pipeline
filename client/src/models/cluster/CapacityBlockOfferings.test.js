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
import CapacityBlockOfferings from './CapacityBlockOfferings';

test('CapacityBlockOfferings posts to /cluster/capacity-reservation/offerings' +
  ' and exposes the payload list as .value', async () => {
  const offerings = [{offeringId: 'offering-1', instanceType: 'p5.48xlarge'}];
  const api = stubApi({
    '/cluster/capacity-reservation/offerings': offerings
  });
  const model = new CapacityBlockOfferings();
  const body = {
    regionId: 2,
    resourceType: 'INSTANCE',
    candidates: [{instanceType: 'p5.48xlarge', instanceCount: 4}],
    startDateRange: '2026-12-21 04:14:02.500',
    capacityDurationHours: 24
  };

  await model.send(body);

  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({
    method: 'POST',
    path: '/cluster/capacity-reservation/offerings'
  }));
  expect(JSON.parse(api.calls[0].body)).toEqual(body);
  expect(model.value).toEqual(offerings);
});

test('CapacityBlockOfferings falls back to an empty list when the payload is missing', async () => {
  stubApi({'/cluster/capacity-reservation/offerings': undefined});
  const model = new CapacityBlockOfferings();

  await model.send({});

  expect(model.value).toEqual([]);
});
