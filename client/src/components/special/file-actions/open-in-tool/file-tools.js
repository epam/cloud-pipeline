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

import {action, observable} from 'mobx';
import MetadataMultiLoad from '../../../../models/metadata/MetadataMultiLoad';

const METADATA_KEY = 'open-in-files';
const METADATA_TEMPLATE = 'open-in-files-template';

function getFileTypes (metadata = {}) {
  const {value} = metadata;
  if (!value) {
    return [];
  }
  return value.split(',').map(type => type.trim().toLowerCase());
}

class FileTools {
  @observable loaded = false;
  @observable error = undefined;
  @observable tools = undefined;
  promise = undefined;

  @action
  fetch (toolsIds) {
    if (this.promise) {
      return this.promise;
    }
    this.promise = new Promise((resolve) => {
      const payload = toolsIds.map(id => ({
        entityId: id,
        entityClass: 'TOOL'
      }));
      const request = new MetadataMultiLoad(payload);
      request
        .fetch()
        .then(() => {
          if (request.loaded) {
            this.tools = (request.value || [])
              .filter(value => value.data && value.data[METADATA_KEY])
              .map((value) => ({
                toolId: value.entity.entityId,
                openInFiles: getFileTypes(value.data[METADATA_KEY]),
                template: (value.data[METADATA_TEMPLATE] || {}).value
              }));
            this.loaded = true;
            this.error = undefined;
          } else {
            throw new Error(request.error || 'File tools not found');
          }
        })
        .catch(e => {
          this.loaded = false;
          this.tools = undefined;
          this.error = e.message;
        })
        .then(() => resolve(this.tools));
    });
    return this.promise;
  }

  clearCache () {
    this.promise = undefined;
  }
}

export default FileTools;
