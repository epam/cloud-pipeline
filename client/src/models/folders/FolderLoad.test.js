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
import FolderLoad from './FolderLoad';

// Proves the dominant model idiom (a two-line Remote subclass) is testable at
// all — Remote.test.js covers the shared behaviour these one-liners inherit.

test('FolderLoad requests /folder/{id}/load and exposes the payload as .value', async () => {
  const api = stubApi({'/folder/42/load': {id: 42, name: 'root'}});
  const model = new FolderLoad(42);

  await model.fetch();

  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({
    method: 'GET',
    path: '/folder/42/load'
  }));
  expect(model.value).toEqual({id: 42, name: 'root'});
  expect(model.loaded).toBe(true);
});
