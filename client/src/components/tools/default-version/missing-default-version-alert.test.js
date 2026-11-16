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
import {click, fn, render, screen, stubApi, waitFor} from '@test';
import MissingDefaultVersionAlert from './missing-default-version-alert';

const versions = ['latest', '1.2', '1.1'];
// the version is shown in bold, so the warning is found by the whole text of its message
const warning = () => screen.queryByText((content, element) => content.includes('does not exist') &&
  element.textContent.startsWith('The default version 1.3 does not exist.'));
const CONFIRMATION = "Are you sure you want to unset version '1.3' as default?";

it('warns, that the default version of a tool does not exist', () => {
  render(<MissingDefaultVersionAlert tool={{id: 1, defaultVersion: '1.3'}} versions={versions} />);

  expect(warning()).toBeInTheDocument();
});

it('does not warn, if the default version exists', () => {
  render(<MissingDefaultVersionAlert tool={{id: 1, defaultVersion: '1.2'}} versions={versions} />);

  expect(warning()).not.toBeInTheDocument();
});

it('does not warn, if a default version is not set', () => {
  render(<MissingDefaultVersionAlert tool={{id: 1}} versions={versions} />);

  expect(screen.queryByText(/does not exist/)).not.toBeInTheDocument();
});

it('unsets the default version, which does not exist, once a user confirms it', async () => {
  const api = stubApi({'/tool/1/defaultVersion': {id: 1}});
  const onChange = fn();
  render(
    <MissingDefaultVersionAlert
      tool={{id: 1, defaultVersion: '1.3'}}
      versions={versions}
      onChange={onChange}
    />
  );

  await click(screen.getByRole('button', {name: 'Unset'}));
  expect(await screen.findByText(CONFIRMATION)).toBeInTheDocument();
  await click(screen.getByRole('button', {name: 'Yes'}));

  await waitFor(() => expect(onChange).toHaveBeenCalled());
  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({method: 'DELETE'}));
});

it('does not offer to unset the default version to a user, who cannot change the tool', () => {
  render(
    <MissingDefaultVersionAlert
      tool={{id: 1, defaultVersion: '1.3'}}
      versions={versions}
      disabled
    />
  );

  expect(warning()).toBeInTheDocument();
  expect(screen.queryByRole('button', {name: 'Unset'})).not.toBeInTheDocument();
});
