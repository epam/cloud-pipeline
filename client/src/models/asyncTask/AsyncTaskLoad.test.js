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
import AsyncTaskLoad from './AsyncTaskLoad';

test('AsyncTaskLoad requests /async-task/{id} and exposes the task as .value', async () => {
  const api = stubApi({'/async-task/42': {id: 42, status: 'RUNNING'}});
  const model = new AsyncTaskLoad(42);

  await model.fetch();

  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({
    method: 'GET',
    path: '/async-task/42'
  }));
  expect(model.value).toEqual({id: 42, status: 'RUNNING'});
  expect(model.loaded).toBe(true);
});
