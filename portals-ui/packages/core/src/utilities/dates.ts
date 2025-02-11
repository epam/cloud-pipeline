import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';

dayjs.extend(utc);

const defaultDateDisplayFormat = 'YYYY-MM-DD, HH:mm:ss';

const displayDate = (utcDate: string | undefined, format: string = defaultDateDisplayFormat): string => {
  if (!utcDate) {
    return '';
  }
  return dayjs.utc(utcDate).local().format(format);
};

export { displayDate, defaultDateDisplayFormat };
