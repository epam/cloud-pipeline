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
import wrapRequest from './wrap-request';
import MetadataSearch from '../../../../models/metadata/MetadataSearch';
import LoadTool from '../../../../models/tools/LoadTool';

class HaloTool {
  @observable loaded = false;
  @observable error = undefined;
  @observable tool = undefined;
  promise = undefined;
  @action
  fetch () {
    if (this.promise) {
      return this.promise;
    }
    this.promise = new Promise((resolve) => {
      const request = new MetadataSearch('TOOL', 'open-in-halo', 'true');
      request
        .fetch()
        .then(() => {
          const [info] = request.value;
          if (info) {
            const {entityId} = info;
            return wrapRequest(new LoadTool(entityId));
          } else {
            throw new Error('HALO tool not found');
          }
        })
        .then(toolRequest => {
          if (toolRequest.loaded) {
            this.tool = toolRequest.value;
            this.loaded = true;
            this.error = undefined;
          } else {
            throw new Error(toolRequest.error || 'HALO tool not found');
          }
        })
        .catch(e => {
          this.loaded = false;
          this.tool = undefined;
          this.error = e.message;
        })
        .then(() => resolve(this.tool));
    });
    return this.promise;
  }
}

export default HaloTool;
