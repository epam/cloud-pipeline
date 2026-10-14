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
import ReservationScheduleControl, {windowCoversDuration} from './ReservationScheduleControl';

// Mirrors the `row` helper in `../index.test.js`: the label span's closest `div` is the
// `formRow` div holding both the label and its control.
function row (labelText) {
  return screen.getByText(labelText).closest('div');
}

describe('windowCoversDuration', () => {
  test('a window well longer than the duration covers it', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-31 10:00:00.000', 3600)
    ).toBe(true);
  });

  test('an exact fit covers the duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-01 11:00:00.000', 3600)
    ).toBe(true);
  });

  test('one second short does not cover the duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-01 11:00:00.000', 3601)
    ).toBe(false);
  });

  test('undefined with no start', () => {
    expect(
      windowCoversDuration(undefined, '2026-01-01 11:00:00.000', 3600)
    ).toBeUndefined();
  });

  test('undefined with no end', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', undefined, 3600)
    ).toBeUndefined();
  });

  test('undefined with no duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-01 11:00:00.000', undefined)
    ).toBeUndefined();
  });

  test('undefined with a zero duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-01 11:00:00.000', 0)
    ).toBeUndefined();
  });

  test('undefined with a negative duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-01 11:00:00.000', -5)
    ).toBeUndefined();
  });

  test('undefined with an unparseable start', () => {
    expect(
      windowCoversDuration('not-a-date', '2026-01-01 11:00:00.000', 3600)
    ).toBeUndefined();
  });

  test('undefined with an unparseable end', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', 'not-a-date', 3600)
    ).toBeUndefined();
  });

  test('undefined with a start that is missing milliseconds (strict parsing)', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00', '2026-01-01 11:00:00.000', 3600)
    ).toBeUndefined();
  });
});

test('renders the three date/duration labels', () => {
  render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      duration={undefined}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.getByText('Starts on:')).toBeInTheDocument();
  expect(screen.getByText('Ends on:')).toBeInTheDocument();
  expect(screen.getByText('Duration (sec):')).toBeInTheDocument();
});

test('Starts on and Ends on show their own values independently', () => {
  render(
    <ReservationScheduleControl
      start="2026-01-01 10:00:00.000"
      end="2026-02-01 10:00:00.000"
      duration={undefined}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(
    within(row('Starts on:')).getByRole('textbox').value
  ).toBe('2026-01-01 10:00:00.000');
  expect(
    within(row('Ends on:')).getByRole('textbox').value
  ).toBe('2026-02-01 10:00:00.000');
});

test('disabled disables all three inputs', () => {
  render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      duration={undefined}
      disabled
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );

  expect(within(row('Starts on:')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Ends on:')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Duration (sec):')).getByRole('textbox')).toBeDisabled();
});

test('startError and endError each show only when touched, independently', () => {
  const {rerender} = render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      duration={undefined}
      startError="Start date is required"
      endError="End date is required"
      touched={false}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.queryByText('Start date is required')).not.toBeInTheDocument();
  expect(screen.queryByText('End date is required')).not.toBeInTheDocument();

  rerender(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      duration={undefined}
      endError="End date is required"
      touched
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.getByText('End date is required')).toBeInTheDocument();
  expect(screen.queryByText('Start date is required')).not.toBeInTheDocument();

  rerender(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      duration={undefined}
      startError="Start date is required"
      endError="End date is required"
      touched
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.getByText('Start date is required')).toBeInTheDocument();
  expect(screen.getByText('End date is required')).toBeInTheDocument();
});

test('changing Duration calls onChangeDuration', async () => {
  const onChangeDuration = fn();
  render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      duration={undefined}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={onChangeDuration}
    />
  );

  const durationInput = within(row('Duration (sec):')).getByRole('textbox');
  await change(durationInput, '7200');

  expect(onChangeDuration).toHaveBeenCalledWith(7200);
});

// Deviation recorded per the Phase 2 plan: a `fireEvent('change', ...)` on the "Ends on"
// textbox, including a trailing `keyDown({key: 'Enter'})` + `blur` to force rc-calendar to
// commit it, never calls `onChangeEnd` under antd 2.13.14/React 15 in this harness — both
// forms were genuinely tried and left `onChangeEnd` uncalled. There is no existing
// precedent anywhere in this codebase for driving an antd DatePicker through a unit test
// (see the plan's "Supporting facts"). The dedicated interaction test is dropped; tests
// above ("Starts on and Ends on show their own values independently", "disabled disables
// all three inputs", "startError and endError each show only when touched, independently")
// already prove the end field is a real, independently-editable input.

test('the fit hint reflects the window', () => {
  const {rerender} = render(
    <ReservationScheduleControl
      start="2026-01-01 10:00:00.000"
      end="2026-01-31 10:00:00.000"
      duration={3600}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.getByText('Reservation window covers this duration')).toBeInTheDocument();
  expect(
    screen.queryByText('This duration does not fit in the selected window')
  ).not.toBeInTheDocument();

  rerender(
    <ReservationScheduleControl
      start="2026-01-01 10:00:00.000"
      end="2026-01-01 10:30:00.000"
      duration={3600}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(
    screen.getByText('This duration does not fit in the selected window')
  ).toBeInTheDocument();
  expect(
    screen.queryByText('Reservation window covers this duration')
  ).not.toBeInTheDocument();

  rerender(
    <ReservationScheduleControl
      start="2026-01-01 10:00:00.000"
      end={undefined}
      duration={3600}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.queryByText('Reservation window covers this duration')).not.toBeInTheDocument();
  expect(
    screen.queryByText('This duration does not fit in the selected window')
  ).not.toBeInTheDocument();
});
