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
import {renderWithStores} from '@test';
import PoolCard from './pool-card';

const loaded = (value) => observable({loaded: true, pending: false, value});

const regionsList = [{id: 1, name: 'eu-central-1', provider: 'aws'}];

function stores () {
  return {
    awsRegions: observable({
      loaded: true,
      pending: false,
      value: regionsList,
      getRegion: (id) => regionsList.find((r) => r.id === id),
      getRegionByUID: (id) => regionsList.find((r) => r.regionId === id)
    }),
    cloudRegionsInfo: loaded(regionsList),
    allInstanceTypes: loaded([])
  };
}

function pool (overrides = {}) {
  return {
    id: 7,
    name: 'reserved-pool',
    regionId: 1,
    instanceType: 'm5.large',
    instanceDisk: 50,
    priceType: 'ON_DEMAND',
    count: 2,
    owner: 'MANAGER',
    ...overrides
  };
}

function renderCard (props = {}) {
  return renderWithStores(
    <PoolCard pool={pool()} {...props} />,
    stores()
  );
}

// The card's three actions are icon-only buttons, so each is found by its antd icon class
// rather than by text.
function icons (container, type) {
  return container.querySelectorAll(`.anticon-${type}`);
}

describe('PoolCard actions', () => {
  it('offers no action at all by default', () => {
    const {container} = renderCard();
    expect(icons(container, 'edit')).toHaveLength(0);
    expect(icons(container, 'area-chart')).toHaveLength(0);
    expect(icons(container, 'delete')).toHaveLength(0);
  });

  it('shows the edit button when editing is allowed', () => {
    const {container} = renderCard({canEdit: true});
    expect(icons(container, 'edit')).toHaveLength(1);
  });

  // The usage page is gated on ADMIN_OR_CLUSTER_READER, which a node pool manager who may
  // edit its own pool does not necessarily hold - so the two buttons move independently.
  it('hides the usage button while still showing the edit one', () => {
    const {container} = renderCard({canEdit: true, canViewUsage: false});
    expect(icons(container, 'edit')).toHaveLength(1);
    expect(icons(container, 'area-chart')).toHaveLength(0);
  });

  it('shows the usage button when the usage page is reachable', () => {
    const {container} = renderCard({canViewUsage: true});
    expect(icons(container, 'area-chart')).toHaveLength(1);
  });

  it('shows the delete button when deleting is allowed', () => {
    const {container} = renderCard({canDelete: true});
    expect(icons(container, 'delete')).toHaveLength(1);
  });
});
