/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

const METADATA_KEYS = {
  columns: 'MetadataColumns',
  columnsSorting: 'MetadataColumnsSorting',
  filters: 'MetadataFilters'
};

const ENTITY_PRIORITY = {
  [METADATA_KEYS.filters]: [FOLDER],
  default: [FOLDER, USER, ROLE]
};

function postprocessValue (value, key) {
  if (key === METADATA_KEYS.filters) {
    try {
      return JSON.parse(value);
    } catch (e) {
      return {};
    }
  }
  if (typeof value === 'string') {
    return value.split(',')
      .map(item => item.trim())
      .filter(item => item.length > 0);
  }
  return value;
}

function getDefaultMetadataProperties (folderId, userInfo) {
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
          const metadataParameters = {};
          for (const key of Object.values(METADATA_KEYS)) {
            const priority = ENTITY_PRIORITY[key] || ENTITY_PRIORITY.default;
            for (const entityClass of priority) {
              if (metadataParameters.hasOwnProperty(key)) {
                break;
              }
              const entityObject = (metadataRequest.value || [])
                .find(item => item &&
                  item.entity.entityClass === entityClass &&
                  item.data && item.data[key]
                );
              if (entityObject) {
                const {value} = entityObject.data[key];
                metadataParameters[key] = postprocessValue(value, key);
              }
            }
          }
          resolve(metadataParameters);
        }
        resolve({});
      })
      .catch(() => resolve({}));
  });
}

export {METADATA_KEYS};
export default getDefaultMetadataProperties;
