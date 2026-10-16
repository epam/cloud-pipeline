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
import {observable} from 'mobx';
import {renderWithStores, screen, click, fn} from '@test';
import InstanceTypeSuggestion, {MAX_VISIBLE_SUGGESTIONS} from './index';

const regionsList = [
  {id: 1, name: 'eu-central-1', provider: 'aws'},
  {id: 2, name: 'eu-west-1', provider: 'aws'}
];

function awsRegionsStore (regionsValue = regionsList) {
  return observable({
    loaded: true,
    pending: false,
    value: regionsValue,
    getRegion: (id) => regionsValue.find((r) => r.id === id),
    getRegionByUID: (id) => regionsValue.find((r) => r.regionId === id)
  });
}

function stores () {
  return {awsRegions: awsRegionsStore()};
}

function pool (overrides = {}) {
  return {
    id: 1,
    name: 'reserved-pool-1',
    instanceType: 'm5.large',
    regionId: 1,
    count: 2,
    endDate: '2024-06-01 00:00:00.000',
    isCapacityReservation: true,
    ...overrides
  };
}

function instanceType (name) {
  return {name, instanceFamily: 'General purpose'};
}

function renderSuggestion (props = {}) {
  return renderWithStores(
    <InstanceTypeSuggestion
      pools={[]}
      reservations={[]}
      {...props}
    />,
    stores()
  );
}

describe('InstanceTypeSuggestion', () => {
  it('renders nothing when there are no suggestions', () => {
    const {container} = renderSuggestion();
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a suggestion for a matching pool', () => {
    renderSuggestion({
      pools: [pool()],
      instanceTypes: [instanceType('m5.large')]
    });
    expect(screen.getByText('m5.large')).toBeInTheDocument();
    expect(screen.getByText(/2 nodes reserved/)).toBeInTheDocument();
    expect(screen.getByText(/available until 1 Jun 2024/)).toBeInTheDocument();
  });

  // The comma between the region and the count is the count's own, so a pool
  // with no known count must not leave one dangling after the region.
  it('separates the reserved count from the region with a comma', () => {
    const {container} = renderSuggestion({
      pools: [pool()],
      instanceTypes: [instanceType('m5.large')]
    });
    expect(container.querySelector('[class*="infoMain"]').textContent)
      .toBe('m5.largeeu-central-1, 2 nodes reserved');
  });

  it('renders no trailing comma when the reserved count is unknown', () => {
    const {container} = renderSuggestion({
      pools: [pool({count: undefined})],
      reservations: [],
      instanceTypes: [instanceType('m5.large')]
    });
    expect(container.querySelector('[class*="infoMain"]').textContent)
      .toBe('m5.largeeu-central-1');
  });

  it('filters out a pool outside the selected region', () => {
    renderSuggestion({
      pools: [pool({regionId: 2})],
      regionId: 1,
      instanceTypes: [instanceType('m5.large')]
    });
    expect(screen.queryByText('m5.large')).toBeNull();
  });

  it('keeps a pool matching the selected region', () => {
    renderSuggestion({
      pools: [pool({regionId: 1})],
      regionId: 1,
      instanceTypes: [instanceType('m5.large')]
    });
    expect(screen.getByText('m5.large')).toBeInTheDocument();
  });

  it('filters out a pool whose instance type the node type select does not offer', () => {
    renderSuggestion({
      pools: [pool({instanceType: 'm5.large'})],
      instanceTypes: [instanceType('m5.xlarge')]
    });
    expect(screen.queryByText('m5.large')).toBeNull();
  });

  it('does not filter by instance type when the offered list has not loaded yet', () => {
    renderSuggestion({
      pools: [pool({instanceType: 'm5.large'})],
      instanceTypes: []
    });
    expect(screen.getByText('m5.large')).toBeInTheDocument();
  });

  it('falls back to the matched reservation for the count and the end date', () => {
    renderSuggestion({
      pools: [pool({count: undefined, endDate: undefined, id: 7})],
      reservations: [{
        nodePoolId: 7,
        status: 'ACTIVE',
        instanceCount: 5,
        endDate: '2024-07-15 00:00:00.000'
      }],
      instanceTypes: [instanceType('m5.large')]
    });
    expect(screen.getByText(/5 nodes reserved/)).toBeInTheDocument();
    expect(screen.getByText(/available until 15 Jul 2024/)).toBeInTheDocument();
  });

  it('hides the count and the end date when neither the pool nor the reservation has them', () => {
    renderSuggestion({
      pools: [pool({count: undefined, endDate: undefined})],
      instanceTypes: [instanceType('m5.large')]
    });
    expect(screen.queryByText(/node.* reserved/)).toBeNull();
    expect(screen.queryByText(/available until/)).toBeNull();
  });

  it('calls onSelect with the instance type and the pool when a row is clicked', async () => {
    const onSelect = fn();
    const matchedPool = pool();
    renderSuggestion({
      pools: [matchedPool],
      instanceTypes: [instanceType('m5.large')],
      onSelect
    });
    await click(screen.getByText('use this type'));
    expect(onSelect).toHaveBeenCalledWith('m5.large', matchedPool);
  });

  it('does not call onSelect for the already selected instance type', async () => {
    const onSelect = fn();
    renderSuggestion({
      pools: [pool()],
      instanceTypes: [instanceType('m5.large')],
      currentInstanceType: 'm5.large',
      onSelect
    });
    expect(screen.getByText('selected')).toBeInTheDocument();
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('lists at most MAX_VISIBLE_SUGGESTIONS rows and offers the rest behind "show more"', () => {
    const pools = [1, 2, 3, 4, 5].map((id) => pool({
      id,
      name: `reserved-pool-${id}`,
      instanceType: `m5.${id}xlarge`
    }));
    renderSuggestion({
      pools,
      instanceTypes: pools.map((p) => instanceType(p.instanceType))
    });
    expect(screen.getAllByText('use this type')).toHaveLength(MAX_VISIBLE_SUGGESTIONS);
    expect(screen.getByText(`Show ${pools.length - MAX_VISIBLE_SUGGESTIONS} more`))
      .toBeInTheDocument();
  });

  it('reveals the hidden suggestions when "show more" is clicked', async () => {
    const pools = [1, 2, 3, 4, 5].map((id) => pool({
      id,
      name: `reserved-pool-${id}`,
      instanceType: `m5.${id}xlarge`
    }));
    renderSuggestion({
      pools,
      instanceTypes: pools.map((p) => instanceType(p.instanceType))
    });
    expect(screen.queryByText('m5.5xlarge')).toBeNull();
    await click(screen.getByText(`Show ${pools.length - MAX_VISIBLE_SUGGESTIONS} more`));
    expect(await screen.findByText('m5.5xlarge')).toBeInTheDocument();
  });

  it('shows no "show more" link when every suggestion already fits', () => {
    renderSuggestion({
      pools: [pool()],
      instanceTypes: [instanceType('m5.large')]
    });
    expect(screen.queryByText(/Show \d+ more/)).toBeNull();
  });
});
