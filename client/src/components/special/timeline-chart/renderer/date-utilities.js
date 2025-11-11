/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import moment from 'moment-timezone';

function isNumber (number) {
  return number !== undefined && number !== null && !Number.isNaN(Number(number));
}

function isDate (date) {
  return isNumber(date) || (typeof date === 'string') || moment.isMoment(date);
}

function parseDate (date) {
  let dateValue, unix;
  if (moment.isMoment(date)) {
    dateValue = date;
    unix = dateValue.unix();
  } else if (isNumber(date)) {
    unix = Number(date);
    dateValue = moment.unix(unix);
  } else if (typeof date === 'string') {
    dateValue = moment.utc(date).local();
    if (!dateValue.isValid()) {
      dateValue = undefined;
    } else {
      unix = dateValue.unix();
    }
  }
  if (unix !== undefined && dateValue !== undefined) {
    return {
      unix,
      date: dateValue
    };
  }
  return undefined;
}

export {
  parseDate,
  isDate,
  isNumber
};
