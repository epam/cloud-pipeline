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
import AsyncTaskCancel from './AsyncTaskCancel';

test('AsyncTaskCancel posts to /async-task/{id}/cancel and exposes the task as' +
  ' .value', async () => {
  const api = stubApi({'/async-task/42/cancel': {id: 42, status: 'CANCEL_REQUESTED'}});
  const model = new AsyncTaskCancel(42);

  await model.send({});

  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({
    method: 'POST',
    path: '/async-task/42/cancel'
  }));
  expect(model.value).toEqual({id: 42, status: 'CANCEL_REQUESTED'});
});
