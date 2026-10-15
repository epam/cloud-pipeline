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
import ReservationScheduleControl from './ReservationScheduleControl';

// Mirrors the `row` helper in `../index.test.js`: the label span's closest `div` is the
// `formRow` div holding both the label and its control.
function row (labelText) {
  return screen.getByText(labelText).closest('div');
}

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

describe('the reservation status notice', () => {
  test('no reservationStatus renders no alert at all', () => {
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
    expect(document.querySelector('.ant-alert')).not.toBeInTheDocument();
  });

  test('REQUIRED_APPROVE shows the approval sentence', () => {
    render(
      <ReservationScheduleControl
        start={undefined}
        end={undefined}
        durationDays={undefined}
        reservationStatus="REQUIRED_APPROVE"
        onChangeStart={fn()}
        onChangeEnd={fn()}
        onChangeDuration={fn()}
      />
    );
    expect(
      screen.getByText('This reservation is waiting for an administrator\'s approval.')
    ).toBeInTheDocument();
  });

  test('the client-only PENDING fallback shows the "waiting to be scheduled" sentence', () => {
    render(
      <ReservationScheduleControl
        start={undefined}
        end={undefined}
        durationDays={undefined}
        reservationStatus="PENDING"
        onChangeStart={fn()}
        onChangeEnd={fn()}
        onChangeDuration={fn()}
      />
    );
    expect(screen.getByText(
      'This pool is waiting to be scheduled by the capacity reservation service.'
    )).toBeInTheDocument();
  });

  test('SCHEDULED shows its own sentence, not the "waiting to be scheduled" one', () => {
    render(
      <ReservationScheduleControl
        start={undefined}
        end={undefined}
        durationDays={undefined}
        reservationStatus="SCHEDULED"
        onChangeStart={fn()}
        onChangeEnd={fn()}
        onChangeDuration={fn()}
      />
    );
    expect(screen.getByText(
      'This reservation is scheduled. Its granted window is not available here yet.'
    )).toBeInTheDocument();
    expect(screen.queryByText(
      'This pool is waiting to be scheduled by the capacity reservation service.'
    )).not.toBeInTheDocument();
  });

  // antd 2 carries the alert's severity as a class, with no accessible attribute at this
  // version - same gap `index.test.js`'s `selectIsDisabled` helper documents for `Select`.
  test('CANCELLED shows the cancelled sentence at warning severity', () => {
    render(
      <ReservationScheduleControl
        start={undefined}
        end={undefined}
        durationDays={undefined}
        reservationStatus="CANCELLED"
        onChangeStart={fn()}
        onChangeEnd={fn()}
        onChangeDuration={fn()}
      />
    );
    expect(screen.getByText('This reservation was cancelled.')).toBeInTheDocument();
    expect(document.querySelector('.ant-alert-warning')).toBeInTheDocument();
  });

  // Same antd-2 gap as above: severity is a class, not an accessible attribute.
  test('FAILED shows the failed sentence at error severity', () => {
    render(
      <ReservationScheduleControl
        start={undefined}
        end={undefined}
        durationDays={undefined}
        reservationStatus="FAILED"
        onChangeStart={fn()}
        onChangeEnd={fn()}
        onChangeDuration={fn()}
      />
    );
    expect(screen.getByText('This reservation failed.')).toBeInTheDocument();
    expect(document.querySelector('.ant-alert-error')).toBeInTheDocument();
  });

  test('an unknown status falls back to the humanised sentence', () => {
    render(
      <ReservationScheduleControl
        start={undefined}
        end={undefined}
        durationDays={undefined}
        reservationStatus="SOME_NEW_STATUS"
        onChangeStart={fn()}
        onChangeEnd={fn()}
        onChangeDuration={fn()}
      />
    );
    expect(screen.getByText('Reservation status: Some new status.')).toBeInTheDocument();
  });
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
