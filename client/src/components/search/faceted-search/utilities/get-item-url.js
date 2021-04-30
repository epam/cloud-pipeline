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

import {SearchItemTypes} from '../../../../models/search';

// URL should starts with slash, i.e. '/storage/id', '/folder/id' etc.
function getItemUrl (item) {
  return new Promise((resolve) => {
    switch (item.type) {
      case SearchItemTypes.azFile:
      case SearchItemTypes.s3File:
      case SearchItemTypes.NFSFile:
      case SearchItemTypes.gsFile:
        if (item.parentId) {
          const path = item.id;
          const parentFolder = path.split('/').slice(0, path.split('/').length - 1).join('/');
          if (parentFolder) {
            resolve(`/storage/${item.parentId}?path=${parentFolder}`);
            return;
          } else {
            resolve(`/storage/${item.parentId}`);
            return;
          }
        }
        break;
      case SearchItemTypes.azStorage:
      case SearchItemTypes.s3Bucket:
      case SearchItemTypes.NFSBucket:
      case SearchItemTypes.gsStorage:
        resolve(`/storage/${item.id}`);
        return;
      case SearchItemTypes.run:
        resolve(`/run/${item.id}`);
        return;
      case SearchItemTypes.pipeline:
        resolve(`/${item.id}`);
        return;
      case SearchItemTypes.tool:
        resolve(`/tool/${item.id}`);
        return;
      case SearchItemTypes.folder:
        resolve(`/folder/${item.id}`);
        return;
      case SearchItemTypes.configuration:
        const [id, ...configName] = item.id.split('-');
        resolve(`/configuration/${id}/${configName.join('-')}`);
        return;
      case SearchItemTypes.metadataEntity:
        if (item.parentId) {
          resolve(`/metadata/redirect/${item.parentId}/${item.id}`);
          return;
        }
        break;
      case SearchItemTypes.issue:
        if (item.entity) {
          const {entityClass, entityId} = item.entity;
          switch (entityClass.toLowerCase()) {
            case 'folder':
              resolve(`/folder/${entityId}/`);
              return;
            case 'pipeline':
              resolve(`/${entityId}/`);
              return;
            case 'tool':
              resolve(`/tool/${entityId}/`);
              return;
          }
        }
        break;
      case SearchItemTypes.pipelineCode:
        const {parentId, pipelineVersion, path} = item;
        if (parentId && pipelineVersion && path) {
          if (/^docs\//i.test(path)) {
            resolve(`/${item.parentId}/${pipelineVersion}/documents`);
          } else if (/^src\//i.test(path)) {
            const subPath = path.substr(4).split('/').slice(0, -1).join('/');
            if (!subPath) {
              resolve(`/${item.parentId}/${pipelineVersion}/code`);
            } else {
              // eslint-disable-next-line
              resolve(`/${item.parentId}/${pipelineVersion}/code?path=${encodeURIComponent(subPath)}`);
            }
            return;
          }
          resolve(`/${item.parentId}/${pipelineVersion}/code`);
          return;
        } else if (parentId && pipelineVersion) {
          resolve(`/${item.parentId}/${pipelineVersion}`);
        } else if (parentId) {
          resolve(`/${item.parentId}`);
        }
        break;
    }
    resolve(undefined);
  });
}

export default getItemUrl;
