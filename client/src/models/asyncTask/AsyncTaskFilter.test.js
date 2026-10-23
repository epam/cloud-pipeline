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
import AsyncTaskFilter from './AsyncTaskFilter';

test('AsyncTaskFilter posts the given body verbatim to /async-task/filter', async () => {
  const api = stubApi({'/async-task/filter': [{id: 1, status: 'RUNNING'}]});
  const model = new AsyncTaskFilter();
  const body = {type: 'DOWNLOAD_ARCHIVE', owner: 'user', statuses: ['RUNNING']};

  await model.send(body);

  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({
    method: 'POST',
    path: '/async-task/filter'
  }));
  expect(JSON.parse(api.calls[0].body)).toEqual(body);
  expect(model.value).toEqual([{id: 1, status: 'RUNNING'}]);
});

test('a payload-less OK envelope yields an empty array', async () => {
  const api = stubApi();
  api.envelope('/async-task/filter', {status: 'OK'});
  const model = new AsyncTaskFilter();

  await model.send({type: 'DOWNLOAD_ARCHIVE'});

  expect(model.value).toEqual([]);
});
