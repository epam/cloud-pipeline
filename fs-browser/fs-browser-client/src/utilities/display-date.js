import dateFns from 'date-fns';

const dateDisplayFormat = 'YYYY-MM-DD HH:mm:ss';
const displayDate = (date, format = dateDisplayFormat) => {
  if (!date) {
    return '';
  }
  return dateFns.format(new Date(date), format);
};

export default displayDate;
