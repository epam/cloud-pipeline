import moment from 'moment-timezone';
import {DAYS, COMPUTED_DAYS, ORDINALS} from './forms';

export const ruleModes = {
  daily: 'daily',
  weekly: 'weekly',
  monthly: 'monthly',
  yearly: 'yearly'
};

const DECODERS = {
  [ruleModes.daily]: (parts) => ({
    mode: ruleModes.daily,
    every: parts.dayOfMonth.split('/')[1]
  }),
  [ruleModes.weekly]: (parts) => ({
    mode: ruleModes.weekly,
    dayOfWeek: parts.dayOfWeek.includes(',')
      ? parts.dayOfWeek.replace('0', '7').split(',')
      : [parts.dayOfWeek.replace('0', '7')]
  }),
  [ruleModes.monthly]: ({dayOfMonth, dayOfWeek, month}) => {
    if (dayOfMonth === '?') {
      // Every Nth day of week
      const [dw, ...ordinal] = dayOfWeek;
      return {
        mode: ruleModes.monthly,
        daySelectorMode: 'computed',
        ordinal: ordinal.join(''),
        day: dw,
        dayNumber: 1,
        every: Number(month.split('/')[1])
      };
    } else if (dayOfMonth.includes('W') && dayOfWeek === '?') {
      // Every # weekday
      const ordinal = dayOfMonth.replace('W', '');
      return {
        mode: ruleModes.monthly,
        daySelectorMode: 'computed',
        ordinal: ordinal === 'L' ? ordinal : `#${ordinal}`,
        day: COMPUTED_DAYS.weekday.key,
        dayNumber: 1,
        every: Number(month.split('/')[1])
      };
    } else if (dayOfMonth === 'L') {
      // Every Last (Weekday | Day | day of week)
      return {
        mode: ruleModes.monthly,
        daySelectorMode: 'computed',
        ordinal: dayOfMonth,
        day: COMPUTED_DAYS.day.key,
        dayNumber: 1,
        every: Number(month.split('/')[1])
      };
    }
    return {
      // every # Day
      mode: ruleModes.monthly,
      daySelectorMode: 'numeric',
      ordinal: ORDINALS[0].cronCode,
      day: DAYS[0].key,
      dayNumber: dayOfMonth,
      every: Number(month.split('/')[1])
    };
  },
  [ruleModes.yearly]: ({dayOfMonth, dayOfWeek, month}) => {
    if (dayOfMonth === '?') {
      // Every Nth day of week
      const [dw, ...ordinal] = dayOfWeek;
      return {
        mode: ruleModes.yearly,
        daySelectorMode: 'computed',
        ordinal: ordinal.join(''),
        day: dw,
        dayNumber: 1,
        month
      };
    } else if (dayOfMonth.includes('W') && dayOfWeek === '?') {
      // Every # weekday
      const ordinal = dayOfMonth.replace('W', '');
      return {
        mode: ruleModes.yearly,
        daySelectorMode: 'computed',
        ordinal: ordinal === 'L' ? ordinal : `#${ordinal}`,
        day: COMPUTED_DAYS.weekday.key,
        dayNumber: 1,
        month
      };
    } else if (dayOfMonth === 'L') {
      // Every Last (Weekday | Day | day of week)
      return {
        mode: ruleModes.yearly,
        daySelectorMode: 'computed',
        ordinal: dayOfMonth,
        day: COMPUTED_DAYS.day.key,
        dayNumber: 1,
        month
      };
    }
    return {
      // every # Day
      mode: ruleModes.yearly,
      daySelectorMode: 'numeric',
      ordinal: ORDINALS[0].cronCode,
      day: DAYS[0].key,
      dayNumber: dayOfMonth,
      month
    };
  }
};

export function isTimeZoneEqualCurrent (timeZone) {
  const current = moment.tz.guess();
  if (!timeZone) {
    return true;
  }
  return current === timeZone;
}

export class CronConvert {
  static _getCronParts (expression) {
    if (!expression || expression.length === 0) {
      return null;
    }
    const parts = expression.split(' ').filter(Boolean);
    if (parts.length > 5) {
      const [, minutes, hours, dayOfMonth, month, dayOfWeek] = parts;
      return {
        minutes,
        hours,
        month,
        dayOfMonth,
        dayOfWeek
      };
    } else if (parts.length === 5) {
      const [minutes, hours, dayOfMonth, month, dayOfWeek] = parts;
      return {
        minutes,
        hours,
        dayOfMonth,
        month,
        dayOfWeek
      };
    }
    return null;
  }

  static convertToRuleScheduleObject (cronExpression) {
    const parts = CronConvert._getCronParts(cronExpression);
    let schedule = {
      mode: undefined,
      every: undefined,
      day: undefined,
      dayNumber: undefined,
      dayOfWeek: undefined,
      daySelectorMode: undefined,
      month: undefined,
      ordinal: undefined,
      time: {
        hours: undefined,
        minutes: undefined
      }
    };
    if (!isNaN(+parts.minutes)) {
      schedule.time.minutes = +parts.minutes;
    }
    if (!isNaN(+parts.hours)) {
      schedule.time.hours = +parts.hours;
    }
    if (
      parts.dayOfMonth.includes('/') &&
      parts.month === '*' &&
      (parts.dayOfWeek === '*' || parts.dayOfWeek === '?')
    ) {
      schedule = {...schedule, ...DECODERS[ruleModes.daily](parts)};
    } else if (
      parts.dayOfMonth === '?' &&
      parts.dayOfWeek?.split(',').every(d => !isNaN(d))
    ) {
      schedule = {...schedule, ...DECODERS[ruleModes.weekly](parts)};
    } else if (parts.month.includes('/') && !isNaN(parts.month.split('/')[1])) {
      schedule = {...schedule, ...DECODERS[ruleModes.monthly](parts)};
    } else if (!isNaN(parts.month)) {
      schedule = {...schedule, ...DECODERS[ruleModes.yearly](parts)};
    }
    return schedule;
  }

  /**
   * Returns cron expression according to given params
   * @param scheduleObject {Object}
   * @param scheduleObject.mode {ruleModes.weekly|ruleModes.daily} -
   *    rule recurrence mode (daily or weekly)
   * @param scheduleObject.dayOfWeek {null|Array} -
   *    An array of day(s) of week for weekly recurrence mode
   * @param scheduleObject.every {null|Number|String} - day of month for daily recurrence mode
   * @param scheduleObject.time {Object} - time object
   * @param scheduleObject.time.hours {Number|String} - hours
   * @param scheduleObject.time.minutes {Number|String} - minutes
   * @param cronLength {Number} - Resulting cron expression string format (5, 6 or 7 parts)
   * @return {String|null} - Cron expression string
   * */
  static convertToCronString ({
    mode,
    every,
    day,
    dayNumber,
    dayOfWeek,
    daySelectorMode,
    month,
    ordinal,
    time: {
      hours,
      minutes
    }
  },
  cronLength = 6
  ) {
    const convertSunday = (weekday) => {
      if (+weekday === 0) {
        return 7;
      }
      return weekday;
    };
    let cron5;
    switch (mode) {
      case ruleModes.daily:
        cron5 = `${minutes} ${hours} */${every} * ?`;
        break;
      case ruleModes.weekly:
        cron5 = `${minutes} ${hours} ? * ${dayOfWeek.map(convertSunday).sort().join(',')}`;
        break;
      case ruleModes.monthly:
        if (daySelectorMode === 'numeric') {
          cron5 = `${minutes} ${hours} ${dayNumber} 1/${every} ?`;
        } else if (daySelectorMode === 'computed' && ordinal) {
          if (day === COMPUTED_DAYS.day.key) {
            cron5 = `${minutes} ${hours} ${ordinal.replace('#', '')} 1/${every} ?`;
          } else if (day === COMPUTED_DAYS.weekday.key) {
            cron5 = `${minutes} ${hours} ${ordinal.replace('#', '')}W 1/${every} ?`;
          } else {
            cron5 = `${minutes} ${hours} ? 1/${every} ${day}${ordinal}`;
          }
        }
        break;
      case ruleModes.yearly:
        if (daySelectorMode === 'numeric') {
          cron5 = `${minutes} ${hours} ${dayNumber} ${month} ?`;
        } else if (daySelectorMode === 'computed' && ordinal) {
          if (day === COMPUTED_DAYS.day.key) {
            cron5 = `${minutes} ${hours} ${ordinal.replace('#', '')} ${month} ?`;
          } else if (day === COMPUTED_DAYS.weekday.key) {
            cron5 = `${minutes} ${hours} ${ordinal.replace('#', '')}W ${month} ?`;
          } else {
            cron5 = `${minutes} ${hours} ? ${month} ${day}${ordinal}`;
          }
        }
        break;
      default:
        break;
    }
    if (cronLength === 6) {
      return `0 ${cron5}`;
    }
    if (cronLength === 7) {
      return `0 ${cron5} *`;
    }
    return cron5 || null;
  }
}
