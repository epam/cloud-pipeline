/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import MetadataUpdate from '../../../models/metadata/MetadataUpdate';

function updateUserMetadata (userId, data) {
  return new Promise((resolve, reject) => {
    const updateRequest = new MetadataUpdate();
    const entity = {
      entityId: +userId,
      entityClass: 'PIPELINE_USER'
    };
    updateRequest.send({
      entity,
      data
    })
      .then(() => {
        if (updateRequest.error || !updateRequest.loaded) {
          throw new Error(updateRequest.error || 'Error updating user');
        }
        resolve(updateRequest.value);
      })
      .catch(reject);
  });
}

export default updateUserMetadata;
