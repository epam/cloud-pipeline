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

import dayjs, {ensureDayjs} from '../../utils/dayjs';

const Period = {
  month: 'month',
  quarter: 'quarter',
  year: 'year',
  custom: 'custom',
  day: 'day',
};

const UNIT_GETTERS = {
  Y: 'year',
  M: 'month',
  D: 'date',
  Q: 'quarter',
};

function getTickFormat(start, end) {
  if (!start || !end) {
    return '1M';
  }
  return end.diff(start, 'month') >= 1 ? '1M' : '1d';
}

function buildRangeString({start, end}, period) {
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

function getRangeDescription({start, end}, period) {
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
        const startOfMonthDate = dayjs(start).startOf('month').date();
        const endOfMonthDate = dayjs(end).endOf('month').date();
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

function parseRangeString(string, period) {
  if (!string) {
    return {
      isCurrent: true,
    };
  }
  const [startStr, endStr] = string.split('|');
  let start, end, isCurrent;
  const now = dayjs.utc();
  const checkCurrent = (date, ...units) => {
    const checkUnit = (unit) => date[UNIT_GETTERS[unit]]() === now[UNIT_GETTERS[unit]]();
    return units.map(checkUnit).reduce((r, c) => r && c, true);
  };
  switch (period) {
    case Period.custom:
      start = dayjs.utc(startStr, 'YYYY-MM-DD').startOf('day');
      isCurrent = false;
      if (endStr) {
        end = dayjs.utc(endStr, 'YYYY-MM-DD').endOf('day');
      } else {
        end = dayjs(start).endOf('day');
      }
      break;
    case Period.year:
      start = dayjs.utc(startStr, 'YYYY').startOf('year');
      end = dayjs(start).endOf('year');
      isCurrent = checkCurrent(start, 'Y');
      break;
    case Period.quarter:
      start = dayjs.utc(startStr, 'YYYY-MM').startOf('quarter');
      end = dayjs(start).endOf('quarter');
      isCurrent = checkCurrent(start, 'Y', 'Q');
      break;
    case Period.month:
      start = dayjs.utc(startStr, 'YYYY-MM').startOf('month');
      end = dayjs(start).endOf('month');
      isCurrent = checkCurrent(start, 'Y', 'M');
      break;
    case Period.day:
      start = dayjs(startStr, 'YYYY-MM-DD').startOf('day');
      end = dayjs(start).endOf('day');
      isCurrent = checkCurrent(start, 'Y', 'M', 'D');
      break;
  }
  return {
    start,
    end,
    isCurrent,
  };
}

function buildRangeByDate(date, period) {
  const normalized = ensureDayjs(date);
  if (!normalized || period === Period.custom) {
    return {
      start: undefined,
      end: undefined,
    };
  }
  let unit;
  switch (period) {
    case Period.quarter:
      unit = 'quarter';
      break;
    case Period.year:
      unit = 'year';
      break;
    case Period.month:
      unit = 'month';
      break;
    default:
      unit = 'day';
      break;
  }
  const start = normalized.startOf(unit);
  const end = normalized.endOf(unit);
  return {
    start,
    end,
  };
}

const Range = {
  parse: parseRangeString,
  build: buildRangeString,
  buildRangeByDate,
  getRangeDescription,
};

function getCurrentDate() {
  return dayjs.utc().subtract(1, 'day');
}

function getPeriod(period, range) {
  const dateNow = getCurrentDate();
  let {start, end, isCurrent} = Range.parse(range, period);
  let before;
  const rangeIsSelected = !!start && !!end;
  let tickFormat;
  let previousStart;
  let previousEnd;
  let endStrict;
  let previousEndStrict;
  let previousShiftFn;
  let previousFilterFn;

  switch ((period || '').toLowerCase()) {
    case Period.month: {
      if (!rangeIsSelected) {
        start = dayjs(dateNow).startOf('month');
        end = dayjs(dateNow).endOf('month');
      }
      before = start ? dayjs(start).add(-1, 'month') : dayjs(dateNow).add(-1, 'month');
      tickFormat = '1d';
      previousStart = dayjs(start).add(-1, 'month');
      previousEnd = dayjs(previousStart).endOf('month');
      endStrict = dayjs(end);
      previousEndStrict = dayjs(previousEnd);
      if (isCurrent) {
        if (dateNow.valueOf() < endStrict.valueOf()) {
          endStrict = dayjs(dateNow);
        }
        const temp = dayjs(endStrict).add(-1, 'month');
        if (temp.valueOf() < previousEndStrict.valueOf()) {
          previousEndStrict = temp;
        }
      }
      endStrict = endStrict.endOf('day');
      previousEndStrict = previousEndStrict.endOf('day');
      const daysInMonth = start.daysInMonth();
      previousShiftFn = (mappedDate) => dayjs(mappedDate).add(1, 'month');
      previousFilterFn = (mappedDate) => mappedDate.date() <= daysInMonth;
      break;
    }
    case Period.quarter:
      if (!rangeIsSelected) {
        start = dayjs(dateNow).startOf('quarter');
        end = dayjs(start).endOf('quarter');
      }
      before = start ? dayjs(start).add(-1, 'quarter') : dayjs(dateNow).add(-1, 'quarter');
      tickFormat = '1M';
      previousStart = dayjs(start).add(-1, 'year');
      previousEnd = dayjs(end).add(-1, 'year');
      endStrict = dayjs(end);
      previousEndStrict = dayjs(previousEnd);
      if (isCurrent) {
        if (dateNow.valueOf() < endStrict.valueOf()) {
          endStrict = dayjs(dateNow);
        }
        const temp = dayjs(endStrict).add(-1, 'year');
        if (temp.valueOf() < previousEndStrict.valueOf()) {
          previousEndStrict = temp;
        }
      }
      endStrict = endStrict.endOf('day');
      previousEndStrict = previousEndStrict.endOf('day');
      previousShiftFn = (mappedDate) => dayjs(mappedDate).add(1, 'year');
      break;
    case Period.year:
      if (!rangeIsSelected) {
        start = dayjs(dateNow).startOf('year');
        end = dayjs(dateNow).endOf('year');
      }
      before = start ? dayjs(start).add(-1, 'year') : dayjs(dateNow).add(-1, 'year');
      tickFormat = '1M';
      previousStart = dayjs(start).add(-1, 'year');
      previousEnd = dayjs(end).add(-1, 'year');
      endStrict = dayjs(end);
      previousEndStrict = dayjs(previousEnd);
      if (isCurrent) {
        if (dateNow.valueOf() < endStrict.valueOf()) {
          endStrict = dayjs(dateNow);
        }
        const temp = dayjs(endStrict).add(-1, 'year');
        if (temp.valueOf() < previousEndStrict.valueOf()) {
          previousEndStrict = temp;
        }
      }
      previousShiftFn = (mappedDate) => dayjs(mappedDate).add(1, 'year');
      break;
    case Period.day:
      if (!rangeIsSelected) {
        start = dayjs().startOf('day');
        end = dayjs().endOf('day');
      }
      before = start ? dayjs(start).add(-1, 'day') : dayjs().add(-1, 'day');
      tickFormat = getTickFormat(start, end);
      previousStart = dayjs(start).add(-1, 'day');
      previousEnd = dayjs(end).add(-1, 'day');
      endStrict = dayjs(end);
      previousEndStrict = dayjs(previousEnd);
      if (isCurrent) {
        if (dayjs().valueOf() < endStrict.valueOf()) {
          endStrict = dayjs();
        }
        const temp = dayjs(endStrict).add(-1, 'day');
        if (temp.valueOf() < previousEndStrict.valueOf()) {
          previousEndStrict = temp;
        }
      }
      previousShiftFn = (mappedDate) => dayjs(mappedDate).add(1, 'day');
      break;
    default:
      tickFormat = getTickFormat(start, end);
      endStrict = dayjs(end);
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
    before: Range.build({start: before}, period),
  };
}

export {Period, getPeriod, Range, getTickFormat, getCurrentDate};
