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

import {render, screen} from '@test';
import {getColumn} from './column-elapsed';

function makeRun (overrides = {}) {
  return {
    status: 'RUNNING',
    startDate: '2024-01-01T00:00:00.000Z',
    pricePerHour: 10,
    instance: {
      nodeType: 'm5.large',
      cloudRegionId: 1,
      // The API sends 0, not null, for a run that is not on a pool node.
      poolId: 0
    },
    pipelineRunParameters: [],
    ...overrides
  };
}

function renderColumn (run) {
  const {render: cellRender} = getColumn();
  return render(cellRender(run));
}

describe('column-elapsed estimated price', () => {
  it('shows the estimated price for a run on its own node', async () => {
    renderColumn(makeRun());

    expect(await screen.findByText(/Cost:/)).toBeTruthy();
  });

  it('hides the estimated price for a run on a pool node', async () => {
    renderColumn(makeRun({instance: {nodeType: 'm5.large', cloudRegionId: 1, poolId: 5}}));

    expect(screen.queryByText(/Cost:/)).toBeNull();
  });

  it('hides the estimated price for a run with reservation requests', async () => {
    renderColumn(makeRun({
      pipelineRunParameters: [{name: 'CP_CAP_REQUESTS_CPU', value: '2'}]
    }));

    expect(screen.queryByText(/Cost:/)).toBeNull();
  });
});
