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

const Period = {
  month: 'month',
  quarter: 'quarter',
  year: 'year',
  custom: 'custom'
};

function getTickFormat (start, end) {
  if (!start || !end) {
    return '1M';
  }
  return moment.duration(end.diff(start)).asMonths() > 3 ? '1M' : '1d';
}

function buildRangeString ({start, end}, period) {
  switch (period) {
    case Period.custom:
      if (!start || !end) {
        return undefined;
      }
      return `${start.format('YYYY-MM')}|${end.format('YYYY-MM')}`;
    case Period.year:
      if (!start) {
        return undefined;
      }
      return start.format('YYYY');
    default:
      if (!start) {
        return undefined;
      }
      return start.format('YYYY-MM');
  }
}

function parseRangeString (string, period) {
  if (!string) {
    return {};
  }
  const [startStr, endStr] = string.split('|');
  let start, end;
  switch (period) {
    case Period.custom:
      start = moment(startStr, 'YYYY-MM').startOf('M');
      if (endStr) {
        end = moment(endStr, 'YYYY-MM').endOf('M');
      } else {
        end = moment(start).endOf('M');
      }
      break;
    case Period.year:
      start = moment(startStr, 'YYYY').startOf('Y');
      end = moment(start).endOf('Y');
      break;
    case Period.quarter:
      start = moment(startStr, 'YYYY-MM').startOf('Q');
      end = moment(start).endOf('Q');
      break;
    case Period.month:
      start = moment(startStr, 'YYYY-MM').startOf('M');
      end = moment(start).endOf('M');
      break;
  }
  return {
    start,
    end
  };
}

function buildRangeByDate (date, period) {
  if (!date || period === Period.custom) {
    return {
      start: undefined,
      end: undefined
    };
  }
  let unit = 'M';
  switch (period) {
    case Period.quarter:
      unit = 'Q';
      break;
    case Period.year:
      unit = 'Y';
      break;
    default:
    case Period.month:
      unit = 'M';
      break;
  }
  const start = moment(date).startOf(unit);
  const end = moment(date).endOf(unit);
  return {
    start,
    end
  };
}

const Range = {
  parse: parseRangeString,
  build: buildRangeString,
  buildRangeByDate
};

function getPeriod (period, range) {
  const now = Date.now();
  const dateNow = moment.utc(now);
  let {start, end} = Range.parse(range, period);
  const rangeIsSelected = !!start && !!end;
  let tickFormat;
  let previousStart;
  let previousEnd;
  let previousShiftFn;
  let previousFilterFn;

  switch ((period || '').toLowerCase()) {
    case Period.month:
      if (!rangeIsSelected) {
        start = moment(dateNow).startOf('month');
        end = moment(dateNow).endOf('month');
      }
      tickFormat = getTickFormat(start, end);
      previousStart = moment(start).add(-1, 'M');
      previousEnd = moment(previousStart).endOf('M');
      const daysInMonth = start.daysInMonth();
      previousShiftFn = (momentDate) => moment(momentDate).add(1, 'M');
      previousFilterFn = (momentDate) => momentDate.get('D') <= daysInMonth;
      break;
    case Period.quarter:
      if (!rangeIsSelected) {
        start = moment(dateNow).startOf('Q');
        end = moment(start).endOf('Q');
      }
      tickFormat = getTickFormat(start, end);
      previousStart = moment(start).add(-1, 'y');
      previousEnd = moment(end).add(-1, 'y');
      previousShiftFn = (momentDate) => moment(momentDate).add(1, 'y');
      break;
    case Period.year:
      if (!rangeIsSelected) {
        start = moment(dateNow).startOf('Y');
        end = moment(dateNow).endOf('Y');
      }
      tickFormat = getTickFormat(start, end);
      previousStart = moment(start).add(-1, 'y');
      previousEnd = moment(end).add(-1, 'y');
      previousShiftFn = (momentDate) => moment(momentDate).add(1, 'y');
      break;
    default:
      tickFormat = getTickFormat(start, end);
      break;
  }
  return {
    name: period,
    tick: tickFormat,
    start,
    end,
    previousStart,
    previousEnd,
    previousShiftFn,
    previousFilterFn
  };
}

export {
  Period,
  getPeriod,
  Range
};
