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

import {useFakeTimers, useRealTimers, setSystemTime} from '@test';
import displayDuration, {displayDurationInSeconds} from './displayDuration';

const MINUTE = 60;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

describe('displayDurationInSeconds', () => {
  it('always reports seconds, so a zero duration still reads', () => {
    expect(displayDurationInSeconds(0)).toBe('0 seconds');
    expect(displayDurationInSeconds()).toBe('0 seconds');
  });

  it('pluralises on the count, not on the unit', () => {
    expect(displayDurationInSeconds(1)).toBe('1 second');
    expect(displayDurationInSeconds(2)).toBe('2 seconds');
    expect(displayDurationInSeconds(HOUR, true)).toBe('1 hour, 0 seconds');
    expect(displayDurationInSeconds(2 * HOUR, true)).toBe('2 hours, 0 seconds');
  });

  it('reports only the largest unit unless details are asked for', () => {
    expect(displayDurationInSeconds(MINUTE)).toBe('1 minute');
    expect(displayDurationInSeconds(DAY + HOUR + MINUTE + 1)).toBe('1 day');
  });

  it('lists every non-zero unit when details are asked for', () => {
    expect(displayDurationInSeconds(DAY + HOUR + MINUTE + 1, true))
      .toBe('1 day, 1 hour, 1 minute, 1 second');
  });

  it('omits a zero intermediate unit but keeps the seconds', () => {
    expect(displayDurationInSeconds(HOUR, true)).toBe('1 hour, 0 seconds');
  });

  it('truncates a fractional second rather than rounding it', () => {
    expect(displayDurationInSeconds(1.9)).toBe('1 second');
  });
});

describe('displayDuration', () => {
  afterEach(() => {
    useRealTimers();
  });

  it('returns null when it has neither end of the interval', () => {
    expect(displayDuration()).toBeNull();
    expect(displayDuration(null, null)).toBeNull();
  });

  it('measures between two timestamps', () => {
    expect(displayDuration('2023-01-15T10:00:00.000Z', '2023-01-15T11:30:00.000Z'))
      .toBe('1 hour');
    expect(displayDuration('2023-01-15T10:00:00.000Z', '2023-01-15T10:00:30.000Z'))
      .toBe('30 seconds');
  });

  it('measures against now when no end is given', () => {
    useFakeTimers();
    setSystemTime(new Date('2023-01-15T12:00:00.000Z'));
    expect(displayDuration('2023-01-15T10:00:00.000Z')).toBe('2 hours');
  });
});
