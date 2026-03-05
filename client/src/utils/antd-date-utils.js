/*
 * Helpers for using antd v5 DatePicker/TimePicker (dayjs) with app code that uses moment.
 * Use at component boundaries: convert moment -> dayjs for value/defaultValue,
 * and dayjs -> moment in onChange when the rest of the app expects moment.
 */
import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
import moment from 'moment-timezone';

dayjs.extend(utc);

/**
 * @param {import('moment').Moment | string | number | null | undefined} m
 * @returns {import('dayjs').Dayjs | null}
 */
export function momentToDayjs (m) {
  if (!m) {
    return null;
  }
  if (typeof m === 'string' || typeof m === 'number') {
    const result = dayjs(m);
    return result.isValid() ? result : null;
  }
  if (typeof m.valueOf === 'function') {
    const result = dayjs(m.valueOf());
    return result.isValid() ? result : null;
  }
  return null;
}

/**
 * @param {import('dayjs').Dayjs | null | undefined} d
 * @returns {import('moment').Moment | null}
 */
export function dayjsToMoment (d) {
  if (!d) {
    return null;
  }
  if (typeof d.isValid === 'function' && !d.isValid()) {
    return null;
  }
  const result = moment(d.valueOf());
  return result.isValid() ? result : null;
}
