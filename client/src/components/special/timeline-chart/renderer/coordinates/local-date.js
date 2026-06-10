import dayjs, {toUtcDayjs} from '../../../../../utils/dayjs';

export default function localDate(date) {
  if (!date) {
    return '';
  }
  const utc = toUtcDayjs(date);
  return utc ? dayjs(utc.toDate()) : '';
}
