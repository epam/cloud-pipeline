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

export default async function getObjectMetadataAttribute (folderId, userInfo, attributeName) {

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

  try {
    const metadataRequest = new MetadataMultiLoad(requestBody);
    await metadataRequest.fetch();
    if (metadataRequest.value && metadataRequest.value.length) {
      const entityClasses = [FOLDER, USER, ROLE];
      let attributeValue = [];
      for (let key in entityClasses) {
        if (attributeValue.length === 0) {
          const metadataRequestValue = metadataRequest.value
            .filter(item => item &&
              item.entity.entityClass === entityClasses[key] &&
              item.data[attributeName]
            )[0];
          if (metadataRequestValue && metadataRequestValue.data) {
            attributeValue = metadataRequestValue.data[attributeName].value
              .split(',')
              .filter(item => item)
              .map(item => item.trim());
            if (attributeValue.length) {
              return attributeValue;
            }
          }
        }
      }
    }
  } catch (e) {
    return [];
  }
  return [];
}
