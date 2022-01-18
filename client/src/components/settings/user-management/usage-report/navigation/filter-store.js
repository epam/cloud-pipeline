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

import {observable, isObservableArray} from 'mobx';
import {Period} from '../../../../special/periods';
import RunnerType from './runner-types';
import reportsRouting from './reports-routing';
class Filter {
  static RUNNER_SEPARATOR = '|';
  static REGION_SEPARATOR = '|';
  @observable period;
  @observable range;
  @observable runner;

  rebuild = ({location, router}) => {
    this.router = router;
    const {
      period = Period.day,
      user,
      group,
      range
    } = (location || {}).query || {};
    if (user) {
      this.runner = {
        type: RunnerType.user,
        id: (user || '').split(Filter.RUNNER_SEPARATOR)
      };
    } else if (group) {
      this.runner = {
        type: RunnerType.group,
        id: (group || '').split(Filter.RUNNER_SEPARATOR)
      };
    } else {
      this.runner = undefined;
    }
    this.period = period;
    this.range = range;
  };

  navigate = (navigation, strictRange = false) => {
    let {runner, period, range} = navigation || {};
    if (runner === undefined) {
      runner = this.runner;
    }
    if (period === undefined) {
      period = this.period;
    }
    if (range === undefined && !strictRange) {
      range = this.range;
    }
    const mapRunnerId = (id) => {
      if (id && (Array.isArray(id) || isObservableArray(id))) {
        return id.join(Filter.RUNNER_SEPARATOR);
      }
      return id;
    };
    const params = [
      runner && runner.type === RunnerType.user && `user=${mapRunnerId(runner.id)}`,
      runner && runner.type === RunnerType.group && `group=${mapRunnerId(runner.id)}`,
      period && `period=${period}`,
      range && `range=${range}`
    ].filter(Boolean);
    let query = '';
    if (params.length) {
      query = `?${params.join('&')}`;
    }
    if (this.router) {
      this.router.push(`${reportsRouting.pathame}${query}`);
    }
  };

  buildNavigationFn = (property) => e => this.navigate({[property]: e});

  periodNavigation = (period, range) => this.navigate({period, range}, true);
}

export default Filter;
