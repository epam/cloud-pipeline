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
import {render, screen, within, change, hover, fn} from '@test';
import ReservationScheduleControl, {windowCoversDuration} from './ReservationScheduleControl';

// Mirrors the `row` helper in `../index.test.js`: the label span's closest `div` is the
// `formRow` div holding both the label and its control.
function row (labelText) {
  return screen.getByText(labelText).closest('div');
}

describe('windowCoversDuration', () => {
  test('a window well longer than the duration (in days) covers it', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-02-01 10:00:00.000', 14)
    ).toBe(true);
  });

  test('an exact fit at 14 days covers the duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-15 10:00:00.000', 14)
    ).toBe(true);
  });

  test('15 days in a 14-day window does not cover the duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-15 10:00:00.000', 15)
    ).toBe(false);
  });

  test('undefined with no start', () => {
    expect(
      windowCoversDuration(undefined, '2026-01-15 10:00:00.000', 14)
    ).toBeUndefined();
  });

  test('undefined with no end', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', undefined, 14)
    ).toBeUndefined();
  });

  test('undefined with no duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-15 10:00:00.000', undefined)
    ).toBeUndefined();
  });

  test('undefined with a zero duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-15 10:00:00.000', 0)
    ).toBeUndefined();
  });

  test('undefined with a negative duration', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', '2026-01-15 10:00:00.000', -5)
    ).toBeUndefined();
  });

  test('undefined with an unparseable start', () => {
    expect(
      windowCoversDuration('not-a-date', '2026-01-15 10:00:00.000', 14)
    ).toBeUndefined();
  });

  test('undefined with an unparseable end', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00.000', 'not-a-date', 14)
    ).toBeUndefined();
  });

  test('undefined with a start that is missing milliseconds (strict parsing)', () => {
    expect(
      windowCoversDuration('2026-01-01 10:00:00', '2026-01-15 10:00:00.000', 14)
    ).toBeUndefined();
  });
});

test('renders the three date/duration labels', () => {
  render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      durationDays={undefined}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(screen.getByText('Search from:')).toBeInTheDocument();
  expect(screen.getByText('Search until:')).toBeInTheDocument();
  expect(screen.getByText('Duration (days):')).toBeInTheDocument();
});

test('Search from and Search until show their own values independently', () => {
  render(
    <ReservationScheduleControl
      start="2026-01-01 10:00:00.000"
      end="2026-02-01 10:00:00.000"
      durationDays={undefined}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  expect(
    within(row('Search from:')).getByRole('textbox').value
  ).toBe('2026-01-01 10:00:00.000');
  expect(
    within(row('Search until:')).getByRole('textbox').value
  ).toBe('2026-02-01 10:00:00.000');
});

test('disabled disables all three inputs', () => {
  render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      durationDays={undefined}
      disabled
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );

  expect(within(row('Search from:')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Search until:')).getByRole('textbox')).toBeDisabled();
  expect(within(row('Duration (days):')).getByRole('textbox')).toBeDisabled();
});

test('startError and endError each show only when touched, independently', () => {
  const {rerender} = render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      durationDays={undefined}
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
      durationDays={undefined}
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
      durationDays={undefined}
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
      durationDays={undefined}
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={onChangeDuration}
    />
  );

  const durationInput = within(row('Duration (days):')).getByRole('textbox');
  await change(durationInput, '30');

  expect(onChangeDuration).toHaveBeenCalledWith(30);
});

// There is no test here driving the "Search until" picker through the UI, and that is
// deliberate: a `change` on its textbox, with or without a trailing `Enter`/blur to make
// rc-calendar commit the value, never reaches `onChangeEnd` under antd 2.13.14 and React
// 15 — both forms were tried. Nothing anywhere in this codebase drives an antd DatePicker
// from a unit test. The tests above ("Search from and Search until show their own values
// independently", "disabled disables all three inputs", "startError and endError each
// show only when touched, independently") carry the proof instead: the end date is a real
// input of its own, not a value derived from the other two fields.

test('the fit hint reflects the window', () => {
  const {rerender} = render(
    <ReservationScheduleControl
      start="2026-01-01 10:00:00.000"
      end="2026-01-31 10:00:00.000"
      durationDays={14}
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
      end="2026-01-05 10:00:00.000"
      durationDays={14}
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
      durationDays={14}
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

test('each of the three rows renders a hint trigger', () => {
  render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      durationDays={undefined}
      startHint="Explains Search from"
      endHint="Explains Search until"
      durationHint="Explains Duration"
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );
  // HintControl.test.js establishes this is the only accessible selector for the
  // trigger - antd's Icon exposes no role.
  const triggers = document.querySelectorAll('.anticon-question-circle');
  expect(triggers.length).toBe(3);
});

test('hovering each row\'s hint trigger shows its own content', async () => {
  render(
    <ReservationScheduleControl
      start={undefined}
      end={undefined}
      durationDays={undefined}
      startHint="Explains Search from"
      endHint="Explains Search until"
      durationHint="Explains Duration"
      onChangeStart={fn()}
      onChangeEnd={fn()}
      onChangeDuration={fn()}
    />
  );

  const [startTrigger, endTrigger, durationTrigger] = Array.from(
    document.querySelectorAll('.anticon-question-circle')
  );

  await hover(startTrigger);
  expect(await screen.findByText('Explains Search from')).toBeInTheDocument();

  await hover(endTrigger);
  expect(await screen.findByText('Explains Search until')).toBeInTheDocument();

  await hover(durationTrigger);
  expect(await screen.findByText('Explains Duration')).toBeInTheDocument();
});
