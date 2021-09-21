/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import Remote from '../basic/Remote';
import defer from '../../utils/defer';

class PipelineConfigurations extends Remote {
  constructor (id, pipelineRequest) {
    super();
    this.pipelineRequest = pipelineRequest;
    // this.url = `/pipeline/${id}/configurations?version=${version}`;
  };

  async fetch () {
    this._loadRequired = false;
    if (!this._fetchPromise) {
      this._fetchPromise = new Promise(async (resolve) => {
        this._pending = true;
        const {prefix, fetchOptions} = this.constructor;
        try {
          await defer();
          await this.pipelineRequest.fetchIfNeededOrWait();
          if (!this.pipelineRequest.loaded) {
            throw new Error(this.pipelineRequest.error || 'Error fetching pipeline info');
          }
          const pipeline = this.pipelineRequest.value;
          if (!pipeline.currentVersion) {
            throw new Error('Error fetching pipeline latest version');
          }
          this.url = `/pipeline/${pipeline.id}/configurations?version=${pipeline.currentVersion.name}`;
          let headers = fetchOptions.headers;
          if (!headers) {
            headers = {};
          }
          fetchOptions.headers = headers;
          const response = await fetch(`${prefix}${this.url}`, fetchOptions);
          const data = this.constructor.isJson ? (await response.json()) : (await response.blob());
          this.update(data);
        } catch (e) {
          this.failed = true;
          this.error = e.toString();
        }

        this._pending = false;
        this._fetchPromise = null;
        resolve();
      });
    }
    return this._fetchPromise;
  }
}

export default PipelineConfigurations;
