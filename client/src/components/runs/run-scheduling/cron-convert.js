import moment from 'moment-timezone';

export const ruleModes = {
  daily: 'daily',
  weekly: 'weekly'
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
      const [, minutes, hours, dayOfMonth, , dayOfWeek] = parts;
      return {
        minutes,
        hours,
        dayOfMonth,
        dayOfWeek
      };
    } else if (parts.length === 5) {
      const [minutes, hours, dayOfMonth, , dayOfWeek] = parts;

      return {
        minutes,
        hours,
        dayOfMonth,
        dayOfWeek
      };
    }
    return null;
  }

  static convertToRuleScheduleObject (cronExpression) {
    const cronParts = CronConvert._getCronParts(cronExpression);

    let time = {};
    if (!isNaN(+cronParts.minutes)) {
      time.minutes = +cronParts.minutes;
    }
    if (!isNaN(+cronParts.hours)) {
      time.hours = +cronParts.hours;
    }
    let mode;
    let every;
    let dayOfWeek;
    if ((cronParts.dayOfWeek === '*' || cronParts.dayOfWeek === '?') &&
      cronParts.dayOfMonth.includes('/')) {
      mode = ruleModes.daily;
      every = cronParts.dayOfMonth.split('/')[1];
    } else {
      mode = ruleModes.weekly;
      dayOfWeek = cronParts.dayOfWeek.replace('7', '0').includes(',')
        ? cronParts.dayOfWeek.split(',')
        : [cronParts.dayOfWeek];
    }
    // {
    //   mode: ruleModes.daily | ruleModes.weekly,
    //   dayOfWeek: [], | every: 1,
    //   time: {
    //     hours: 0,
    //     minutes: 0
    //   }
    // }
    return {
      mode,
      every,
      dayOfWeek,
      time
    };
  }

  /**
   * Returns cron expression according to given params
   * @param scheduleObject {Object}
   * @param scheduleObject.mode {ruleModes.weekly|ruleModes.daily} - rule recurrence mode (daily or weekly)
   * @param scheduleObject.dayOfWeek {null|Array} - An array of day(s) of week for weekly recurrence mode
   * @param scheduleObject.every {null|Number|String} - day of month for daily recurrence mode
   * @param scheduleObject.time {Object} - time object
   * @param scheduleObject.time.hours {Number|String} - hours
   * @param scheduleObject.time.minutes {Number|String} - minutes
   * @param cronLength {Number} - Resulting cron expression string format (5, 6 or 7 parts)
   * @return {String|null} - Cron expression string
   * */
  static convertToCronString ({
    mode,
    dayOfWeek,
    every,
    time: {
      hours,
      minutes
    }
  },
  cronLength = 6
  ) {
    let cron5;
    if (mode === ruleModes.daily) {
      cron5 = `${minutes} ${hours} */${every} * ?`;
    }
    if (mode === ruleModes.weekly) {
      cron5 = `${minutes} ${hours} ? * ${dayOfWeek.sort().join(',')}`;
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
