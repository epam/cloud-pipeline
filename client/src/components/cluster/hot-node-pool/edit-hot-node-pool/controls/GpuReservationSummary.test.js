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
import {render, screen} from '@test';
import GpuReservationSummary, {
  formatReservationDate,
  isReservationScheduled
} from './GpuReservationSummary';

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
});

test('renders the label and the dash-joined window when both dates are given', () => {
  render(
    <GpuReservationSummary
      start="2026-03-01 10:00:00.000"
      end="2026-04-01 10:00:00.000"
    />
  );
  expect(screen.getByText('Reservation window:')).toBeInTheDocument();
  expect(screen.getByText('2026-03-01 10:00:00 – 2026-04-01 10:00:00')).toBeInTheDocument();
});

test('renders the pending message when end is missing', () => {
  render(<GpuReservationSummary start="2026-03-01 10:00:00.000" end={undefined} />);
  expect(screen.getByText('Pending approval (not yet scheduled)')).toBeInTheDocument();
});

test('renders the pending message when start is missing', () => {
  render(<GpuReservationSummary start={undefined} end="2026-04-01 10:00:00.000" />);
  expect(screen.getByText('Pending approval (not yet scheduled)')).toBeInTheDocument();
});

test('renders the pending message when both dates are missing', () => {
  render(<GpuReservationSummary start={undefined} end={undefined} />);
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
