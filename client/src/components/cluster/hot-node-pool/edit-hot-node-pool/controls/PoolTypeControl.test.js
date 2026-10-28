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
import PoolTypeControl from './PoolTypeControl';

test('renders the label and shows Standard as the current value', () => {
  render(<PoolTypeControl value="STANDARD" onChange={fn()} />);
  expect(screen.getByText('Pool type:')).toBeInTheDocument();
  expect(screen.getByText('Standard')).toBeInTheDocument();
});

test('opening the combobox shows both Standard and Shareable node options', async () => {
  render(<PoolTypeControl value="STANDARD" onChange={fn()} />);

  await click(screen.getByRole('combobox'));

  expect(screen.getAllByText('Standard').length).toBeGreaterThan(0);
  expect(screen.getByText('Shareable node')).toBeInTheDocument();
});

test('clicking Shareable node calls onChange once with SHAREABLE_NODE', async () => {
  const onChange = fn();
  render(<PoolTypeControl value="STANDARD" onChange={onChange} />);

  await click(screen.getByRole('combobox'));
  await click(screen.getByText('Shareable node'));

  expect(onChange).toHaveBeenCalledTimes(1);
  expect(onChange).toHaveBeenCalledWith('SHAREABLE_NODE');
});

test('disabled disables the combobox', () => {
  render(<PoolTypeControl value="STANDARD" onChange={fn()} disabled />);

  const select = screen.getByRole('combobox').closest('.ant-select');
  expect(select).toHaveClass('ant-select-disabled');
});
