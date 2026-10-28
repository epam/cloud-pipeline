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
import DataStorageDownloadArchive from './DataStorageDownloadArchive';

test('DataStorageDownloadArchive posts {paths, archiveName} to' +
  ' /datastorage/{id}/download/archive and returns the created task', async () => {
  const api = stubApi({
    '/datastorage/7/download/archive': {
      id: 99,
      status: 'PENDING',
      params: {
        type: 'DOWNLOAD_ARCHIVE',
        storageId: 7,
        paths: ['a.txt', 'folder/'],
        archiveName: 'archive-20261021-131302'
      }
    }
  });
  const model = new DataStorageDownloadArchive(7);
  const body = {paths: ['a.txt', 'folder/'], archiveName: 'archive-20261021-131302'};

  await model.send(body);

  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({
    method: 'POST',
    path: '/datastorage/7/download/archive'
  }));
  expect(JSON.parse(api.calls[0].body)).toEqual(body);
  expect(model.value.status).toBe('PENDING');
  expect(model.value.id).toBe(99);
});
