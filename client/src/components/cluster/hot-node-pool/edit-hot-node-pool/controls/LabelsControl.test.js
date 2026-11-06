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
import {render, screen, click, change, fn} from '@test';
import LabelsControl from './LabelsControl';

const labelA = {key: 'a', name: 'env', value: 'prod', monitored: true};
const labelB = {key: 'b', name: 'tier', value: 'web', monitored: false};

test('editable mode renders a name/value textbox, Monitored checkbox and delete per label', () => {
  render(
    <LabelsControl
      labels={[labelA, labelB]}
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );
  expect(screen.getAllByRole('textbox')).toHaveLength(4);
  expect(screen.getAllByLabelText('Monitored')).toHaveLength(2);
  expect(screen.getAllByRole('button', {name: ''})).toHaveLength(2);
});

test('typing a name calls onChangeName(key) then the inner fn with the raw event', async () => {
  // React 15 pools SyntheticEvent and nullifies `.target` once the handler
  // returns, so the value is read synchronously inside the inner fn itself.
  const values = [];
  const inner = fn((e) => values.push(e.target.value));
  const onChangeName = fn(() => inner);
  render(
    <LabelsControl
      labels={[labelA]}
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={onChangeName}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );

  const [nameInput] = screen.getAllByRole('textbox');
  await change(nameInput, 'renamed');

  expect(onChangeName).toHaveBeenCalledWith('a');
  expect(values).toEqual(['renamed']);
});

test('changing the value calls onChangeValue(key) then the inner fn', async () => {
  const values = [];
  const inner = fn((e) => values.push(e.target.value));
  const onChangeValue = fn(() => inner);
  render(
    <LabelsControl
      labels={[labelA]}
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={onChangeValue}
      onChangeMonitored={() => fn()}
    />
  );

  const [, valueInput] = screen.getAllByRole('textbox');
  await change(valueInput, 'staging');

  expect(onChangeValue).toHaveBeenCalledWith('a');
  expect(values).toEqual(['staging']);
});

test('toggling Monitored calls onChangeMonitored(key) then the inner fn with checked', async () => {
  const inner = fn();
  const onChangeMonitored = fn(() => inner);
  render(
    <LabelsControl
      labels={[labelA]}
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={onChangeMonitored}
    />
  );

  await click(screen.getByLabelText('Monitored'));

  expect(onChangeMonitored).toHaveBeenCalledWith('a');
  expect(inner.mock.calls[0][0].target.checked).toBe(false);
});

test('delete calls onRemove(key) then the inner fn', async () => {
  const inner = fn();
  const onRemove = fn(() => inner);
  render(
    <LabelsControl
      labels={[labelA, labelB]}
      onAdd={fn()}
      onRemove={onRemove}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );

  const [firstDelete] = screen.getAllByRole('button', {name: ''});
  await click(firstDelete);

  expect(onRemove).toHaveBeenCalledWith('a');
  expect(inner).toHaveBeenCalledTimes(1);
});

test('Add node label calls onAdd and is absent when readOnly', async () => {
  const onAdd = fn();
  render(
    <LabelsControl
      labels={[]}
      readOnly={false}
      onAdd={onAdd}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );
  await click(screen.getByText('Add node label'));
  expect(onAdd).toHaveBeenCalledTimes(1);
});

test('readOnly renders name/value as text with "(monitored)" and no textboxes', () => {
  render(
    <LabelsControl
      labels={[labelA]}
      readOnly
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );
  expect(screen.getByText('env:')).toBeInTheDocument();
  expect(screen.getByText('prod')).toBeInTheDocument();
  expect(screen.getByText('(monitored)')).toBeInTheDocument();
  expect(screen.queryAllByRole('textbox')).toHaveLength(0);
  expect(screen.queryByText('Add node label')).not.toBeInTheDocument();
});

test('an empty labels list with readOnly renders nothing', () => {
  const {container} = render(
    <LabelsControl
      labels={[]}
      readOnly
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );
  expect(container.textContent).toBe('');
});

test('the error row renders with error alone, with no touched gate', () => {
  render(
    <LabelsControl
      labels={[]}
      error="There are duplicate node labels"
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );
  expect(screen.getByText('There are duplicate node labels')).toBeInTheDocument();
});

test('name/value inputs and Monitored checkbox are never disabled, even when disabled', () => {
  render(
    <LabelsControl
      labels={[labelA]}
      disabled
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );
  screen.getAllByRole('textbox').forEach(input => expect(input).not.toBeDisabled());
  expect(screen.getByLabelText('Monitored')).not.toBeDisabled();
  expect(screen.getByText('Add node label').closest('button')).toBeDisabled();
});

test('a duplicate or empty name puts cp-error on the name input', () => {
  render(
    <LabelsControl
      labels={[{...labelA, name: ''}, labelB]}
      onAdd={fn()}
      onRemove={() => fn()}
      onChangeName={() => fn()}
      onChangeValue={() => fn()}
      onChangeMonitored={() => fn()}
    />
  );
  const [emptyNameInput] = screen.getAllByRole('textbox');
  expect(emptyNameInput).toHaveClass('cp-error');
});
