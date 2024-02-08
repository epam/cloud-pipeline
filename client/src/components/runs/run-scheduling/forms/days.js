/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

export const ORDINALS = [{
  title: 'First',
  cronCode: '#1'
}, {
  title: 'Second',
  cronCode: '#2'
}, {
  title: 'Third',
  cronCode: '#3'
}, {
  title: 'Fourth',
  cronCode: '#4'
}, {
  title: 'Last',
  cronCode: 'L'
}];

export function getOrdinalSuffix (number) {
  if (Number.isNaN(number)) {
    return '';
  }
  const n = Number(number);
  if (n > 3 && n < 21) return 'th';
  switch (n % 10) {
    case 1: return 'st';
    case 2: return 'nd';
    case 3: return 'rd';
    default: return 'th';
  }
};

export const DAYS = [
  {title: 'Monday', key: '2'},
  {title: 'Tuesday', key: '3'},
  {title: 'Wednesday', key: '4'},
  {title: 'Thursday', key: '5'},
  {title: 'Friday', key: '6'},
  {title: 'Saturday', key: '7'},
  {title: 'Sunday', key: '1'}
];

export const MONTHS = [
  {title: 'January', key: '1'},
  {title: 'February', key: '2'},
  {title: 'March', key: '3'},
  {title: 'April', key: '4'},
  {title: 'May', key: '5'},
  {title: 'June', key: '6'},
  {title: 'July', key: '7'},
  {title: 'August', key: '8'},
  {title: 'September', key: '9'},
  {title: 'October', key: '10'},
  {title: 'November', key: '11'},
  {title: 'December', key: '12'}
];

export const COMPUTED_DAYS = {
  day: {key: 'day', title: 'Day'},
  weekday: {key: 'weekday', title: 'Weekday'}
};

export function getMaximumDaysInMonth (monthKey) {
  if (!monthKey || isNaN(monthKey)) {
    return null;
  }
  let days = 31; // Jan, March, May, July, August, October, December
  switch (Number(monthKey)) {
    case 2: // February
      days = 28;
      break;
    case 4: // April
    case 6: // June
    case 9: // September
    case 11: // November
      days = 30;
      break;
  }
  return days;
}
