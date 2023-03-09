/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import {action, computed, observable} from 'mobx';
import PipelineRunSingleFilter from '../../../models/pipelines/PipelineRunSingleFilter';

class ChildRuns {
  @observable _pending = true;
  @observable _loaded = false;
  @observable _childRuns = [];
  @observable _error = undefined;
  @observable disabled = false;

  constructor (parentId) {
    this.parentId = parentId;
  }

  @computed
  get pending () {
    return this._pending;
  }

  @computed
  get loaded () {
    return this._loaded;
  }

  @computed
  get childRuns () {
    return this._childRuns;
  }

  @computed
  get error () {
    return this._error;
  }

  _fetchPromise;
  _fetchPromiseOptions;

  @action
  fetch = (count = 50) => {
    if (this._fetchPromise && this._fetchPromiseOptions === count) {
      return this._fetchPromise;
    }
    this._fetchPromiseOptions = count;
    this._fetchPromise = new Promise(async (resolve) => {
      if (count === 0) {
        this._pending = false;
        this._error = undefined;
        this._loaded = true;
        this._childRuns = [];
        resolve();
        return;
      }
      try {
        this._pending = true;
        const request = new PipelineRunSingleFilter({
          page: 1,
          pageSize: count,
          parentId: this.parentId,
          userModified: true
        }, false);
        await request.filter();
        if (request.error) {
          throw new Error(request.error);
        }
        if (request.networkError) {
          throw new Error(request.networkError);
        }
        this._childRuns = request.value || [];
        this._error = undefined;
        this._loaded = true;
      } catch (error) {
        this._childRuns = [];
        this._error = error.message;
        this._loaded = false;
      } finally {
        this._pending = false;
        this._fetchPromise = undefined;
        this._fetchPromiseOptions = undefined;
        resolve();
      }
    });
    return this._fetchPromise;
  }
}

export default ChildRuns;
