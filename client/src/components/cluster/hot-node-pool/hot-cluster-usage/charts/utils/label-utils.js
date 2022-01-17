/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import moment from 'moment-timezone';

const getDayHours = () => {
  return Array.from({length: 24}, (_, i) => moment(i, 'HH').format('HH:mm'));
};

const getMonthDays = (date, format) => {
  const currentMonth = moment(date).format('YYYY-MM');
  const days = moment(date).daysInMonth();
  return Array.from(
    {length: days},
    (_, i) => moment(`${currentMonth}-${moment(i + 1, 'DD').format('DD')}`, format)
      .format(format)
  );
};

export {getDayHours, getMonthDays};
