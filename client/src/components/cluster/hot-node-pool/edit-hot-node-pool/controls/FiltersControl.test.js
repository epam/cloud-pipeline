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
import {observable} from 'mobx';
import {renderWithStores, screen, click, fn} from '@test';
import FiltersControl from './FiltersControl';

const loaded = (value) => observable({loaded: true, pending: false, value});

const baseStores = () => ({
  users: loaded([]),
  pipelines: loaded([]),
  allConfigurations: loaded([])
});

test('with no filters, the condition select and Add filter button render, no error row', () => {
  renderWithStores(
    <FiltersControl roles={loaded([])} disabled={false} filters={{}} onChange={fn()} />,
    baseStores()
  );
  expect(screen.getByText('Condition:')).toBeInTheDocument();
  expect(screen.getByText('Add filter')).toBeInTheDocument();
  expect(document.querySelector('.cp-error')).toBeNull();
});

test('the error row shows only when both error and touched are truthy', () => {
  renderWithStores(
    <FiltersControl
      roles={loaded([])}
      disabled={false}
      filters={{}}
      onChange={fn()}
      error="Some conditions are invalid"
      touched={false}
    />,
    baseStores()
  );
  expect(screen.queryByText('Some conditions are invalid')).not.toBeInTheDocument();
});

test('the error row shows when both error and touched are set', () => {
  renderWithStores(
    <FiltersControl
      roles={loaded([])}
      disabled={false}
      filters={{}}
      onChange={fn()}
      error="Some conditions are invalid"
      touched
    />,
    baseStores()
  );
  expect(screen.getByText('Some conditions are invalid')).toBeInTheDocument();
});

test('disabled reaches the condition select and the Add filter button', () => {
  renderWithStores(
    <FiltersControl roles={loaded([])} disabled filters={{}} onChange={fn()} />,
    baseStores()
  );
  expect(screen.getByText('Add filter').closest('button')).toBeDisabled();
  expect(screen.getByRole('combobox').closest('.ant-select')).toHaveClass('ant-select-disabled');
});

test('choosing a condition calls onChange with the shape onChangeFilters expects', async () => {
  const onChange = fn();
  renderWithStores(
    <FiltersControl
      roles={loaded([])}
      disabled={false}
      filters={{operator: undefined, filters: []}}
      onChange={onChange}
    />,
    baseStores()
  );

  await click(screen.getByRole('combobox'));
  await click(screen.getByText('Matches any filter ("or")'));

  expect(onChange).toHaveBeenCalledWith({operator: 'OR', filters: []});
});

test('adding a filter calls onChange with a new, empty criterion appended', async () => {
  const onChange = fn();
  renderWithStores(
    <FiltersControl
      roles={loaded([])}
      disabled={false}
      filters={{operator: 'AND', filters: []}}
      onChange={onChange}
    />,
    baseStores()
  );

  await click(screen.getByText('Add filter'));

  expect(onChange).toHaveBeenCalledWith({operator: 'AND', filters: [{}]});
});

test('roles reaches the nested FilterControl for a role-based criterion', async () => {
  renderWithStores(
    <FiltersControl
      roles={loaded([{name: 'ROLE_ADMIN', predefined: false}])}
      disabled={false}
      filters={{operator: 'AND', filters: [{}]}}
      onChange={fn()}
    />,
    baseStores()
  );

  // Two comboboxes: the top-level Condition select and the criterion's type select.
  const [, typeSelect] = screen.getAllByRole('combobox');
  await click(typeSelect);
  await click(screen.getByText('Run owner group'));

  // The group selector appears, backed by the `roles` store passed as a prop.
  const selects = screen.getAllByRole('combobox');
  await click(selects[selects.length - 1]);
  expect(await screen.findByText('ADMIN')).toBeInTheDocument();
});
