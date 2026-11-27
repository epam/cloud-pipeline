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

import React from 'react';
import moment from 'moment-timezone';
import {render, screen} from '@test';
import ReservationSummary, {formatReservationDate} from './ReservationSummary';
import {isReservationScheduled} from '../helpers';

describe('formatReservationDate', () => {
  test('drops milliseconds from a valid wire-format value', () => {
    expect(formatReservationDate('2026-03-01 10:00:00.000')).toBe('2026-03-01 10:00:00');
  });

  test.each([undefined, null, ''])('is undefined for %p', (value) => {
    expect(formatReservationDate(value)).toBeUndefined();
  });

  test('is undefined for an unparseable value', () => {
    expect(formatReservationDate('not-a-date')).toBeUndefined();
  });

  // The harness pins the zone to UTC, where this bug is invisible, so set a real offset here.
  test('converts a UTC wall time to the viewer\'s local zone, not read as local', () => {
    moment.tz.setDefault('Asia/Tokyo');
    try {
      expect(formatReservationDate('2026-12-25 11:30:00.000')).toBe('2026-12-25 20:30:00');
    } finally {
      moment.tz.setDefault('UTC');
    }
  });
});

test('renders the label and the dash-joined window when both dates are given', () => {
  render(
    <ReservationSummary
      start="2026-03-01 10:00:00.000"
      end="2026-04-01 10:00:00.000"
    />
  );
  expect(screen.getByText('Reservation window:')).toBeInTheDocument();
  expect(screen.getByText('2026-03-01 10:00:00 – 2026-04-01 10:00:00')).toBeInTheDocument();
});

// AWS terminates the instances at `end`, so a wrong zone here misleads operationally.
test('renders a granted window in the viewer\'s local zone, not the UTC wall time', () => {
  moment.tz.setDefault('Asia/Tokyo');
  try {
    render(
      <ReservationSummary
        start="2026-12-25 11:30:00.000"
        end="2026-12-27 11:30:00.000"
      />
    );
    expect(screen.getByText('2026-12-25 20:30:00 – 2026-12-27 20:30:00')).toBeInTheDocument();
  } finally {
    moment.tz.setDefault('UTC');
  }
});

test('renders the pending message when end is missing', () => {
  render(<ReservationSummary start="2026-03-01 10:00:00.000" end={undefined} />);
  expect(screen.getByText('Pending approval (not yet scheduled)')).toBeInTheDocument();
});

test('renders the pending message when start is missing', () => {
  render(<ReservationSummary start={undefined} end="2026-04-01 10:00:00.000" />);
  expect(screen.getByText('Pending approval (not yet scheduled)')).toBeInTheDocument();
});

test('renders the pending message when both dates are missing', () => {
  render(<ReservationSummary start={undefined} end={undefined} />);
  expect(screen.getByText('Pending approval (not yet scheduled)')).toBeInTheDocument();
});

describe('isReservationScheduled', () => {
  it('is true once both dates parse', () => {
    expect(isReservationScheduled('2026-03-01 10:00:00.000', '2026-04-01 10:00:00.000'))
      .toBe(true);
  });

  it.each([
    [undefined, '2026-04-01 10:00:00.000'],
    ['2026-03-01 10:00:00.000', undefined],
    [undefined, undefined]
  ])('is false when start=%p end=%p', (start, end) => {
    expect(isReservationScheduled(start, end)).toBe(false);
  });
});
