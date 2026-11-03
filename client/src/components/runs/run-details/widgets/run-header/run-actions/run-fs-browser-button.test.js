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
import {flushPromises, pipelineRun, renderWithStores, screen, stubApi, waitFor} from '@test';
import preferences from '../../../../../../models/preferences/PreferencesLoad';
import RunFsBrowserButton from './run-fs-browser-button';

// MultizoneUrl reads the region URLs of the file browser through this store only.
const multiZoneManager = {
  getSortedRegionsWithUrls: (configuration) => Object.entries(configuration || {})
    .map(([region, url]) => ({region, url}))
};

async function renderButton (run) {
  const api = stubApi({
    '/preferences': [],
    [`/run/${run.id}/fsbrowser`]: {'eu-west-1': `https://edge/fsbrowser/${run.id}`}
  });
  preferences.invalidateCache();
  await preferences.fetch();
  renderWithStores(<RunFsBrowserButton run={run} />, {preferences, multiZoneManager});
  await waitFor(() => expect(api.calls.map((call) => call.path))
    .toContain(`/run/${run.id}/fsbrowser`));
  await flushPromises();
}

it('shows BROWSE for a running run', async () => {
  await renderButton(pipelineRun());

  expect(await screen.findByText('BROWSE')).toBeInTheDocument();
});

it('hides BROWSE when the run node is unavailable', async () => {
  await renderButton(pipelineRun({tags: {NODE_UNAVAILABLE: 'true'}}));

  expect(screen.queryByText('BROWSE')).not.toBeInTheDocument();
});
