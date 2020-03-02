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

import {observable} from 'mobx';
import {Period, getPeriod} from '../periods';
import {RunnerType} from './runner-filter';
import ReportsRouting from './reports-routing';

class Filter {
  @observable period;
  @observable range;
  @observable report;
  @observable runner;

  rebuild = ({location, router}) => {
    this.router = router;
    const {
      period = Period.month,
      user,
      group,
      range
    } = (location || {}).query || {};
    if (user) {
      this.runner = {
        type: RunnerType.user,
        id: user
      };
    } else if (group) {
      this.runner = {
        type: RunnerType.group,
        id: group
      };
    } else {
      this.runner = undefined;
    }
    this.report = ReportsRouting.parse(location);
    this.period = period;
    this.range = range;
  };

  navigate = (navigation, strictRange = false) => {
    let {report, runner, period, range} = navigation || {};
    if (report === undefined) {
      report = this.report;
    }
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
      runner && runner.type === RunnerType.user && `user=${runner.id}`,
      runner && runner.type === RunnerType.group && `group=${runner.id}`,
      period && `period=${period}`,
      range && `range=${range}`
    ].filter(Boolean);
    let query = '';
    if (params.length) {
      query = `?${params.join('&')}`;
    }
    if (this.router) {
      this.router.push(`${ReportsRouting.getPath(report)}${query}`);
    }
  };

  getDescription = ({users}) => {
    const title = ReportsRouting.getTitle(this.report) || 'Report';
    const {start, endStrict} = getPeriod(this.period, this.range);
    let dates = this.period;
    if (start && endStrict) {
      dates = `${start.format('YYYY-MM-DD')} - ${endStrict.format('YYYY-MM-DD')}`;
    }
    let runner;
    if (this.runner && this.runner.type === RunnerType.user && users && users.loaded) {
      const [user] = (users.value || []).filter(({id}) => `${id}` === `${this.runner.id}`);
      if (user) {
        runner = user.userName;
      } else {
        runner = `user #${this.runner.id}`;
      }
    } else if (this.runner) {
      runner = `${this.runner.type} ${this.runner.id}`;
    }
    return [
      title,
      dates,
      runner
    ].filter(Boolean).join(' - ');
  };

  buildNavigationFn = (property) => e => this.navigate({[property]: e});

  periodNavigation = (period, range) => this.navigate({period, range}, true);

  reportNavigation = (report, runner) => this.navigate({report, runner});
}

export default Filter;
