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

import Remote from '../basic/Remote';
import defer from '../../utils/defer';

export default class MetadataLoad extends Remote {

  static fetchOptions = {
    mode: 'cors',
    credentials: 'include',
    method: 'POST',
    headers: {
      'Content-Type': 'application/json; charset=UTF-8;'
    }
  };

  constructor (entityId, entityClass) {
    super();
    this.url = '/metadata/load';
    this.entityId = entityId;
    this.entityClass = entityClass;
  }

  _fetchIsExecuting = false;
  async fetch () {
    if (!this._postIsExecuting) {
      this._pending = true;
      this._postIsExecuting = true;
      const {prefix, fetchOptions} = this.constructor;
      try {
        await defer();
        let headers = fetchOptions.headers;
        if (!headers) {
          headers = {};
        }
        fetchOptions.headers = headers;
        let stringifiedBody;
        try {
          stringifiedBody = JSON.stringify([
            {
              entityId: this.entityId,
              entityClass: this.entityClass
            }
          ]);
        } catch (___) {}
        const response = await fetch(
          `${prefix}${this.url}`,
          {...fetchOptions,
            body: stringifiedBody
          }
        );
        const data = this.constructor.isJson ? (await response.json()) : (await response.blob());
        this.update(data);
      } catch (e) {
        this.failed = true;
        this.error = e.toString();
      } finally {
        this._postIsExecuting = false;
      }

      this._pending = false;
      this._fetchIsExecuting = false;
    }
  }
}
