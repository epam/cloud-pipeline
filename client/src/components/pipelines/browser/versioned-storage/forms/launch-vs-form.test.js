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
import {click, renderWithStores, screen, stubApi} from '@test';
import LaunchVSForm from './launch-vs-form';

const stores = (tool) => ({
  dockerRegistries: {
    loaded: true,
    value: {
      registries: [{
        id: 1,
        path: 'registry',
        groups: [{id: 1, name: 'library', tools: [tool]}]
      }]
    }
  },
  authenticatedUserInfo: {
    loaded: true,
    value: {groups: [], roles: []}
  }
});

async function selectTool (tool) {
  renderWithStores(<LaunchVSForm visible />, stores(tool));
  await click(await screen.findByText('tool'));
}

beforeEach(() => {
  stubApi({'/tool/1/tags': ['latest', '1.2', '1.1']});
});

it('selects and labels the default version of a tool', async () => {
  await selectTool({id: 1, image: 'library/tool', defaultVersion: '1.2'});

  expect(await screen.findByText('1.2 (default)')).toBeInTheDocument();
});

it('selects and labels the latest version of a tool without a default version', async () => {
  await selectTool({id: 1, image: 'library/tool'});

  expect(await screen.findByText('latest (default)')).toBeInTheDocument();
});
