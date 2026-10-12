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
import StringInput from './StringInput';

test('renders the label text verbatim, including the colon', () => {
  render(<StringInput label="Pool name:" value="my-pool" onChange={fn()} />);
  expect(screen.getByText('Pool name:')).toBeInTheDocument();
});

test('type "string" renders an Input and onChange receives an event', async () => {
  // React 15 pools SyntheticEvent and nullifies `.target` once the handler
  // returns, so the value is read synchronously inside the handler itself.
  const values = [];
  const onChange = fn((e) => values.push(e.target.value));
  render(<StringInput type="string" label="Pool name:" value="" onChange={onChange} />);

  await change(screen.getByRole('textbox'), 'abc');

  expect(onChange).toHaveBeenCalledTimes(1);
  expect(values).toEqual(['abc']);
});

test('type "number" renders an InputNumber and onChange receives a raw value', async () => {
  const onChange = fn();
  render(
    <StringInput type="number" label="Disk:" value={50} onChange={onChange} min={15} max={15360} />
  );

  const input = screen.getByRole('textbox');
  await change(input, '42');

  expect(onChange).toHaveBeenLastCalledWith(42);
});

test('min/max reach the InputNumber', () => {
  render(
    <StringInput type="number" label="Disk:" value={50} onChange={fn()} min={15} max={15360} />
  );
  expect(screen.getByRole('textbox')).toHaveAttribute('min', '15');
  expect(screen.getByRole('textbox')).toHaveAttribute('max', '15360');
});

test('disabled disables the control', () => {
  render(<StringInput label="Pool name:" value="x" onChange={fn()} disabled />);
  expect(screen.getByRole('textbox')).toBeDisabled();
});

test('the error row shows only when both error and touched are truthy', () => {
  const {rerender} = render(
    <StringInput
      label="Pool name:"
      value=""
      onChange={fn()}
      error="Name is required"
      touched={false}
    />
  );
  expect(screen.queryByText('Name is required')).not.toBeInTheDocument();

  rerender(
    <StringInput label="Pool name:" value="" onChange={fn()} error={undefined} touched />
  );
  expect(screen.queryByText('Name is required')).not.toBeInTheDocument();

  rerender(
    <StringInput label="Pool name:" value="" onChange={fn()} error="Name is required" touched />
  );
  expect(screen.getByText('Name is required')).toBeInTheDocument();
});

test('style reaches the outer wrapper', () => {
  render(
    <StringInput
      type="number"
      label="Min Size:"
      value={1}
      onChange={fn()}
      style={{paddingLeft: 10}}
    />
  );
  expect(screen.getByText('Min Size:').closest('div[style]')).toHaveStyle({paddingLeft: '10px'});
});

test('shows a hint when given', () => {
  render(<StringInput label="Pool name:" value="" onChange={fn()} hint="Explains the field" />);
  expect(document.querySelector('.anticon-question-circle')).not.toBeNull();
});
