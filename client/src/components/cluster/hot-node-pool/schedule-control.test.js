/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import {compareSchedulesArray, scheduleIsValid, parseDay} from './schedule-control';

const full = {from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'};

describe('scheduleIsValid', () => {
  it('is false for a missing schedule', () => {
    expect(scheduleIsValid(undefined)).toBe(false);
  });

  it('is truthy when from/fromTime/to/toTime are all set', () => {
    // Returns the last operand of the `&&` chain (toTime), not a coerced boolean.
    expect(scheduleIsValid(full)).toBeTruthy();
  });

  it.each(['from', 'fromTime', 'to', 'toTime'])('is falsy when %s is missing', (field) => {
    expect(scheduleIsValid({...full, [field]: undefined})).toBeFalsy();
  });
});

describe('compareSchedulesArray', () => {
  it('treats absent and undefined as equal', () => {
    expect(compareSchedulesArray(undefined, undefined)).toBe(true);
  });

  it('treats one side absent as different', () => {
    expect(compareSchedulesArray([full], undefined)).toBe(false);
  });

  it('treats differing lengths as different', () => {
    expect(compareSchedulesArray([full], [full, full])).toBe(false);
  });

  it('is true for element-wise equal entries, ignoring an id field', () => {
    expect(compareSchedulesArray([{...full, id: 0}], [{...full, id: 99}])).toBe(true);
  });

  it('is false when any entry differs', () => {
    expect(compareSchedulesArray([full], [{...full, to: 'SUNDAY'}])).toBe(false);
  });
});

describe('parseDay', () => {
  it('returns the day unchanged when no date is given', () => {
    expect(parseDay('MONDAY', undefined)).toBe('MONDAY');
  });

  it('returns the day unchanged when no day is given', () => {
    expect(parseDay(undefined, '09:00:00')).toBeUndefined();
  });
});
