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
import {renderWithStores, screen, click, change} from '@test';
import HiddenObjects from '../../../utils/hidden-objects';
import EditToolFormParameters from './EditToolFormParameters';

const loaded = (value) => observable({loaded: true, pending: false, value});

async function addParameterNamed (name, allowNodeUpRetryCount) {
  renderWithStores(<EditToolFormParameters />, {
    preferences: observable({allowNodeUpRetryCount}),
    runDefaultParameters: loaded([]),
    authenticatedUserInfo: loaded({admin: false, roles: []}),
    dtsList: loaded([]),
    awsRegions: loaded([]),
    uiLaunchParametersConfiguration: observable({localFiles: {enabled: false}}),
    [HiddenObjects.injectionName]: {}
  });
  await click(screen.getByRole('button', {name: 'Add parameter'}));
  const [nameInput] = screen.getAllByRole('textbox');
  await change(nameInput, name);
}

test('reserves CP_NODEUP_RETRY_COUNT with ui.launch.allow.nodeup.count on', async () => {
  await addParameterNamed('CP_NODEUP_RETRY_COUNT', true);

  expect(screen.getByText('Parameter name is reserved')).toBeInTheDocument();
});

test('allows CP_NODEUP_RETRY_COUNT with ui.launch.allow.nodeup.count off', async () => {
  await addParameterNamed('CP_NODEUP_RETRY_COUNT', false);

  expect(screen.queryByText('Parameter name is reserved')).not.toBeInTheDocument();
});

test('allows any other name with ui.launch.allow.nodeup.count on', async () => {
  await addParameterNamed('OTHER', true);

  expect(screen.queryByText('Parameter name is reserved')).not.toBeInTheDocument();
});
