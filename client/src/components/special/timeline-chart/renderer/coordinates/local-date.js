import moment from 'moment-timezone';

export default function localDate (date) {
  if (!date) {
    return '';
  }
  const localTime = moment.utc(date).toDate();
  return moment(localTime);
}
