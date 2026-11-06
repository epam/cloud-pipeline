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
import {render, screen, click, fn} from '@test';
import ScheduleControls from './ScheduleControls';

const entry = {id: 0, from: 'MONDAY', fromTime: '09:00:00', to: 'FRIDAY', toTime: '18:00:00'};

test('renders one entry per schedule item', () => {
  render(
    <ScheduleControls
      schedule={[entry, {...entry, id: 1}]}
      onAdd={fn()}
      onChange={() => fn()}
      onRemove={() => fn()}
    />
  );
  expect(screen.getAllByText('Starts on:')).toHaveLength(2);
});

test('Add schedule calls onAdd and is enabled while nothing disables the form', async () => {
  const onAdd = fn();
  render(
    <ScheduleControls schedule={[]} onAdd={onAdd} onChange={() => fn()} onRemove={() => fn()} />
  );

  const addButton = screen.getByRole('button', {name: 'Add schedule'});
  expect(addButton).not.toBeDisabled();
  await click(addButton);
  expect(onAdd).toHaveBeenCalledTimes(1);
});

test('removing an entry calls onRemove with its id, then the curried inner fn', async () => {
  const inner = fn();
  const onRemove = fn(() => inner);
  render(
    <ScheduleControls schedule={[entry]} onAdd={fn()} onChange={() => fn()} onRemove={onRemove} />
  );

  const deleteButton = screen.getAllByRole('button').find(b => !b.textContent);
  await click(deleteButton);

  expect(onRemove).toHaveBeenCalledWith(0);
  expect(inner).toHaveBeenCalledTimes(1);
});

test('the error row shows only when both error and touched are truthy', () => {
  const {rerender} = render(
    <ScheduleControls
      schedule={[]}
      error="Invalid schedule"
      touched={false}
      onAdd={fn()}
      onChange={() => fn()}
      onRemove={() => fn()}
    />
  );
  expect(screen.queryByText('Invalid schedule')).not.toBeInTheDocument();

  rerender(
    <ScheduleControls
      schedule={[]}
      error="Invalid schedule"
      touched
      onAdd={fn()}
      onChange={() => fn()}
      onRemove={() => fn()}
    />
  );
  expect(screen.getByText('Invalid schedule')).toBeInTheDocument();
});

// `disabled` is how the form blocks itself while a save is in flight, so every control
// here has to honour it - including each entry's own selects, time pickers and remove
// button, which are rendered by `ScheduleControl` rather than by this component.
test('disabled blocks Add schedule and every control of each entry', () => {
  render(
    <ScheduleControls
      schedule={[entry]}
      disabled
      onAdd={fn()}
      onChange={() => fn()}
      onRemove={() => fn()}
    />
  );

  expect(screen.getByRole('button', {name: 'Add schedule'})).toBeDisabled();
  // The entry's remove button carries an icon and no text.
  expect(screen.getAllByRole('button').find(b => !b.textContent)).toBeDisabled();
  screen.getAllByRole('textbox').forEach(input => expect(input).toBeDisabled());
  // antd's Select combobox carries no native `disabled` attribute at this version; the
  // state is a class on the `.ant-select` ancestor instead.
  screen.getAllByRole('combobox').forEach(combobox => {
    expect(combobox.closest('.ant-select')).toHaveClass('ant-select-disabled');
  });
});
