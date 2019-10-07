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
import moment from 'moment';
import rules from './date-time-rules';

const SIZE_PER_TICK = 100;

export default function (start, end, canvasSize) {
  if (
    !canvasSize ||
    Math.abs(canvasSize) === Infinity ||
    isNaN(canvasSize) ||
    start === end
  ) {
    return [];
  }
  const dateStart = moment.unix(start);
  const dateEnd = moment.unix(end);
  const duration = moment.duration(dateEnd.diff(dateStart));
  const baseTicksCount = Math.floor(canvasSize / SIZE_PER_TICK);
  const durations = rules
    .map(rule => ({
      ...rule,
      duration: rule.fn(duration)
    }));
  const bestFit = durations
    .filter(d => d.duration <= baseTicksCount).pop() || durations[0];
  return bestFit.fillRange(dateStart, dateEnd);
}
