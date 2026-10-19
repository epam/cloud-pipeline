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
import ModeControl from './ModeControl';

test('renders the label and shows Default as the current value', () => {
  render(<ModeControl value="DEFAULT" onChange={fn()} />);
  expect(screen.getByText('Mode:')).toBeInTheDocument();
  expect(screen.getByText('Default')).toBeInTheDocument();
});

test('opening the combobox shows both Default and Future dated options', async () => {
  render(<ModeControl value="DEFAULT" onChange={fn()} />);

  await click(screen.getByRole('combobox'));

  expect(screen.getAllByText('Default').length).toBeGreaterThan(0);
  expect(screen.getByText('Future dated')).toBeInTheDocument();
});

test('clicking Future dated calls onChange once with FUTURE_DATED', async () => {
  const onChange = fn();
  render(<ModeControl value="DEFAULT" onChange={onChange} />);

  await click(screen.getByRole('combobox'));
  await click(screen.getByText('Future dated'));

  expect(onChange).toHaveBeenCalledTimes(1);
  expect(onChange).toHaveBeenCalledWith('FUTURE_DATED');
});

test('disabled disables the combobox', () => {
  render(<ModeControl value="DEFAULT" onChange={fn()} disabled />);

  const select = screen.getByRole('combobox').closest('.ant-select');
  expect(select).toHaveClass('ant-select-disabled');
});

test('with modes={["FUTURE_DATED"]} only the Future dated option is offered', async () => {
  render(<ModeControl modes={['FUTURE_DATED']} onChange={fn()} />);

  await click(screen.getByRole('combobox'));

  expect(screen.queryByText('Default')).not.toBeInTheDocument();
  expect(screen.getByText('Future dated')).toBeInTheDocument();
});
