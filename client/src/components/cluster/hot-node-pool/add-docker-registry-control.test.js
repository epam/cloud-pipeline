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
import {click, fn, focus, renderWithStores, screen, stubApi, type, waitFor, within} from '@test';
import AddDockerRegistryControl from './add-docker-registry-control';

const dockerRegistries = (...tools) => ({
  loaded: true,
  fetchIfNeededOrWait: () => Promise.resolve(),
  value: {
    registries: [{
      id: 1,
      path: 'registry',
      groups: [{id: 1, name: 'library', tools}]
    }]
  }
});

const versions = {
  versions: [{version: 'latest'}, {version: '1.2'}, {version: '1.1'}]
};

beforeEach(() => {
  stubApi({
    '/tool/1/attributes': versions,
    '/tool/2/attributes': versions
  });
});

// a pool image, which version does not exist anymore, is replaced by the default version
function renderControl (tool, onChange) {
  renderWithStores(
    <AddDockerRegistryControl docker="registry/library/tool:2.0" onChange={onChange} />,
    {dockerRegistries: dockerRegistries(tool)}
  );
}

it('selects the default version of a tool', async () => {
  const onChange = fn();

  renderControl({id: 1, image: 'library/tool', defaultVersion: '1.2'}, onChange);

  await waitFor(() => expect(onChange).toHaveBeenCalledWith('registry/library/tool:1.2'));
});

it('selects the latest version of a tool without a default version', async () => {
  const onChange = fn();

  renderControl({id: 1, image: 'library/tool'}, onChange);

  await waitFor(() => expect(onChange).toHaveBeenCalledWith('registry/library/tool:latest'));
});

it('selects the default version of a tool, which replaces the pool image', async () => {
  const onChange = fn();
  renderWithStores(
    <AddDockerRegistryControl docker="registry/library/ubuntu:latest" onChange={onChange} />,
    {
      dockerRegistries: dockerRegistries(
        {id: 2, image: 'library/ubuntu'},
        {id: 1, image: 'library/tool', defaultVersion: '1.2'}
      )
    }
  );
  await waitFor(() => expect(onChange).toHaveBeenCalledWith('registry/library/ubuntu:latest'));

  const [imageSelect] = screen.getAllByRole('combobox');
  await click(imageSelect);
  const search = within(imageSelect).getByRole('textbox');
  // the control clears its search text, once the field is focused
  await focus(search);
  await type(search, 'tool');
  await click(await screen.findByTitle('registry > library > tool'));

  await waitFor(() => expect(onChange).toHaveBeenCalledWith('registry/library/tool:1.2'));
});
