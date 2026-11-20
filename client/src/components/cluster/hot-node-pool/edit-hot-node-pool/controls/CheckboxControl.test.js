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
import {render, screen, within, click, fn} from '@test';
import CheckboxControl from './CheckboxControl';

function row (labelText) {
  return screen.getByText(labelText).parentNode;
}

test('renders the label and reflects checked', () => {
  render(<CheckboxControl label="Autoscaled:" checked />);
  expect(screen.getByText('Autoscaled:')).toBeInTheDocument();
  expect(screen.getByRole('checkbox')).toBeChecked();
});

test('calls onChange with the raw antd event (target.checked)', async () => {
  const onChange = fn();
  render(<CheckboxControl label="Autoscaled:" checked={false} onChange={onChange} />);

  await click(screen.getByRole('checkbox'));

  expect(onChange).toHaveBeenCalledTimes(1);
  expect(onChange.mock.calls[0][0].target.checked).toBe(true);
});

test('disables the checkbox', () => {
  render(<CheckboxControl label="Autoscaled:" checked={false} disabled />);
  expect(screen.getByRole('checkbox')).toBeDisabled();
});

test('shifts the checkbox left margin only when checked', () => {
  const {rerender} = render(<CheckboxControl label="Autoscaled:" checked={false} />);
  const unchecked = within(row('Autoscaled:')).getByRole('checkbox')
    .closest('label');
  expect(unchecked).not.toHaveStyle({marginLeft: '11px'});

  rerender(<CheckboxControl label="Autoscaled:" checked />);
  const checked = within(row('Autoscaled:')).getByRole('checkbox')
    .closest('label');
  expect(checked).toHaveStyle({marginLeft: '11px'});
});

test('shows a hint when given', async () => {
  render(<CheckboxControl label="Autoscaled:" checked={false} hint="Explains autoscaling" />);
  expect(document.querySelector('.anticon-question-circle')).not.toBeNull();
});
