import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';

dayjs.extend(utc);

const displayFormat = 'YYYY-MM-DD, HH:mm:ss';

const displayDate = (date: string, format: string = displayFormat): string => {
  if (!date) {
    return '';
  }
  return dayjs.utc(date).local().format(format);
};

export { displayDate };
