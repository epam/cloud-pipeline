/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
  custom: 'custom',
  day: 'day'
};

function getTickFormat (start, end) {
  if (!start || !end) {
    return '1M';
  }
  return end.diff(start, 'month') >= 1 ? '1M' : '1d';
}

function buildRangeString ({start, end}, period) {
  switch (period) {
    case Period.custom:
      if (!start || !end) {
        return undefined;
      }
      return `${start.format('YYYY-MM-DD')}|${end.format('YYYY-MM-DD')}`;
    case Period.year:
      if (!start) {
        return undefined;
      }
      return start.format('YYYY');
    case Period.day:
      if (!start) {
        return undefined;
      }
      return start.format('YYYY-MM-DD');
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
      return `${start.format('YYYY-MM-DD')} - ${end.format('YYYY-MM-DD')}`;
    case Period.year:
      if (!start) {
        return undefined;
      }
      return start.format('YYYY');
    case Period.day:
      if (!start) {
        return undefined;
      }
      return start.format('YYYY-MM-DD');
    case Period.quarter:
      if (!start) {
        return undefined;
      }
      return `${start.format('Q')} quarter ${start.format('YYYY')}`;
    default:
      if (!start) {
        return undefined;
      }
      if (start && end) {
        const startOfMonthDate = moment(start).startOf('M').date();
        const endOfMonthDate = moment(end).endOf('M').date();
        if (startOfMonthDate !== start.date() || endOfMonthDate !== end.date()) {
          if (start.date() === end.date()) {
            return start.format('D MMMM YYYY');
          }
          return `${start.date()} - ${end.date()} ${start.format('MMMM YYYY')}`;
        }
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
      start = moment.utc(startStr, 'YYYY-MM-DD').startOf('D');
      isCurrent = false;
      if (endStr) {
        end = moment.utc(endStr, 'YYYY-MM-DD').endOf('D');
      } else {
        end = moment(start).endOf('D');
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
    case Period.day:
      start = moment(startStr, 'YYYY-MM-DD').startOf('D');
      end = moment(start).endOf('D');
      isCurrent = checkCurrent(start, 'Y', 'M', 'D');
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
  let unit;
  switch (period) {
    case Period.quarter:
      unit = 'Q';
      break;
    case Period.year:
      unit = 'Y';
      break;
    case Period.month:
      unit = 'M';
      break;
    default:
      unit = 'D';
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
      endStrict = endStrict.endOf('D');
      previousEndStrict = previousEndStrict.endOf('D');
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
      endStrict = endStrict.endOf('D');
      previousEndStrict = previousEndStrict.endOf('D');
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
    case Period.day:
      if (!rangeIsSelected) {
        start = moment().startOf('D');
        end = moment().endOf('D');
      }
      before = start ? moment(start).add(-1, 'D') : moment().add(-1, 'D');
      tickFormat = getTickFormat(start, end);
      previousStart = moment(start).add(-1, 'd');
      previousEnd = moment(end).add(-1, 'd');
      endStrict = moment(end);
      previousEndStrict = moment(previousEnd);
      if (isCurrent) {
        if (moment() < endStrict) {
          endStrict = moment();
        }
        const temp = moment(endStrict).add(-1, 'd');
        if (temp < previousEndStrict) {
          previousEndStrict = temp;
        }
      }
      previousShiftFn = (momentDate) => moment(momentDate).add(1, 'd');
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
