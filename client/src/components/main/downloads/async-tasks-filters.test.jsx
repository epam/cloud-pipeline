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
import {renderWithStores, screen, within, click, fn} from '@test';
import AsyncTasksFilters from './async-tasks-filters';

// A plain observable object is a sufficient store double (test/AGENTS.md
// rule 10) - no @observable class fields needed.
function authenticatedUserInfoDouble (admin = true) {
  return observable({
    loaded: true,
    value: {userName: 'current-user', admin}
  });
}

function usersDouble () {
  return observable({
    loaded: true,
    value: [
      {id: 1, userName: 'current-user'},
      {id: 2, userName: 'someone-else'}
    ]
  });
}

function stores (overrides = {}) {
  return {
    authenticatedUserInfo: overrides.authenticatedUserInfo || authenticatedUserInfoDouble(),
    users: overrides.users || usersDouble()
  };
}

// Finds the Filter wrapper `<div>` for a labelled row by its rendered
// "Label:" text.
function row (label) {
  return screen.getByText(`${label}:`).closest('div');
}

test('choosing an owner calls onChange with that owner', async () => {
  const onChange = fn();
  renderWithStores(<AsyncTasksFilters owner={undefined} onChange={onChange} />, stores());

  await click(within(row('Owner')).getByRole('combobox'));
  await click(screen.getByRole('menuitem', {name: 'someone-else'}));

  expect(onChange).toHaveBeenCalledWith('someone-else');
});

test('renders the owner control for an administrator', () => {
  renderWithStores(<AsyncTasksFilters owner={undefined} onChange={fn()} />, stores());

  expect(screen.getByText('Owner:')).toBeInTheDocument();
});

test('renders nothing for a non-administrator', () => {
  const {container} = renderWithStores(
    <AsyncTasksFilters owner={undefined} onChange={fn()} />,
    stores({authenticatedUserInfo: authenticatedUserInfoDouble(false)})
  );

  expect(screen.queryByText('Owner:')).not.toBeInTheDocument();
  expect(container).toBeEmptyDOMElement();
});

test('the current user\'s owner option is marked "(you)", another user\'s is not', async () => {
  renderWithStores(<AsyncTasksFilters owner={undefined} onChange={fn()} />, stores());

  await click(within(row('Owner')).getByRole('combobox'));

  const own = screen.getByRole('menuitem', {name: /current-user/});
  expect(within(own).getByText('(you)')).toBeInTheDocument();
  const other = screen.getByRole('menuitem', {name: 'someone-else'});
  expect(within(other).queryByText('(you)')).not.toBeInTheDocument();
});
