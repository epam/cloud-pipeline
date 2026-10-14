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
import {pipelineRun, renderWithStores, screen} from '@test';
import VSActions from './index';

// The versioned storages browse dialog, rendered hidden, lists these.
const pipelines = {value: []};

function renderActions (run) {
  renderWithStores(
    <VSActions run={run}>VERSIONED STORAGE</VSActions>,
    {pipelines}
  );
}

it('shows the versioned storages control for a running run', () => {
  renderActions(pipelineRun());

  expect(screen.getByText('VERSIONED STORAGE')).toBeInTheDocument();
});

it('hides the versioned storages control when the run node is unavailable', () => {
  renderActions(pipelineRun({tags: {NODE_UNAVAILABLE: 'true'}}));

  expect(screen.queryByText('VERSIONED STORAGE')).not.toBeInTheDocument();
});
