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
import NodeInstance from './NodeInstance';

class ClusterNodes extends Remote {
  constructor () {
    super();
    this.url = '/cluster/node/loadAll';
  };

  _nodesCache = new Map();

  getNode (name) {
    if (!this._nodesCache.has(name)) {
      const instance = new NodeInstance(name);
      this._nodesCache.set(name, instance);
      return instance;
    }
    return this._nodesCache.get(name);
  }

  clearCachedNode (name) {
    if (this._nodesCache.has(name)) {
      this._nodesCache.delete(name);
    }
  }
}

export default new ClusterNodes();
