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

test('Add schedule calls onAdd and is never disabled', async () => {
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
