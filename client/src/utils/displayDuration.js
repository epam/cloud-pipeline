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
import moment from 'moment-timezone';

export default (start, end = undefined) => {
  if (!start && !end) {
    return null;
  }
  const diff = moment
    .utc(end ? moment.utc(end) : moment.utc())
    .diff(moment.utc(start), 'seconds', false);
  const MINUTE = 60;
  const HOUR = 60 * MINUTE;
  const DAY = 24 * HOUR;
  const days = Math.floor(diff / DAY);
  const hours = Math.floor((diff - days * DAY) / HOUR);
  const minutes = Math.floor((diff - days * DAY - hours * HOUR) / MINUTE);
  const seconds = diff - days * DAY - hours * HOUR - minutes * MINUTE;
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
};
