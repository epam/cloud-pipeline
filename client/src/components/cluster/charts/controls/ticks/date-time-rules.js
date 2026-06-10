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

import dayjs, {calendarDate} from '../../../../../utils/dayjs';

const buildRule = (fnName) => ({
  fn: (duration) =>
    typeof duration[fnName] === 'function'
      ? duration[fnName]()
      : typeof fnName === 'function'
        ? fnName(duration)
        : 0,
  fillRange: function (start, end, isBase = true) {
    let date = this.getAnchor(start);
    if (!date.isValid()) {
      return [];
    }
    const result = [];
    if (isBase) {
      result.push({
        tick: start.unix(),
        display: this.getFullDescription(start),
        isBase,
        isStart: true,
      });
      result.push({
        tick: end.unix(),
        display: this.getFullDescription(end),
        isBase,
        isEnd: true,
      });
    }
    while (date <= start) {
      date = this.addStep(date);
    }
    while (date < end) {
      if (result.filter(({tick}) => tick === date.unix()).length === 0) {
        result.push({
          tick: date.unix(),
          display: this.getDescription(date, !isBase),
          isBase,
        });
      }
      date = this.addStep(date);
    }
    if (isBase && this.nextRule) {
      const subResult = this.nextRule.fillRange(start, end, false);
      for (let i = 0; i < subResult.length; i++) {
        const item = subResult[i];
        if (result.filter(({tick}) => tick === item.tick).length === 0) {
          result.push(item);
        }
      }
    }
    return result;
  },
  nextRule: undefined,
  getDescription: (date) => date.format(),
});

const rules = [
  {
    ...buildRule('asYears'),
    getAnchor: (date) => calendarDate([date.year(), 0, 1, 0, 0, 0]),
    addStep: (date) => date.add(1, 'year'),
    getFullDescription: (date) => date.format('YYYY'),
    getDescription: (date) => date.format('YYYY'),
  },
  {
    ...buildRule((duration) => duration.asMonths() / 3),
    getAnchor: (date) => calendarDate([date.year(), 0, 1, 0, 0, 0]),
    addStep: (date) => date.add(1, 'quarter'),
    getFullDescription: (date) => date.format('MMM YYYY'),
    getDescription: (date) => date.format('MMM'),
  },
  {
    ...buildRule('asMonths'),
    getAnchor: (date) => calendarDate([date.year(), 0, 1, 0, 0, 0]),
    addStep: (date) => date.add(1, 'month'),
    getFullDescription: (date) => date.format('MMM YYYY'),
    getDescription: (date) => date.format('MMM'),
  },
  {
    ...buildRule('asWeeks'),
    getAnchor: (date) => calendarDate([date.year(), date.month(), 1, 0, 0, 0]),
    addStep: (date) => {
      if (date.date() >= 28) {
        return calendarDate([date.year(), date.month(), 1, 0, 0, 0]).add(1, 'month');
      }
      return date.add(7, 'day');
    },
    getFullDescription: (date) => date.format('D MMM, YYYY'),
    getDescription: (date) => date.format('D MMM'),
  },
  {
    ...buildRule('asDays'),
    getAnchor: (date) => calendarDate([date.year(), date.month(), 1, 0, 0, 0]),
    addStep: (date) => date.add(1, 'day'),
    getFullDescription: (date) => date.format('D MMM YYYY'),
    getDescription: (date, intermediate) => {
      if (intermediate) {
        return date.format('D');
      }
      if (date.month() === 0) {
        return date.format('MMM YYYY');
      }
      return date.format('D MMM');
    },
  },
  {
    ...buildRule((duration) => duration.asHours() / 4),
    getAnchor: (date) => calendarDate([date.year(), date.month(), date.date(), 0, 0, 0]),
    addStep: (date) => date.add(6, 'hour'),
    getFullDescription: (date) => date.format('D MMM YYYY, HH:mm'),
    getDescription: (date) => date.format('HH:mm'),
  },
  {
    ...buildRule('asHours'),
    getAnchor: (date) => calendarDate([date.year(), date.month(), date.date(), 0, 0, 0]),
    addStep: (date) => date.add(1, 'hour'),
    getFullDescription: (date) => date.format('D MMM YYYY, HH:mm'),
    getDescription: (date) => date.format('HH:mm'),
  },
  {
    ...buildRule((duration) => duration.asMinutes() / 12),
    getAnchor: (date) => calendarDate([date.year(), date.month(), date.date(), date.hour(), 0, 0]),
    addStep: (date) => date.add(5, 'minute'),
    getFullDescription: (date) => date.format('D MMM YYYY, HH:mm'),
    getDescription: (date) => date.format('HH:mm'),
  },
  {
    ...buildRule('asMinutes'),
    getAnchor: (date) => calendarDate([date.year(), date.month(), date.date(), date.hour(), 0, 0]),
    addStep: (date) => date.add(1, 'minute'),
    getFullDescription: (date) => date.format('D MMM YYYY, HH:mm'),
    getDescription: (date) => date.format('HH:mm'),
  },
  {
    ...buildRule((duration) => duration.asSeconds() / 6),
    getAnchor: (date) =>
      calendarDate([date.year(), date.month(), date.date(), date.hour(), date.minute(), 0]),
    addStep: (date) => date.add(10, 'second'),
    getFullDescription: (date) => date.format('D MMM YYYY, HH:mm'),
    getDescription: (date) => date.format('HH:mm:ss'),
  },
  {
    ...buildRule('asSeconds'),
    getAnchor: (date) =>
      calendarDate([date.year(), date.month(), date.date(), date.hour(), date.minute(), 0]),
    addStep: (date) => date.add(1, 'second'),
    getFullDescription: (date) => date.format('D MMM YYYY, HH:mm'),
    getDescription: (date) => date.format('HH:mm:ss'),
  },
];

for (let i = 0; i < rules.length - 1; i++) {
  rules[i].nextRule = rules[i + 1];
}

export default rules;
