/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import RemotePost from '../basic/RemotePost';
import defer from '../../utils/defer';

class GetDataWithPrevious extends RemotePost {
  constructor (Model, filters, ...opts) {
    super();
    this.filters = filters;
    const {
      start,
      end,
      previousStart,
      previousEnd,
      previousShiftFn,
      previousFilterFn,
      ...rest
    } = filters;
    const hasPreviousDates = previousStart && previousEnd;
    const currentFilters = {
      start,
      end,
      ...rest
    };
    const previousFilters = {
      start: previousStart,
      end: previousEnd,
      dateMapper: previousShiftFn,
      dateFilter: previousFilterFn,
      ...rest
    };
    this.current = new Model(currentFilters, ...opts);
    this.previous = hasPreviousDates
      ? (new Model(previousFilters, ...opts))
      : undefined;
  }

  send () {
    return this.fetch();
  }

  async fetch () {
    this._pending = true;
    try {
      await defer();
      await Promise.all([
        this.current.fetch(),
        this.previous ? this.previous.fetch() : Promise.resolve(true)
      ]);
      if (this.current.error) {
        throw new Error(this.current.error);
      }
      const current = this.current.value;
      const previous = this.previous && this.previous.loaded
        ? this.previous.value
        : [];
      this.update({payload: {current, previous}, status: 'OK'});
    } catch (e) {
      this.failed = true;
      this.error = e.toString();
    } finally {
      this._postIsExecuting = false;
    }
    this._pending = false;
    this._fetchIsExecuting = false;
  }

  postprocess (value) {
    return super.postprocess(value);
  }
}

export default GetDataWithPrevious;
