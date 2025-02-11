import dayjs from 'dayjs';
import { defaultDateDisplayFormat, displayDate } from '../../src';

describe('displayDate', () => {
  const defaultFormat = defaultDateDisplayFormat;

  test('should return formatted date in local time when valid UTC date string is provided', () => {
    const utcDate = '2025-02-06T12:00:00Z'; // UTC time
    const formattedDate = dayjs.utc(utcDate).local().format(defaultFormat);
    expect(displayDate(utcDate)).toBe(formattedDate);
  });

  test('should return formatted date using custom format when valid UTC date and custom format are provided', () => {
    const utcDate = '2025-02-06T12:00:00Z'; // UTC time
    const customFormat = 'MMMM D, YYYY - HH:mm';
    const formattedDate = dayjs.utc(utcDate).local().format(customFormat);
    expect(displayDate(utcDate, customFormat)).toBe(formattedDate);
  });

  test('should return an empty string when an empty string is provided as the date', () => {
    const emptyDate = '';
    expect(displayDate(emptyDate)).toBe('');
  });

  test('should return an empty string when undefined is provided as the date', () => {
    expect(displayDate(undefined)).toBe('');
  });

  test('should return formatted date in local time when valid ISO 8601 UTC date string is provided', () => {
    const utcDate = '2025-02-06T12:00:00Z'; // UTC time
    const formattedDate = dayjs.utc(utcDate).local().format(defaultFormat);
    expect(displayDate(utcDate)).toBe(formattedDate);
  });
});
