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
import {Select} from 'antd';
import SelectInput from './SelectInput';

function options () {
  return (
    <Select.OptGroup key="aws" label="aws">
      <Select.Option key="1" value="1" title="eu-central-1">eu-central-1</Select.Option>
      <Select.Option key="2" value="2" title="eu-west-1">eu-west-1</Select.Option>
    </Select.OptGroup>
  );
}

test('renders the label', () => {
  render(<SelectInput label="Region:" value={undefined} onChange={fn()}>{options()}</SelectInput>);
  expect(screen.getByText('Region:')).toBeInTheDocument();
});

test('shows the current value as the selection', () => {
  render(<SelectInput label="Region:" value="1" onChange={fn()}>{options()}</SelectInput>);
  expect(screen.getByText('eu-central-1')).toBeInTheDocument();
});

test('disabled disables the combobox', () => {
  render(
    <SelectInput label="Region:" value={undefined} onChange={fn()} disabled>
      {options()}
    </SelectInput>
  );
  // antd 2.13's disabled Select exposes no accessible marker on the
  // role="combobox" element itself — only this class on its ancestor — so
  // rule 6's "never by CSS class" has no better option here.
  const select = screen.getByRole('combobox').closest('.ant-select');
  expect(select).toHaveClass('ant-select-disabled');
});

test('selecting an option calls onChange with its raw value', async () => {
  const onChange = fn();
  render(
    <SelectInput label="Region:" value={undefined} onChange={onChange}>{options()}</SelectInput>
  );

  await click(screen.getByRole('combobox'));
  await click(screen.getByText('eu-west-1'));

  expect(onChange).toHaveBeenCalledWith('2');
});

test('the error row shows only when both error and touched are truthy', () => {
  const {rerender} = render(
    <SelectInput
      label="Region:"
      value={undefined}
      onChange={fn()}
      error="Region is required"
      touched={false}
    >
      {options()}
    </SelectInput>
  );
  expect(screen.queryByText('Region is required')).not.toBeInTheDocument();

  rerender(
    <SelectInput
      label="Region:"
      value={undefined}
      onChange={fn()}
      error="Region is required"
      touched
    >
      {options()}
    </SelectInput>
  );
  expect(screen.getByText('Region is required')).toBeInTheDocument();
});

test('no error row at all when error is omitted (pins Price type, which has none)', () => {
  render(
    <SelectInput label="Price type:" value="SPOT" onChange={fn()} touched>
      <Select.Option key="SPOT" value="SPOT" title="Spot">Spot</Select.Option>
    </SelectInput>
  );
  expect(document.querySelector('.cp-error')).toBeNull();
});
