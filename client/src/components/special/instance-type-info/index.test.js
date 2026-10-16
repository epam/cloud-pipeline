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
import capacityBlocks from '../../../models/cluster/capacity-blocks';
import {instanceInfoString} from './index';

const reservedPool = {
  id: 1,
  name: 'reserved-pool',
  instanceType: 'm5.large',
  regionId: 1
};

beforeEach(() => {
  capacityBlocks.invalidateCache();
});

describe('instanceInfoString, showReservationTag', () => {
  it('renders the pool name as the tag for a matching type', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});
    await capacityBlocks.load();

    render(instanceInfoString(
      {name: 'm5.large'},
      {showReservationTag: true, regionId: 1, plainText: false}
    ));

    expect(screen.getByText('reserved-pool')).toBeInTheDocument();
    expect(
      instanceInfoString({name: 'm5.large'}, {showReservationTag: true, regionId: 1})
    ).toBe('m5.large (reserved-pool)');
  });

  it('renders nothing extra for a type with no matching pool', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});
    await capacityBlocks.load();

    render(instanceInfoString(
      {name: 'm5.xlarge'},
      {showReservationTag: true, regionId: 1, plainText: false}
    ));

    expect(screen.queryByText('reserved-pool')).toBeNull();
    expect(
      instanceInfoString({name: 'm5.xlarge'}, {showReservationTag: true, regionId: 1})
    ).toBe('m5.xlarge');
  });

  it('renders nothing extra when the matching pool is in another region', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});
    await capacityBlocks.load();

    render(instanceInfoString(
      {name: 'm5.large'},
      {showReservationTag: true, regionId: 2, plainText: false}
    ));

    expect(screen.queryByText('reserved-pool')).toBeNull();
    expect(
      instanceInfoString({name: 'm5.large'}, {showReservationTag: true, regionId: 2})
    ).toBe('m5.large');
  });

  it('does not look up a pool at all when showReservationTag is not set', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});
    await capacityBlocks.load();

    expect(instanceInfoString({name: 'm5.large'}, {regionId: 1})).toBe('m5.large');
  });
});
