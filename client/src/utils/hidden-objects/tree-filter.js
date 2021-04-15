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

import {ItemTypes} from '../../components/pipelines/model/treeStructureFunctions';
import ObjectTypes from './object-types';

function getItemProperties (treeItem, treeItemType, parent) {
  switch (treeItemType) {
    case ItemTypes.folder:
      return {type: ObjectTypes.folder, id: String(treeItem?.id || '').toLowerCase()};
    case ItemTypes.pipeline:
      return {type: ObjectTypes.pipeline, id: String(treeItem?.id || '').toLowerCase()};
    case ItemTypes.version:
      return {
        type: ObjectTypes.pipelineVersion,
        id: treeItem?.name && parent?.id ? `${parent.id}/${treeItem.name}`.toLowerCase() : undefined
      };
    case ItemTypes.configuration:
      return {type: ObjectTypes.configuration, id: String(treeItem?.id || '').toLowerCase()};
    case ItemTypes.storage:
      return {type: ObjectTypes.storage, id: String(treeItem?.id || '').toLowerCase()};
    case ItemTypes.metadata:
      return {
        type: ObjectTypes.metadataClass,
        id: parent
          ? `${parent.id}/${treeItem?.id}`.toLowerCase()
          : String(treeItem?.id || '').toLowerCase()
      };
    case ItemTypes.metadataFolder:
      return {type: ObjectTypes.metadataFolder, id: String(treeItem?.id || '').toLowerCase()};
  }
  return undefined;
}

export default function treeFilter (hiddenObjects) {
  return function f (filter) {
    return (o, type, ...rest) => {
      const {type: oType, id: oId} = getItemProperties(o, type, ...rest) || {};
      return (!filter || filter(o, type, ...rest)) &&
        (!hiddenObjects || !hiddenObjects.isHidden(oType, oId));
    };
  };
}
