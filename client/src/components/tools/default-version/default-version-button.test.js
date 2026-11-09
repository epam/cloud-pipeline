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
import DefaultVersionButton from './default-version-button';

// the dialog closes with an animation, so the helper waits until it is removed
async function confirm (title, answer = 'Yes') {
  expect(await screen.findByText(title)).toBeInTheDocument();
  await click(screen.getByRole('button', {name: answer}));
  await waitFor(() => expect(screen.queryByText(title)).not.toBeInTheDocument());
}

it('sets a version as the default one, once a user confirms it', async () => {
  const api = stubApi({'/tool/1/defaultVersion?version=1.1': {id: 1, defaultVersion: '1.1'}});
  const onChange = fn();
  render(
    <DefaultVersionButton
      tool={{id: 1, defaultVersion: '1.2'}}
      version="1.1"
      onChange={onChange}
    />
  );

  await click(screen.getByRole('button', {name: 'Set as default'}));
  await confirm("Are you sure you want to set version '1.1' as default?");

  await waitFor(() => expect(onChange).toHaveBeenCalledWith({id: 1, defaultVersion: '1.1'}));
  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({method: 'POST'}));
});

it('unsets the default version, once a user confirms it', async () => {
  const api = stubApi({'/tool/1/defaultVersion': {id: 1}});
  const onChange = fn();
  render(
    <DefaultVersionButton
      tool={{id: 1, defaultVersion: '1.2'}}
      version="1.2"
      onChange={onChange}
    />
  );

  await click(screen.getByRole('button', {name: 'Unset default'}));
  await confirm("Are you sure you want to unset version '1.2' as default?");

  await waitFor(() => expect(onChange).toHaveBeenCalled());
  expect(api.calls).toHaveLength(1);
  expect(api.calls[0]).toEqual(expect.objectContaining({method: 'DELETE'}));
});

it('does not change the default version, if a user cancels it', async () => {
  const api = stubApi();
  const onChange = fn();
  render(
    <DefaultVersionButton
      tool={{id: 1, defaultVersion: '1.2'}}
      version="1.2"
      onChange={onChange}
    />
  );

  await click(screen.getByRole('button', {name: 'Unset default'}));
  await confirm("Are you sure you want to unset version '1.2' as default?", 'No');

  expect(api.calls).toHaveLength(0);
  expect(onChange).not.toHaveBeenCalled();
});

it('does not call back, if the request fails', async () => {
  const api = stubApi();
  api.fail('/tool/1/defaultVersion?version=1.1', 'Version does not exist');
  const onChange = fn();
  render(<DefaultVersionButton tool={{id: 1}} version="1.1" onChange={onChange} />);

  await click(screen.getByRole('button', {name: 'Set as default'}));
  await confirm("Are you sure you want to set version '1.1' as default?");

  await waitFor(() => expect(api.calls).toHaveLength(1));
  expect(onChange).not.toHaveBeenCalled();
});

it('offers to set the latest version as default, while a default version is not set', () => {
  render(<DefaultVersionButton tool={{id: 1}} version="latest" />);

  expect(screen.getByRole('button', {name: 'Set as default'})).toBeInTheDocument();
});
