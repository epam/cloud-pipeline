/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import moment from 'moment-timezone';

export default function Duration ({startDate, endDate}) {
  if (!startDate) {
    return null;
  }
  const start = moment.utc(startDate);
  const finish = endDate ? moment.utc(endDate) : moment.utc();
  const getDuration = (unit) => {
    return finish.diff(start, unit, false);
  };
  const plural = (count, label) => {
    return `${count} ${label}${count === 1 ? '' : 's'}`;
  };
  const seconds = getDuration('s');
  const minutes = getDuration('m');
  const hours = getDuration('h');
  let display = plural(seconds, 'second');
  if (hours >= 2) {
    display = plural(hours, 'hour');
  } else if (minutes >= 2) {
    display = plural(minutes, 'minute');
  }
  return (
    <span>
      {display}
    </span>
  );
}
