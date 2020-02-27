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
import {getCurrentDate} from '../../periods';
import Fields from './fields';

const DATE_FORMAT = 'YYYY-MM-DD';

function getDateRanges (filters) {
  const {start, end, tick} = filters || {};
  if (start && end) {
    let date = moment.utc(start);
    const dates = [];
    const nextDate = () => {
      if (/^1M$/i.test(tick)) {
        date = date.add(1, 'M');
      } else {
        date = date.add(1, 'd');
      }
    };
    let i = 0;
    while (date <= moment(end) && i < 100) {
      i += 1;
      dates.push(date.format(DATE_FORMAT));
      nextDate();
    }
    return dates;
  }
  return [];
}

function compose (csv, request) {
  return new Promise((resolve, reject) => {
    if (!request.loaded) {
      reject(new Error('Summary data is not available'));
    } else {
      const {filters = {}} = request;
      const {tick = '1d'} = filters;
      let currentDate = getCurrentDate();
      if (/^1M$/i.test(tick)) {
        currentDate = moment.utc(currentDate).startOf('M');
      }
      currentDate = currentDate.format(DATE_FORMAT);
      getDateRanges(filters).forEach(column => csv.addColumn(column));
      csv.addRow(Fields.costPrevious);
      csv.addRow(Fields.costCurrent);
      csv.addRow(Fields.summaryPrevious);
      csv.addRow(Fields.summaryCurrent);
      csv.addRow(Fields.quota);
      const {values = [], quota} = request.value || {};
      values.forEach((item) => {
        const {dateValue, value, previous, cost, previousCost} = item;
        const column = moment.utc(dateValue).format(DATE_FORMAT);
        csv.setCellValue(Fields.costPrevious, column, previousCost);
        csv.setCellValue(Fields.costCurrent, column, cost);
        csv.setCellValue(Fields.summaryPrevious, column, previous);
        csv.setCellValue(Fields.summaryCurrent, column, value);
        if (column === currentDate) {
          csv.setCellValue(Fields.quota, column, quota);
        }
      });
      resolve();
    }
  });
}

export default compose;
