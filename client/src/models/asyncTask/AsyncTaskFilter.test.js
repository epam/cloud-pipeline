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
  const api = stubApi({
    '/async-task/filter': {elements: [{id: 1, status: 'RUNNING'}], totalCount: 62}
  });
  const model = new AsyncTaskFilter();
  const body = {type: 'DOWNLOAD_ARCHIVE', owner: 'user', statuses: ['RUNNING']};

  await model.send(body);

  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({
    method: 'POST',
    path: '/async-task/filter'
  }));
  expect(JSON.parse(api.calls[0].body)).toEqual(body);
  // `totalCount` is what the filter matched, not what this page carries - one
  // task here, sixty-two matching the filter.
  expect(model.value).toEqual({
    elements: [{id: 1, status: 'RUNNING'}],
    totalCount: 62
  });
});

test('a payload-less OK envelope yields no elements and a zero total', async () => {
  const api = stubApi();
  api.envelope('/async-task/filter', {status: 'OK'});
  const model = new AsyncTaskFilter();

  await model.send({type: 'DOWNLOAD_ARCHIVE'});

  expect(model.value).toEqual({elements: [], totalCount: 0});
});

test('a payload with elements but no totalCount counts what it carries', async () => {
  stubApi({'/async-task/filter': {elements: [{id: 1}, {id: 2}]}});
  const model = new AsyncTaskFilter();

  await model.send({type: 'DOWNLOAD_ARCHIVE'});

  expect(model.value).toEqual({elements: [{id: 1}, {id: 2}], totalCount: 2});
});

// The shape this endpoint answered before the paged result landed. Accepted so
// that a GUI pointed at an older API shows its tasks rather than an empty
// table.
test('a bare array payload is read as its own single page', async () => {
  stubApi({'/async-task/filter': [{id: 1}, {id: 2}, {id: 3}]});
  const model = new AsyncTaskFilter();

  await model.send({type: 'DOWNLOAD_ARCHIVE'});

  expect(model.value).toEqual({
    elements: [{id: 1}, {id: 2}, {id: 3}],
    totalCount: 3
  });
});
