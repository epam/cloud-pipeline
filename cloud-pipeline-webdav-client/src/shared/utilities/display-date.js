const moment = require('moment-timezone');

const dateDisplayFormat = 'DD MMM YYYY, HH:mm:ss';
const displayDate = (date, format = dateDisplayFormat) => {
  if (!date) {
    return date;
  }
  const localTime = moment(date).toDate();
  return moment(localTime).format(format);
};
module.exports = displayDate;
