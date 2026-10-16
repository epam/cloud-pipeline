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
import {hover, render, screen} from '@test';
import DefaultVersionLabel from './default-version-label';

const versions = ['latest', '1.2', '1.1'];

it('marks the default version set for a tool', async () => {
  render(<DefaultVersionLabel tool={{defaultVersion: '1.2'}} version="1.2" versions={versions} />);

  await hover(screen.getByText('default'));

  expect(await screen.findByText('This version is launched, if a version is not specified'))
    .toBeInTheDocument();
});

it('marks the latest version of a tool without a default version', async () => {
  render(<DefaultVersionLabel tool={{}} version="latest" versions={versions} />);

  await hover(screen.getByText('default'));

  expect(await screen.findByText('A default version is not set, so the latest version is used'))
    .toBeInTheDocument();
});

it('marks the only version of a tool without a default and a latest version', async () => {
  render(<DefaultVersionLabel tool={{}} version="1.1" versions={['1.1']} />);

  await hover(screen.getByText('default'));

  expect(await screen.findByText('A default version is not set, so the only version is used'))
    .toBeInTheDocument();
});

it('does not mark other versions', () => {
  render(
    <div>
      <DefaultVersionLabel tool={{defaultVersion: '1.2'}} version="latest" versions={versions} />
      <DefaultVersionLabel tool={{defaultVersion: '1.2'}} version="1.1" versions={versions} />
    </div>
  );

  expect(screen.queryByText('default')).not.toBeInTheDocument();
});

it('tells that the default version of a tool does not exist anymore', async () => {
  const tool = {defaultVersion: '1.3'};
  render(<DefaultVersionLabel tool={tool} version="latest" versions={versions} />);

  await hover(screen.getByText('default'));

  expect(
    await screen.findByText('The default version 1.3 does not exist, so the latest version is used')
  ).toBeInTheDocument();
});
