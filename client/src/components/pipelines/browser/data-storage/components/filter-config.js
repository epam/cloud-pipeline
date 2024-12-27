/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import moment from 'moment-timezone';

const FILTER_FIELDS = {
  name: 'name',
  sizeGreaterThan: 'sizeGreaterThan',
  sizeLessThan: 'sizeLessThan',
  dateAfter: 'dateAfter',
  dateBefore: 'dateBefore',
  dateFilterType: 'dateFilterType'
};

const PREDEFINED_DATE_FILTERS = [{
  title: 'Last week',
  key: 'lastWeek',
  dateAfter: (currentDate) => currentDate && moment(currentDate).subtract(7, 'days').startOf('day'),
  dateBefore: undefined
}, {
  title: 'Last month',
  key: 'lastMonth',
  dateAfter: (currentDate) => currentDate && moment(currentDate).subtract(1, 'month').endOf('day'),
  dateBefore: undefined
}];

export {FILTER_FIELDS, PREDEFINED_DATE_FILTERS};
