/*
 * Configured dayjs instance with plugins used across the app.
 * Import from here instead of 'dayjs' directly so plugins are registered once.
 */
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import timezone from 'dayjs/plugin/timezone';
import customParseFormat from 'dayjs/plugin/customParseFormat';
import quarterOfYear from 'dayjs/plugin/quarterOfYear';
import duration from 'dayjs/plugin/duration';
import relativeTime from 'dayjs/plugin/relativeTime';
import isSameOrBefore from 'dayjs/plugin/isSameOrBefore';
import isSameOrAfter from 'dayjs/plugin/isSameOrAfter';

dayjs.extend(utc);
dayjs.extend(timezone);
dayjs.extend(customParseFormat);
dayjs.extend(quarterOfYear);
dayjs.extend(duration);
dayjs.extend(relativeTime);
dayjs.extend(isSameOrBefore);
dayjs.extend(isSameOrAfter);

/** Default format for stored filter timestamps (API payloads). */
export const DATE_FORMAT = 'YYYY-MM-DD HH:mm:ss';

/**
 * @param {*} v
 * @returns {boolean}
 */
export function isDayjs(v) {
  return (
    v != null &&
    typeof v.isValid === 'function' &&
    typeof v.format === 'function' &&
    typeof v.unix === 'function'
  );
}

/**
 * @param {number} ts Unix timestamp in seconds
 * @returns {import('dayjs').Dayjs}
 */
export function fromUnix(ts) {
  return dayjs.unix(ts);
}

/**
 * Replaces moment([year, month, date, hour, minute, second]).
 * Month is 0-indexed, matching moment semantics.
 * @param {number[]} parts
 * @returns {import('dayjs').Dayjs}
 */
export function calendarDate(parts) {
  const [year = 0, month = 0, date = 1, hour = 0, minute = 0, second = 0] = parts;
  return dayjs(new Date(year, month, date, hour, minute, second));
}

/**
 * @param {*} input
 * @param {{withoutSuffix?: boolean}} [options]
 * @returns {string}
 */
export function fromNowUtc(input, {withoutSuffix = false} = {}) {
  const utc = toUtcDayjs(input);
  return utc ? utc.fromNow(withoutSuffix) : '';
}

/**
 * Parse a UTC-formatted string into local dayjs for date pickers.
 * @param {string | undefined} stored
 * @param {string} [format]
 * @returns {import('dayjs').Dayjs | undefined}
 */
export function parseStoredDate(stored, format = DATE_FORMAT) {
  if (!stored) {
    return undefined;
  }
  const utc = dayjs.utc(stored, format);
  return utc.isValid() ? dayjs(utc.toDate()) : undefined;
}

/**
 * Format a picker date as UTC string for API/filter storage.
 * @param {*} date
 * @param {string} [format]
 * @returns {string | undefined}
 */
export function formatStoredDate(date, format = DATE_FORMAT) {
  if (!date) {
    return undefined;
  }
  const local = ensureDayjs(date);
  return local ? dayjs.utc(local.valueOf()).format(format) : undefined;
}

/**
 * Normalize dayjs / moment / Date / string / number → dayjs (or null).
 * @param {*} input
 * @returns {import('dayjs').Dayjs | null}
 */
export function ensureDayjs(input) {
  if (!input) {
    return null;
  }
  if (isDayjs(input)) {
    return input.isValid() ? input : null;
  }
  const ms = typeof input.valueOf === 'function' ? input.valueOf() : input;
  const result = dayjs(ms);
  return result.isValid() ? result : null;
}

/**
 * Normalize string | number | Date | moment | dayjs → UTC dayjs (or null).
 * @param {*} input
 * @returns {import('dayjs').Dayjs | null}
 */
export function toUtcDayjs(input) {
  if (!input) {
    return null;
  }
  const ms = typeof input.valueOf === 'function' ? input.valueOf() : input;
  const result = dayjs.utc(ms);
  return result.isValid() ? result : null;
}

/**
 * Convert a local calendar date (from a picker) to UTC for API payloads.
 * Mirrors legacy `localDateToUTC`: local wall-clock instant → UTC formatting.
 * @param {*} input
 * @returns {import('dayjs').Dayjs | null}
 */
export function localDateToUtcDayjs(input) {
  if (!input) {
    return null;
  }
  const local = dayjs(input);
  return local.isValid() ? dayjs.utc(local.valueOf()) : null;
}

/**
 * Start or end of the selected local calendar day (not UTC midnight on that date).
 * @param {import('dayjs').Dayjs} selectedDate
 * @param {boolean} [endOfDay=false]
 * @returns {import('dayjs').Dayjs | null}
 */
export function calendarDayBoundary(selectedDate, endOfDay = false) {
  if (!selectedDate || !selectedDate.isValid()) {
    return null;
  }
  let result = dayjs()
    .year(selectedDate.year())
    .month(selectedDate.month())
    .date(selectedDate.date())
    .startOf('day');
  if (endOfDay) {
    result = result.endOf('day');
  }
  return result;
}

/**
 * Restore a stored filter date for antd Calendar (local calendar day).
 * @param {*} storedDate
 * @returns {import('dayjs').Dayjs | undefined}
 */
export function filterDateForCalendar(storedDate) {
  if (!storedDate) {
    return undefined;
  }
  const local = dayjs(storedDate);
  return local.isValid() ? local : undefined;
}

/**
 * Parse a UTC date string from the API into local dayjs for date pickers.
 * @param {string | undefined} string
 * @returns {import('dayjs').Dayjs | undefined}
 */
export function utcStringToLocalDayjs(string) {
  if (!string) {
    return undefined;
  }
  const utc = toUtcDayjs(string);
  return utc ? dayjs(utc.toDate()) : undefined;
}

export default dayjs;
