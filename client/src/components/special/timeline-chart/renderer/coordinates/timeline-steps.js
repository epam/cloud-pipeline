import moment from 'moment-timezone';

const SECOND = 1;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const MONTH = 30 * DAY;
const YEAR = 365 * DAY;

const configurations = [
  {
    step: YEAR,
    unit: 'year',
    format: 'YYYY',
    maxLabel: 'WWW',
    variations: [1, 2, 4, 5, 10, 20],
    getNearest: (tick) => moment.unix(tick).startOf('year').unix(),
    change: {
      default: 'YYYY'
    }
  },
  {
    step: MONTH,
    unit: 'month',
    format: 'MMMM',
    smallFormat: 'MMM',
    variations: [2, 3, 6, 12],
    maxLabel: 'WWWWWWWWW',
    getNearest: (tick) => moment.unix(tick).startOf('year').unix(),
    change: {
      default: 'MMMM YYYY'
    }
  },
  {
    step: DAY,
    unit: 'date',
    format: 'D MMM',
    smallFormat: 'D',
    variations: [1, 2, 4],
    maxLabel: 'WW WWW',
    getNearest: (tick) => moment.unix(tick).startOf('month').unix(),
    change: {
      year: 'D MMM YYYY',
      default: 'D MMM'
    }
  },
  {
    step: HOUR,
    unit: 'hour',
    format: 'HH:mm',
    variations: [3, 6, 12],
    maxLabel: 'WW:WW',
    getNearest: (tick) => moment.unix(tick).startOf('date').unix(),
    change: {
      year: 'D MMM YYYY',
      default: 'D MMM'
    }
  },
  {
    step: MINUTE,
    unit: 'minute',
    format: 'HH:mm:ss',
    smallFormat: 'HH:mm',
    variations: [1, 5, 15, 30],
    maxLabel: 'WW:WW:WW',
    getNearest: (tick) => moment.unix(tick).startOf('hour').unix(),
    change: {
      year: 'D MMM YYYY',
      default: 'D MMM'
    }
  },
  {
    step: SECOND,
    unit: 'second',
    format: 'HH:mm:ss',
    variations: [1, 5, 15, 30],
    maxLabel: 'WW:WW:WW',
    getNearest: (tick) => moment.unix(tick).startOf('minute').unix(),
    change: {
      year: 'D MMM YYYY',
      default: 'D MMM'
    }
  }
];

export default configurations;
