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

import {render, screen, stubApi} from '@test';
import capacityBlocks from '../../../../models/cluster/capacity-blocks';
import {getColumn} from './column-elapsed';

const reservedPool = {
  id: 1,
  name: 'reserved-pool',
  instanceType: 'm5.large',
  regionId: 1
};

function makeRun (overrides = {}) {
  return {
    status: 'RUNNING',
    startDate: '2024-01-01T00:00:00.000Z',
    pricePerHour: 10,
    instance: {
      nodeType: 'm5.large',
      cloudRegionId: 1
    },
    ...overrides
  };
}

function renderColumn (run) {
  const {render: cellRender} = getColumn();
  return render(cellRender(run));
}

beforeEach(() => {
  capacityBlocks.invalidateCache();
});

describe('column-elapsed estimated price', () => {
  it('shows the estimated price for an instance type with no capacity-block pool', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});
    await capacityBlocks.load();

    renderColumn(makeRun({instance: {nodeType: 'm5.xlarge', cloudRegionId: 1}}));

    expect(await screen.findByText(/Cost:/)).toBeTruthy();
  });

  it('hides the estimated price for an instance type served by a capacity-block pool', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});
    await capacityBlocks.load();

    renderColumn(makeRun());

    expect(screen.queryByText(/Cost:/)).toBeNull();
  });

  it('shows the estimated price when the matching pool is in another region', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});
    await capacityBlocks.load();

    renderColumn(makeRun({instance: {nodeType: 'm5.large', cloudRegionId: 2}}));

    expect(await screen.findByText(/Cost:/)).toBeTruthy();
  });
});
