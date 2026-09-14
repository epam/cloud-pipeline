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

import {describeLive} from '@test';
import FolderLoad from './FolderLoad';

// A smoke test, not a suite: proves the live seam reaches a real deployment
// and that the response is still the platform's Result<T> envelope. It never
// asserts on which folders exist — folder 1 may or may not be present on a
// given deployment, and whether it is is not this test's business, only the
// shape of whatever comes back.
describeLive('FolderLoad (contract)', () => {
  test('/folder/1/load returns a Result<T> envelope', async () => {
    const model = new FolderLoad(1);

    await model.fetch();

    expect(model.response).toBeTruthy();
    expect(model.response.status).toBeDefined();
    if (model.response.status === 'OK') {
      expect(model.value).toEqual(expect.objectContaining({
        id: expect.anything(),
        name: expect.anything()
      }));
    } else {
      expect(model.response.message).toBeDefined();
    }
  });
});
