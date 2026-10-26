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
import {render, screen, change, fn} from '@test';
import NodeUpRetryCountFormItem from './index.jsx';

function renderField (props = {}) {
  render(<NodeUpRetryCountFormItem placeholder={4} {...props} />);
  return screen.getByLabelText('Capacity retries');
}

test('shows the value it is given', () => {
  expect(renderField({value: '7'})).toHaveValue('7');
});

test('shows a numeric value as text', () => {
  expect(renderField({value: 12})).toHaveValue('12');
});

test('is empty with the default as its placeholder when no value is set', () => {
  const input = renderField({value: undefined});

  expect(input).toHaveValue('');
  expect(input).toHaveAttribute('placeholder', '4');
});

test('reports each change, and an empty string when cleared', async () => {
  const onChange = fn();
  const input = renderField({value: '7', onChange});

  await change(input, '10');
  expect(onChange).toHaveBeenLastCalledWith('10');

  await change(input, '');
  expect(onChange).toHaveBeenLastCalledWith('');
});

test('shows the error it is given', () => {
  renderField({value: '0', error: 'Please enter a valid positive integer number'});

  expect(screen.getByText('Please enter a valid positive integer number')).toBeInTheDocument();
});

test('is disabled when asked to be', () => {
  expect(renderField({value: '7', disabled: true})).toBeDisabled();
});
