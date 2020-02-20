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

import moment from 'moment-timezone';

function isRangeStart (date, range, formatter) {
  return moment(date).startOf(range).format(formatter) === date.format(formatter);
}

function isRangeEnd (date, range, formatter) {
  return moment(date).endOf(range).format(formatter) === date.format(formatter);
}

function isFullRange (start, end, unit, formatter) {
  return isRangeStart(start, unit, formatter) && isRangeEnd(end, unit, formatter);
}

const isMonthStart = (date) => isRangeStart(date, 'M', 'D.MM');
const isMonthEnd = (date) => isRangeEnd(date, 'M', 'D.MM');
const isFullMonth = (start, end) => isMonthStart(start) && isMonthEnd(end);

const rules = [
  {
    value: date => date.format('DD.MM.YYYY'),
    render: (start) => start.format('D MMM YYYY')
  },
  {
    value: date => date.format('MM.YYYY'),
    render: (start, end) => {
      if (isFullMonth(start, end)) {
        return start.format('MMMM YYYY');
      }
      return `${start.format('MMMM YYYY')}, ${start.format('D')} - ${end.format('D')}`;
    }
  },
  {
    value: date => date.format('YYYY'),
    render: (start, end, year) => {
      const dateFormat = isMonthStart(start) && isMonthEnd(end)
        ? 'MMMM'
        : 'D MMMM';
      return `${start.format(dateFormat)} - ${end.format(dateFormat)}, ${year}`;
    }
  },
  {
    value: () => undefined,
    render: (start, end) => {
      const startStringFormat = isMonthStart(start) ? 'MMM YYYY' : 'D MMM YYYY';
      const endStringFormat = isMonthEnd(end) ? 'MMM YYYY' : 'D MMM YYYY';
      return `${start.format(startStringFormat)} - ${end.format(endStringFormat)}`;
    }
  }
];

export default function dateRangeRenderer (start, end) {
  if (!start || !end) {
    return undefined;
  }
  const [firstMatch] = rules.map(
    (rule) => ({
      startValue: rule.value(start),
      endValue: rule.value(end),
      render: () => rule.render(start, end, rule.value(start))
    })
  )
    .filter(rule => rule.startValue === rule.endValue);
  if (!firstMatch) {
    return undefined;
  }
  return firstMatch.render();
}
