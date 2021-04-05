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

import RemotePost from '../../basic/RemotePost';
import {computed, observable} from 'mobx';
import pipelineRunFSBrowserCache from '../../pipelines/PipelineRunFSBrowserCache';

class VSRemotePost extends RemotePost {
  static fetchOptions = {
    mode: 'cors',
    credentials: 'include',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=UTF-8;'
    }
  };

  @observable _endpoint;
  @observable initialized = false;
  fetchEndpointPromise;
  runId;

  @computed
  get endpoint () {
    return this._endpoint;
  }

  set endpoint (value) {
    if (value) {
      this._endpoint = value.endsWith('/') ? value : value.concat('/');
      this.constructor.prefix = this._endpoint;
    } else {
      this._endpoint = undefined;
    }
  }

  constructor (runId) {
    super();
    this.runId = runId;
  }

  fetchEndpoint () {
    if (this.endpoint) {
      return Promise.resolve(this.endpoint);
    }
    if (this.fetchEndpointPromise) {
      return this.fetchEndpointPromise;
    }
    const runId = this.runId;
    this.fetchEndpointPromise = new Promise((resolve) => {
      const fsBrowserRequest = pipelineRunFSBrowserCache.getPipelineRunFSBrowser(this.runId);
      fsBrowserRequest
        .fetchIfNeededOrWait()
        .then(() => {
          if (fsBrowserRequest.error || !fsBrowserRequest.value) {
            // eslint-disable-next-line
            console.warn(`Error fetching FS Browser endpoint for #${runId} run: ${fsBrowserRequest.error}`);
          } else {
            this.endpoint = fsBrowserRequest.value;
          }
          this.fetchEndpointPromise = undefined;
          resolve(this.endpoint);
        })
        .catch(e => {
          console.warn(`Error fetching FS Browser endpoint for #${runId} run: ${e.message}`);
          this.fetchEndpointPromise = undefined;
          resolve();
        });
    });
    return this.fetchEndpointPromise;
  }

  async send (body) {
    return this.fetchEndpoint().then(() => super.send(body));
  }
}

export default VSRemotePost;
