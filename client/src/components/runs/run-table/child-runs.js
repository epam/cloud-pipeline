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

import {action, computed, observable, makeObservable} from 'mobx';
import PipelineRunSingleFilter from '../../../models/pipelines/PipelineRunSingleFilter';

class ChildRuns {
  _pending = true;
  _loaded = false;
  _childRuns = [];
  _error = undefined;
  disabled = false;

  constructor (parentId) {
    makeObservable(this, {
      _pending: observable,
      _loaded: observable,
      _childRuns: observable,
      _error: observable,
      disabled: observable,
      pending: computed,
      loaded: computed,
      childRuns: computed,
      error: computed,
      fetch: action
    });
    this.parentId = parentId;
  }

  get pending () {
    return this._pending;
  }

  get loaded () {
    return this._loaded;
  }

  get childRuns () {
    return this._childRuns;
  }

  get error () {
    return this._error;
  }

  _fetchPromise;
  _fetchPromiseOptions;

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
