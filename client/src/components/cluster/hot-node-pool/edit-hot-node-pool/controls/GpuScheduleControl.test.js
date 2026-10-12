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
import {render, screen, within, change, fn} from '@test';
import GpuScheduleControl, {getCommitmentEnd} from './GpuScheduleControl';

// Mirrors the `row` helper in `../index.test.js`: the label span's closest `div` is the
// `formRow` div holding both the label and its control.
function row (labelText) {
  return screen.getByText(labelText).closest('div');
}

describe('getCommitmentEnd', () => {
  test('adds whole hours', () => {
    expect(getCommitmentEnd('2026-01-01 10:00:00.000', 3600)).toBe('2026-01-01 11:00:00.000');
  });

  test('adds seconds across a minute boundary', () => {
    expect(getCommitmentEnd('2026-01-01 10:00:00.000', 90)).toBe('2026-01-01 10:01:30.000');
  });

  test('undefined with no start', () => {
    expect(getCommitmentEnd(undefined, 3600)).toBeUndefined();
  });

  test('undefined with no duration', () => {
    expect(getCommitmentEnd('2026-01-01 10:00:00', undefined)).toBeUndefined();
  });

  test('undefined with a zero duration', () => {
    expect(getCommitmentEnd('2026-01-01 10:00:00', 0)).toBeUndefined();
  });

  test('undefined with a negative duration', () => {
    expect(getCommitmentEnd('2026-01-01 10:00:00', -5)).toBeUndefined();
  });

  test('undefined with an unparseable start', () => {
    expect(getCommitmentEnd('not-a-date', 3600)).toBeUndefined();
  });
});

test('renders all three labels', () => {
  render(
    <GpuScheduleControl
      start={undefined}
      duration={undefined}
      onChangeStart={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.getByText('Starts on:')).toBeInTheDocument();
  expect(screen.getByText('Duration (sec):')).toBeInTheDocument();
  expect(screen.getByText('Ends on:')).toBeInTheDocument();
});

test('Ends on shows "Not set" when start/duration are absent', () => {
  render(
    <GpuScheduleControl
      start={undefined}
      duration={undefined}
      onChangeStart={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(within(row('Ends on:')).getByText('Not set')).toBeInTheDocument();
});

test('Ends on shows the derived value when both start and duration are given', () => {
  render(
    <GpuScheduleControl
      start="2026-01-01 10:00:00.000"
      duration={3600}
      onChangeStart={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(within(row('Ends on:')).getByText('2026-01-01 11:00:00.000')).toBeInTheDocument();
});

test('changing Duration calls onChangeDuration', async () => {
  const onChangeDuration = fn();
  render(
    <GpuScheduleControl
      start={undefined}
      duration={undefined}
      onChangeStart={fn()}
      onChangeDuration={onChangeDuration}
    />
  );

  const durationInput = within(row('Duration (sec):')).getByRole('textbox');
  await change(durationInput, '7200');

  expect(onChangeDuration).toHaveBeenCalledWith(7200);
});

test('disabled disables the duration input and the Starts on picker input', () => {
  render(
    <GpuScheduleControl
      start={undefined}
      duration={undefined}
      disabled
      onChangeStart={fn()}
      onChangeDuration={fn()}
    />
  );

  expect(within(row('Duration (sec):')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Starts on:')).getByRole('textbox')).toBeDisabled();
});

test('startError shows only when touched', () => {
  const {rerender} = render(
    <GpuScheduleControl
      start={undefined}
      duration={undefined}
      startError="Start date is required"
      touched={false}
      onChangeStart={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.queryByText('Start date is required')).not.toBeInTheDocument();

  rerender(
    <GpuScheduleControl
      start={undefined}
      duration={undefined}
      startError="Start date is required"
      touched
      onChangeStart={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.getByText('Start date is required')).toBeInTheDocument();
});

test('durationError does not show when not touched', () => {
  render(
    <GpuScheduleControl
      start={undefined}
      duration={undefined}
      startError="Start date is required"
      durationError="Duration should be a positive number of seconds"
      touched={false}
      onChangeStart={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.queryByText('Start date is required')).not.toBeInTheDocument();
  expect(
    screen.queryByText('Duration should be a positive number of seconds')
  ).not.toBeInTheDocument();
});
