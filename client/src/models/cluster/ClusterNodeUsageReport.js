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
import Remote from '../basic/Remote';

const second = 1;
const minute = 60 * second;
const hour = 60 * minute;
const day = 24 * hour;
const week = 7 * day;

const UsageTickIntervals = [
  {
    duration: minute,
    name: '1 minute',
    value: 'PT1M'
  },
  {
    duration: 5 * minute,
    name: '5 minutes',
    value: 'PT5M'
  },
  {
    duration: 15 * minute,
    name: '15 minutes',
    value: 'PT15M'
  },
  {
    duration: 30 * minute,
    name: '30 minutes',
    value: 'PT30M'
  },
  {
    duration: hour,
    name: '1 hour',
    value: 'PT1H'
  },
  {
    duration: 4 * hour,
    name: '4 hours',
    value: 'PT4H'
  },
  {
    duration: 12 * hour,
    value: 'PT12H',
    name: '12 hours'
  },
  {
    duration: day,
    value: 'P1D',
    name: '1 day'
  },
  {
    duration: 7 * day,
    value: 'P7D',
    name: '1 week'
  }
];

function getTickIntervalsCount (fromUnix, toUnix) {
  const toCorrected = toUnix || moment().unix();
  if (!fromUnix) {
    return UsageTickIntervals.map((unit) => ({...unit, count: Infinity}));
  }
  const duration = toCorrected - fromUnix;
  return UsageTickIntervals
    .map((unit) => ({...unit, count: duration / unit.duration}))
    .filter(({count}) => count > 1);
}

function getAvailableTickIntervals (fromUnix, toUnix) {
  return getTickIntervalsCount(fromUnix, toUnix)
    .map(({count, ...unit}) => unit);
}

function autoDetectTickInterval (fromUnix, toUnix) {
  const toCorrected = toUnix || moment().unix();
  if (!fromUnix) {
    return 'P1D'; // 1 day
  }
  const durationInSeconds = toCorrected - fromUnix;
  const ticksCount = 50;
  let intervalInSeconds = Math.ceil(durationInSeconds / ticksCount);
  const days = Math.floor(intervalInSeconds / day);
  intervalInSeconds -= days * day;
  const hours = Math.floor(intervalInSeconds / hour);
  intervalInSeconds -= hours * hour;
  const minutes = Math.floor(intervalInSeconds / minute);
  function getDurationString (value, unit) {
    if (value > 0) {
      return `${value}${unit}`;
    }
    return undefined;
  }
  const d = getDurationString(days, 'D');
  const h = getDurationString(hours, 'H');
  const m = getDurationString(minutes, 'M');
  const period = d ? `${d}` : '';
  const time = h || m ? `T${h || ''}${m || ''}` : '';
  return period || time ? `P${period}${time}` : '';
}

export {autoDetectTickInterval, getAvailableTickIntervals};

export default class ClusterNodeUsageReport extends Remote {
  constructor (name, from, to, tick) {
    super();
    this.constructor.isJson = false;
    this.name = name;
    this.from = from;
    this.to = to;
    this.interval = tick ||
      autoDetectTickInterval(
        from ? moment.utc(from).unix() : undefined,
        to ? moment.utc(to).unix() : undefined
      );
    const parts = [
      from && `from=${encodeURIComponent(from)}`,
      to && `to=${encodeURIComponent(to)}`,
      tick && `interval=${tick}`
    ].filter(Boolean);
    const query = parts.length > 0 ? `?${parts.join('&')}` : '';
    this.url = `/cluster/node/${this.name}/usage/report${query}`;
  }
}
