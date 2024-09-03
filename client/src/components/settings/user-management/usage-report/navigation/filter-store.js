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

import {observable} from 'mobx';
import {Period} from '../../../../special/periods';
import RunnerType from './runner-types';
import reportsRouting from './reports-routing';

export function runnersEqual (a, b) {
  const arrayA = (a || []);
  const arrayB = (b || []);
  if (arrayA.length !== arrayB.length) {
    return false;
  }
  for (let i = 0; i < arrayA.length; i++) {
    const testA = arrayA[i];
    if (!arrayB.find(o => o.type === testA.type && o.id === testA.id)) {
      return false;
    }
    const testB = arrayB[i];
    if (!arrayA.find(o => o.type === testB.type && o.id === testB.id)) {
      return false;
    }
  }
  return true;
}

class Filter {
  static RUNNER_SEPARATOR = '|';
  static REGION_SEPARATOR = '|';
  @observable period;
  @observable range;
  @observable runner;
  listeners = [];

  addListener = (listener) => {
    this.removeListener(listener);
    this.listeners.push(listener);
  }

  removeListener = (listener) => {
    this.listeners = this.listeners.filter(l => l !== listener);
  }

  reportListeners = () => {
    for (const listener of this.listeners) {
      listener(this);
    }
  }

  rebuild = ({location, router}) => {
    this.router = router;
    const {
      period = Period.day,
      users,
      range
    } = (location || {}).query || {};
    this.runner = (users || '')
      .split(Filter.RUNNER_SEPARATOR)
      .filter(o => !!o && o.length)
      .map(o => o.split(':'))
      .map(([type, id]) => ({type: RunnerType.parse(type), id}));
    this.period = period;
    this.range = range;
    this.reportListeners();
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
    const params = [
      (runner || []).length > 0 &&
      `users=${runner.map(o => `${o.type}:${o.id}`).join(Filter.RUNNER_SEPARATOR)}`,
      period && `period=${period}`,
      range && `range=${range}`
    ].filter(Boolean);
    let query = '';
    if (params.length) {
      query = `?${params.join('&')}`;
    }
    if (this.router) {
      this.router.push(`${reportsRouting.pathame}${query}`);
    } else {
      this.runner = runner;
      this.period = period;
      this.range = range;
    }
    this.reportListeners();
  };

  buildNavigationFn = (property) => e => this.navigate({[property]: e});

  periodNavigation = (period, range) => this.navigate({period, range}, true);
}

export default Filter;
