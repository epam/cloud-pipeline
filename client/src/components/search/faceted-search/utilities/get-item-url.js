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
import MetadataEntityLoad from '../../../../models/folderMetadata/MetadataEntityLoad';
import {getPipelineFileInfo, PipelineFileTypes} from '../../utilities/getPipelineFileInfo';

function getItemUrl (item, stores) {
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
          const request = new MetadataEntityLoad(item.id);
          request
            .fetch()
            .then(() => {
              if (request.loaded && request.value.classEntity && request.value.classEntity.name) {
                resolve(`/metadata/${item.parentId}/${request.value.classEntity.name}`);
              } else {
                resolve(`/metadataFolder/${item.parentId}/`);
              }
            })
            .catch(() => resolve());
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
        if (item.parentId && item.description && item.id) {
          const {pipelines} = stores || {};
          const versions = pipelines.versionsForPipeline(item.parentId);
          versions
            .fetch()
            .then(() => {
              let version = item.description;
              if (versions.loaded) {
                let [v] = (versions.value || []).filter(v => v.name === item.description);
                if (!v && versions.value.length > 0) {
                  const [draft] = versions.value.filter(v => v.draft);
                  if (draft) {
                    version = draft.name;
                  } else {
                    version = versions.value[0].name;
                  }
                }
              }
              let url = `/${item.parentId}/${version}`;
              getPipelineFileInfo(item.parentId, version, item.id)
                .then(fileInfo => {
                  if (fileInfo) {
                    switch (fileInfo.type) {
                      case PipelineFileTypes.document:
                        url = `/${item.parentId}/${version}/documents`;
                        break;
                      case PipelineFileTypes.source:
                        if (fileInfo.path) {
                          url = `/${item.parentId}/${version}/code&path=${fileInfo.path}`;
                        } else {
                          url = `/${item.parentId}/${version}/code`;
                        }
                        break;
                    }
                  }
                  resolve(url);
                });
            })
            .catch(() => resolve());
          return;
        } else if (item.parentId && item.description) {
          resolve(`/${item.parentId}/${item.description}`);
          return;
        } else if (item.parentId) {
          resolve(`/${item.parentId}`);
          return;
        }
        break;
    }
    resolve(undefined);
  });
}

export default getItemUrl;
