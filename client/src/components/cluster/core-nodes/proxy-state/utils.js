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

import moment from 'moment-timezone';
import {
  HISTOGRAM_TYPES
} from '../../../../models/cluster/ClusterNetworkUsageFilter';

export function getDatasetStyles (key, reportThemes) {
  const common = {
    maxBarThickness: 50,
    showFlag: false,
    textColor: reportThemes.textColor
  };
  return {
    backgroundColor: reportThemes.lightPrevious,
    borderColor: reportThemes.previous,
    flagColor: reportThemes.previous,
    textColor: reportThemes.textColor,
    borderWidth: 2,
    ...common
  };
}

export function getDatasetOptions (key) {
  if (key === HISTOGRAM_TYPES.time) {
    return {
      type: 'line'
    };
  }
  if (key === HISTOGRAM_TYPES.resource) {
    return {
      type: 'horizontalBar'
    };
  }
};

export function checkDateInRange (date, start = undefined, end = undefined) {
  const dateToCheck = moment.utc(date).startOf('D').add(1, 'D');
  if (start && moment.utc(start).startOf('D') > dateToCheck) {
    return true;
  }
  if (end && moment.utc(end).endOf('D') < dateToCheck) {
    return true;
  }
  return moment.utc().endOf('D') < dateToCheck;
}

export function formatLabel (type, value, filters = {}) {
  const {from, to} = filters;
  if (type === HISTOGRAM_TYPES.time) {
    let format = 'YYYY-MM-DD HH:mm:ss';
    if (from && to) {
      const fromMoment = moment.utc(from);
      const toMoment = moment.utc(to);
      if (toMoment.diff(fromMoment, 'hours') < 24) {
        format = 'HH:mm:ss';
      }
    }
    return moment.utc(value).format(format);
  }
  return value;
}
