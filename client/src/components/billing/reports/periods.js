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

function getRangeDescription ({start, end}, period) {
  switch (period) {
    case Period.custom:
      if (!start || !end) {
        return undefined;
      }
      return `${start.format('YYYY-MM')} - ${end.format('YYYY-MM')}`;
    case Period.year:
      if (!start) {
        return undefined;
      }
      return start.format('YYYY');
    case Period.quarter:
      if (!start) {
        return undefined;
      }
      return `${start.format('Q')} quarter ${start.format('YYYY')}`;
    default:
      if (!start) {
        return undefined;
      }
      return start.format('MMMM YYYY');
  }
}

function parseRangeString (string, period) {
  if (!string) {
    return {
      isCurrent: true
    };
  }
  const [startStr, endStr] = string.split('|');
  let start, end, isCurrent;
  const checkCurrent = (date, ...units) => {
    const checkUnit = (unit) => date.get(unit) === moment.utc().get(unit);
    return units.map(checkUnit).reduce((r, c) => r && c, true);
  };
  switch (period) {
    case Period.custom:
      start = moment.utc(startStr, 'YYYY-MM').startOf('M');
      isCurrent = false;
      if (endStr) {
        end = moment.utc(endStr, 'YYYY-MM').endOf('M');
      } else {
        end = moment(start).endOf('M');
      }
      break;
    case Period.year:
      start = moment.utc(startStr, 'YYYY').startOf('Y');
      end = moment(start).endOf('Y');
      isCurrent = checkCurrent(start, 'Y');
      break;
    case Period.quarter:
      start = moment.utc(startStr, 'YYYY-MM').startOf('Q');
      end = moment(start).endOf('Q');
      isCurrent = checkCurrent(start, 'Y', 'Q');
      break;
    case Period.month:
      start = moment.utc(startStr, 'YYYY-MM').startOf('M');
      end = moment(start).endOf('M');
      isCurrent = checkCurrent(start, 'Y', 'M');
      break;
  }
  return {
    start,
    end,
    isCurrent
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
  buildRangeByDate,
  getRangeDescription
};

function getCurrentDate () {
  return moment.utc().add(-1, 'd');
}

function getPeriod (period, range) {
  const dateNow = getCurrentDate();
  let {start, end, isCurrent} = Range.parse(range, period);
  let before = start ? moment(start).add(-1, 'd') : moment(dateNow).add(-1, 'd');
  const rangeIsSelected = !!start && !!end;
  let tickFormat;
  let previousStart;
  let previousEnd;
  let endStrict;
  let previousEndStrict;
  let previousShiftFn;
  let previousFilterFn;

  switch ((period || '').toLowerCase()) {
    case Period.month:
      if (!rangeIsSelected) {
        start = moment(dateNow).startOf('month');
        end = moment(dateNow).endOf('month');
      }
      before = start ? moment(start).add(-1, 'M') : moment(dateNow).add(-1, 'M');
      tickFormat = getTickFormat(start, end);
      previousStart = moment(start).add(-1, 'M');
      previousEnd = moment(previousStart).endOf('M');
      endStrict = moment(end);
      previousEndStrict = moment(previousEnd);
      if (isCurrent) {
        if (dateNow < endStrict) {
          endStrict = moment(dateNow);
        }
        const temp = moment(endStrict).add(-1, 'M');
        if (temp < previousEndStrict) {
          previousEndStrict = temp;
        }
      }
      const daysInMonth = start.daysInMonth();
      previousShiftFn = (momentDate) => moment(momentDate).add(1, 'M');
      previousFilterFn = (momentDate) => momentDate.get('D') <= daysInMonth;
      break;
    case Period.quarter:
      if (!rangeIsSelected) {
        start = moment(dateNow).startOf('Q');
        end = moment(start).endOf('Q');
      }
      before = start ? moment(start).add(-1, 'Q') : moment(dateNow).add(-1, 'Q');
      tickFormat = getTickFormat(start, end);
      previousStart = moment(start).add(-1, 'y');
      previousEnd = moment(end).add(-1, 'y');
      endStrict = moment(end);
      previousEndStrict = moment(previousEnd);
      if (isCurrent) {
        if (dateNow < endStrict) {
          endStrict = moment(dateNow);
        }
        const temp = moment(endStrict).add(-1, 'y');
        if (temp < previousEndStrict) {
          previousEndStrict = temp;
        }
      }
      previousShiftFn = (momentDate) => moment(momentDate).add(1, 'y');
      break;
    case Period.year:
      if (!rangeIsSelected) {
        start = moment(dateNow).startOf('Y');
        end = moment(dateNow).endOf('Y');
      }
      before = start ? moment(start).add(-1, 'Y') : moment(dateNow).add(-1, 'Y');
      tickFormat = getTickFormat(start, end);
      previousStart = moment(start).add(-1, 'y');
      previousEnd = moment(end).add(-1, 'y');
      endStrict = moment(end);
      previousEndStrict = moment(previousEnd);
      if (isCurrent) {
        if (dateNow < endStrict) {
          endStrict = moment(dateNow);
        }
        const temp = moment(endStrict).add(-1, 'y');
        if (temp < previousEndStrict) {
          previousEndStrict = temp;
        }
      }
      previousShiftFn = (momentDate) => moment(momentDate).add(1, 'y');
      break;
    default:
      tickFormat = getTickFormat(start, end);
      endStrict = moment(end);
      before = undefined;
      break;
  }
  return {
    name: period,
    tick: tickFormat,
    start,
    end,
    endStrict,
    previousStart,
    previousEnd,
    previousEndStrict,
    previousShiftFn,
    previousFilterFn,
    current: Range.build({start}, period),
    before: Range.build({start: before}, period)
  };
}

export {
  Period,
  getPeriod,
  Range,
  getTickFormat,
  getCurrentDate
};
