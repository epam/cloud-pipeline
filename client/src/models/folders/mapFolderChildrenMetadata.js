/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

export default function mapFolderChildrenMetadata (
  requestResult,
  {childFolders, pipelines, storages, configurations}) {
  const clearInfo = (item) => {
    item.hasMetadata = false;
    item.issuesCount = 0;
    item.objectMetadata = undefined;
  };
  (childFolders || []).forEach(clearInfo);
  (pipelines || []).forEach(clearInfo);
  (storages || []).forEach(clearInfo);
  (configurations || []).forEach(clearInfo);
  if (requestResult.loaded && (requestResult.value || []).length > 0) {
    (requestResult.value || []).filter(info => info.entity).forEach(info => {
      let item;
      switch (info.entity.entityClass) {
        case 'FOLDER': [item] = (childFolders || [])
          .filter(folder => folder.id === info.entity.entityId);
          break;
        case 'PIPELINE': [item] = (pipelines || [])
          .filter(pipeline => pipeline.id === info.entity.entityId);
          break;
        case 'DATA_STORAGE': [item] = (storages || [])
          .filter(storage => storage.id === info.entity.entityId);
          break;
        case 'CONFIGURATION': [item] = (configurations || [])
          .filter(configuration => configuration.id === info.entity.entityId);
          break;
      }
      if (item) {
        item.issuesCount = info.issuesCount;
        item.hasMetadata = !!info.data;
        item.objectMetadata = info.data;
      }
    });
  }
}
