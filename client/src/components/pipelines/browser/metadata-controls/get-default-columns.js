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

import MetadataMultiLoad from '../../../../models/metadata/MetadataMultiLoad';

const FOLDER = 'FOLDER';
const USER = 'PIPELINE_USER';
const ROLE = 'ROLE';

function getDefaultColumns (folderId, userInfo, attributeName = 'MetadataColumns') {
  const requestBody = [
    {
      entityClass: FOLDER,
      entityId: folderId
    }, {
      entityClass: USER,
      entityId: userInfo.id
    },
    ...userInfo.roles.map(role => ({
      entityClass: ROLE,
      entityId: role.id
    }))
  ];
  const metadataRequest = new MetadataMultiLoad(requestBody);
  return new Promise((resolve) => {
    metadataRequest
      .fetch()
      .then(() => {
        if (metadataRequest.loaded && metadataRequest.value && metadataRequest.value.length) {
          const entityClasses = [FOLDER, USER, ROLE];
          for (const entityClass of entityClasses) {
            const metadataRequestValue = (metadataRequest.value || [])
              .find(item => item &&
                item.entity.entityClass === entityClass &&
                item.data && item.data[attributeName]
              );
            if (metadataRequestValue) {
              const attributeValue = metadataRequestValue.data[attributeName].value
                .split(',')
                .map(item => item.trim())
                .filter(item => item.length > 0);
              if (attributeValue.length > 0) {
                resolve(attributeValue);
                return;
              }
            }
          }
        }
        resolve([]);
      })
      .catch(() => resolve([]));
  });
}

export default getDefaultColumns;
