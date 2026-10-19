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
import {Select} from 'antd';
import {render, screen, click, fn, stubApi} from '@test';
import capacityBlocks from '../../../models/cluster/capacity-blocks';
import {getCapacityBlockOptions} from '../../../models/cluster/capacity-blocks/utilities';
import {instanceInfoString, getSelectOptions} from './index';

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

describe('getSelectOptions, the "Reserved capacity blocks" group', () => {
  const instanceTypes = [
    {name: 'm5.large', instanceFamily: 'General purpose', vcpu: 2, memory: 8},
    {name: 'm5.4xlarge', instanceFamily: 'General purpose', vcpu: 16, memory: 64}
  ];

  function renderOptions (options) {
    render(<Select>{getSelectOptions(instanceTypes, options)}</Select>);
  }

  it('prepends the group, first, when a pool qualifies', async () => {
    const pool = {id: 351, instanceType: 'm5.large', name: 'pool-351', regionId: 1};
    const capacityBlockPools = getCapacityBlockOptions([pool], instanceTypes, {regionId: 1});

    renderOptions({capacityBlockPools});
    await click(screen.getByRole('combobox'));

    expect(screen.getByText('Reserved capacity blocks')).toBeInTheDocument();
    const dropdownText = document.body.textContent;
    expect(dropdownText.indexOf('Reserved capacity blocks'))
      .toBeLessThan(dropdownText.indexOf('General purpose'));
  });

  it('tags each capacity-block entry with its own pool name', async () => {
    const poolA = {id: 1, instanceType: 'm5.large', name: 'pool-a', regionId: 1};
    const poolB = {id: 2, instanceType: 'm5.4xlarge', name: 'pool-b', regionId: 1};
    const capacityBlockPools = getCapacityBlockOptions([poolA, poolB], instanceTypes, {
      regionId: 1
    });

    renderOptions({capacityBlockPools});
    await click(screen.getByRole('combobox'));

    expect(screen.getByText('pool-a')).toBeInTheDocument();
    expect(screen.getByText('pool-b')).toBeInTheDocument();
  });

  it('values a capacity-block option with the encoded pool value', async () => {
    const pool = {id: 351, instanceType: 'm5.large', name: 'pool-351', regionId: 1};
    const capacityBlockPools = getCapacityBlockOptions([pool], instanceTypes, {regionId: 1});
    const onChange = fn();

    render(
      <Select onChange={onChange}>{getSelectOptions(instanceTypes, {capacityBlockPools})}</Select>
    );
    await click(screen.getByRole('combobox'));
    await click(screen.getByText('pool-351'));

    expect(onChange).toHaveBeenCalledWith('pool:351');
  });

  it('omits the group entirely when no pool qualifies', async () => {
    renderOptions({capacityBlockPools: []});
    await click(screen.getByRole('combobox'));

    expect(screen.queryByText('Reserved capacity blocks')).toBeNull();
  });

  it('omits the group when capacityBlockPools is not given at all', async () => {
    renderOptions({});
    await click(screen.getByRole('combobox'));

    expect(screen.queryByText('Reserved capacity blocks')).toBeNull();
  });

  it('suppresses the pool tag on the plain options once the group renders', async () => {
    const pool = {id: 351, instanceType: 'm5.large', name: 'pool-351', regionId: 1};
    stubApi({'/cluster/pool/filter': [pool]});
    await capacityBlocks.load();
    const capacityBlockPools = getCapacityBlockOptions([pool], instanceTypes, {regionId: 1});

    // A caller is never meant to pass both, but the plain options must not
    // carry the pool tag regardless - picking one of them means *not* a
    // capacity block.
    renderOptions({capacityBlockPools, showReservationTag: true, regionId: 1});
    await click(screen.getByRole('combobox'));

    expect(screen.getAllByText('pool-351')).toHaveLength(1);
  });
});
