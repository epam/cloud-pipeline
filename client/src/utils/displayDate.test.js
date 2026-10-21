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

import moment from 'moment-timezone';
import {testTimezone} from '@test';
import displayDate from './displayDate';

describe('the timezone pin', () => {
  // displayDate formats through moment's default zone, so without the pin every
  // assertion below would only hold in the author's timezone. Asserting the pin
  // itself makes that failure legible instead of looking like a date bug.
  it('puts moment in the timezone the harness declares', () => {
    expect(moment.tz.zone(testTimezone)).not.toBeNull();
    expect(moment('2023-01-15T10:30:00.000Z').utcOffset()).toBe(0);
  });
});

describe('displayDate', () => {
  it('renders a UTC instant as its UTC wall clock', () => {
    expect(displayDate('2023-01-15T10:30:00.000Z')).toBe('2023-01-15 10:30:00');
  });

  it('renders an offset instant in the pinned zone, not the original offset', () => {
    expect(displayDate('2023-01-15T10:30:00.000+03:00')).toBe('2023-01-15 07:30:00');
  });

  it('accepts a Date as well as a string', () => {
    expect(displayDate(new Date(Date.UTC(2023, 0, 15, 10, 30, 0)))).toBe('2023-01-15 10:30:00');
  });

  it('honours an explicit format', () => {
    expect(displayDate('2023-01-15T10:30:00.000Z', 'D MMMM YYYY')).toBe('15 January 2023');
    expect(displayDate('2023-01-15T10:30:00.000Z', 'HH:mm')).toBe('10:30');
  });

  it('renders nothing for no date', () => {
    expect(displayDate(undefined)).toBe('');
    expect(displayDate(null)).toBe('');
    expect(displayDate('')).toBe('');
  });
});
