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

import {click, screen, stubApi} from '@test';
import deleteToolConfirm from './index';

const DEFAULT_VERSION_WARNING = 'You are going to delete the default version of the tool.';

const router = {push: () => {}};

beforeEach(() => {
  stubApi({
    '/tool/load?image=1': {
      id: 1,
      registry: 'registry',
      image: 'library/tool',
      defaultVersion: '1.2'
    },
    '/run/filter': {totalCount: 0, elements: []}
  });
});

async function cancel (confirmation) {
  await click(await screen.findByRole('button', {name: 'Cancel'}));
  expect(await confirmation).toBe(false);
}

it('warns that the default version of a tool is going to be deleted', async () => {
  const confirmation = deleteToolConfirm({tool: 1, version: '1.2'}, router);

  expect(await screen.findByText(DEFAULT_VERSION_WARNING)).toBeInTheDocument();
  await cancel(confirmation);
});

it('does not warn about another version', async () => {
  const confirmation = deleteToolConfirm({tool: 1, version: 'latest'}, router);

  expect(await screen.findByText(/Are you sure you want to delete version 'latest'/))
    .toBeInTheDocument();
  expect(screen.queryByText(DEFAULT_VERSION_WARNING)).not.toBeInTheDocument();
  await cancel(confirmation);
});

it('warns that the default version of the same image is going to be deleted', async () => {
  const confirmation = deleteToolConfirm({tool: 1, version: 'alias', aliases: ['1.2']}, router);

  expect(await screen.findByText(DEFAULT_VERSION_WARNING)).toBeInTheDocument();
  expect(screen.getByText(/refers to the same image/).textContent).toBe(
    'The default version 1.2 refers to the same image, so the registry deletes it as well. '
  );
  await cancel(confirmation);
});

it('tells that no latest version is left, if it is an image of the default version', async () => {
  const confirmation = deleteToolConfirm({tool: 1, version: 'latest', aliases: ['1.2']}, router);

  expect(await screen.findByText(/Once it is deleted/).then(e => e.textContent)).toBe(
    'Once it is deleted, the tool has no default version, and no latest version either.'
  );
  await cancel(confirmation);
});

it('does not warn about another version of another image', async () => {
  const confirmation = deleteToolConfirm({tool: 1, version: '1.0', aliases: ['1.1']}, router);

  expect(await screen.findByText(/Are you sure you want to delete version '1.0'/))
    .toBeInTheDocument();
  expect(screen.queryByText(DEFAULT_VERSION_WARNING)).not.toBeInTheDocument();
  await cancel(confirmation);
});

it('does not tell about the same image when the default version itself is deleted', async () => {
  const confirmation = deleteToolConfirm({tool: 1, version: '1.2', aliases: ['alias']}, router);

  expect(await screen.findByText(DEFAULT_VERSION_WARNING)).toBeInTheDocument();
  expect(screen.queryByText(/refers to the same image/)).not.toBeInTheDocument();
  await cancel(confirmation);
});
