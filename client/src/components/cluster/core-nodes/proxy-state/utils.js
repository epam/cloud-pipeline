/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import dayjs from '../../../../utils/dayjs';
import {HISTOGRAM_TYPES} from '../../../../models/cluster/ClusterNetworkUsageFilter';

export function getDatasetStyles(key, reportThemes) {
  const common = {
    maxBarThickness: 50,
    showFlag: false,
    textColor: reportThemes.textColor,
  };
  return {
    backgroundColor: reportThemes.lightPrevious,
    borderColor: reportThemes.previous,
    flagColor: reportThemes.previous,
    textColor: reportThemes.textColor,
    borderWidth: 2,
    ...common,
  };
}

export function getDatasetOptions(key) {
  if (key === HISTOGRAM_TYPES.time) {
    return {
      type: 'line',
    };
  }
  if (key === HISTOGRAM_TYPES.resource) {
    return {
      type: 'horizontalBar',
    };
  }
}

export function checkDateInRange(date, start = undefined, end = undefined) {
  const dateToCheck = dayjs.utc(date).startOf('day').add(1, 'day');
  if (start && dayjs.utc(start).startOf('day') > dateToCheck) {
    return true;
  }
  if (end && dayjs.utc(end).endOf('day') < dateToCheck) {
    return true;
  }
  return dayjs.utc().endOf('day') < dateToCheck;
}

export function formatLabel(type, value, filters = {}) {
  const {from, to} = filters;
  if (type === HISTOGRAM_TYPES.time) {
    let format = 'YYYY-MM-DD HH:mm:ss';
    if (from && to) {
      const fromDayjs = dayjs.utc(from);
      const toDayjs = dayjs.utc(to);
      if (toDayjs.diff(fromDayjs, 'hour') < 24) {
        format = 'HH:mm:ss';
      }
    }
    return dayjs.utc(value).format(format);
  }
  return value;
}
