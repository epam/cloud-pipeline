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

import {getAllStatusesArray} from './evaluateRunDuration';

export function displayedFullPausedDuration (duration) {
  const MINUTE = 60;
  const HOUR = 60 * MINUTE;
  const DAY = 24 * HOUR;

  const days = Math.floor(duration / DAY);
  const hours = Math.floor((duration - days * DAY) / HOUR);
  const minutes = Math.floor((duration - days * DAY - hours * HOUR) / MINUTE);
  const seconds = duration - days * DAY - hours * HOUR - minutes * MINUTE;

  const plural = (count, word) => `${count} ${word}${count === 1 ? '' : 's'}`;
  if (days > 0) {
    return plural(days, 'day');
  }
  if (hours > 0) {
    return plural(hours, 'hour');
  }
  if (minutes >= 1) {
    return `${minutes} min`;
  }
  return `${seconds} sec`;
}

export default function evaluatePausedDurations (run) {
  const dates = getAllStatusesArray(run);
  const isPaused = dates.some(date => date.status === 'PAUSED');
  if (!isPaused) return null;
  const pausedDurations = [];
  for (let i = 0; i < dates.length - 1; i++) {
    const start = dates[i];
    const end = dates[i + 1];
    if (/^PAUSED$/i.test(start.status)) {
      pausedDurations.push({
        start,
        end,
        duration: end.timestamp.diff(start.timestamp, 'seconds', true)
      });
    }
  }
  return pausedDurations;
}
