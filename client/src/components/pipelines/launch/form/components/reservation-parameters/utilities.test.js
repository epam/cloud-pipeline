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

import {stubApi} from '@test';
import capacityBlocks from '../../../../../../models/cluster/capacity-blocks';
import {
  buildLaunchParametersFromReservationParameters,
  readReservationParameters
} from './utilities';

// The store is a module-level singleton (like `models/preferences/PreferencesLoad`
// elsewhere), so every test starts it off the same way: as if nothing had been
// fetched yet.
beforeEach(() => {
  capacityBlocks.invalidateCache();
});

const reservedPool = {
  id: 1,
  name: 'reserved-pool',
  instanceType: 'm5.large',
  regionId: 1,
  launchConfig: {
    cpuRequestsEnabled: true,
    gpuRequestsEnabled: true,
    ramRequestsEnabled: true,
    cpuRequestsReserved: 1,
    gpuRequestsReserved: 1,
    ramRequestsReserved: 1,
    ramRequestsUnit: 'GiB',
    kubeAssignPolicy: {selector: {label: 'pool_id', value: '1'}},
    additionalParameters: {CP_CAP_CUSTOM: 'true'}
  }
};

const barePool = {
  id: 2,
  name: 'bare-pool',
  instanceType: 'm5.xlarge',
  regionId: 1
};

describe('buildLaunchParametersFromReservationParameters', () => {
  it('produces the three CP_CAP_REQUESTS_* parameters, additionalParameters ' +
    'and kubeAssignPolicy as podAssignPolicy for a pool with a launchConfig', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});

    const {parameters, podAssignPolicy} = await buildLaunchParametersFromReservationParameters(
      readReservationParameters({}),
      'm5.large',
      {},
      {regionId: 1}
    );

    expect(parameters.CP_CAP_REQUESTS_CPU).toEqual({value: '1'});
    expect(parameters.CP_CAP_REQUESTS_GPU).toEqual({value: '1'});
    expect(parameters.CP_CAP_REQUESTS_RAM).toEqual({value: 1});
    expect(parameters.CP_CAP_CUSTOM).toEqual({value: 'true'});
    expect(podAssignPolicy).toEqual({selector: {label: 'pool_id', value: '1'}});
  });

  it('produces none of them and no podAssignPolicy for a pool without a launchConfig', async () => {
    stubApi({'/cluster/pool/filter': [barePool]});

    const {parameters, podAssignPolicy} = await buildLaunchParametersFromReservationParameters(
      readReservationParameters({}),
      'm5.xlarge',
      {},
      {regionId: 1}
    );

    expect(parameters.CP_CAP_REQUESTS_CPU).toBeUndefined();
    expect(parameters.CP_CAP_REQUESTS_GPU).toBeUndefined();
    expect(parameters.CP_CAP_REQUESTS_RAM).toBeUndefined();
    expect(podAssignPolicy).toBeUndefined();
  });

  it('does not match a pool in another region', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});

    const {parameters, podAssignPolicy} = await buildLaunchParametersFromReservationParameters(
      readReservationParameters({}),
      'm5.large',
      {},
      {regionId: 2}
    );

    expect(parameters.CP_CAP_REQUESTS_CPU).toBeUndefined();
    expect(parameters.CP_CAP_REQUESTS_GPU).toBeUndefined();
    expect(parameters.CP_CAP_REQUESTS_RAM).toBeUndefined();
    expect(podAssignPolicy).toBeUndefined();
  });

  it('strips a previously-applied set of reservation parameters once the ' +
    'instance type stops being a capacity block', async () => {
    stubApi({'/cluster/pool/filter': [reservedPool]});
    const previouslyApplied = {
      CP_CAP_REQUESTS_CPU: {value: '1'},
      CP_CAP_REQUESTS_GPU: {value: '1'},
      CP_CAP_REQUESTS_RAM: {value: 1},
      CP_CAP_CUSTOM: {value: 'true'},
      SOME_OTHER_PARAMETER: {value: 'kept'}
    };

    const {parameters, podAssignPolicy} = await buildLaunchParametersFromReservationParameters(
      readReservationParameters(previouslyApplied),
      // not a capacity-block instance type - no matching pool
      'm5.2xlarge',
      previouslyApplied,
      {regionId: 1}
    );

    expect(parameters.CP_CAP_REQUESTS_CPU).toBeUndefined();
    expect(parameters.CP_CAP_REQUESTS_GPU).toBeUndefined();
    expect(parameters.CP_CAP_REQUESTS_RAM).toBeUndefined();
    expect(parameters.CP_CAP_CUSTOM).toBeUndefined();
    expect(parameters.SOME_OTHER_PARAMETER).toEqual({value: 'kept'});
    expect(podAssignPolicy).toBeUndefined();
  });
});
