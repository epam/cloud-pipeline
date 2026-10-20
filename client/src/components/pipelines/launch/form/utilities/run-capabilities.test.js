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
import capacityBlocks from '../../../../../models/cluster/capacity-blocks';
import {getReservationParametersConfig} from '../components/reservation-parameters/utilities';
import {isEnabledByReservationConfig} from './run-capabilities';

// The store is a module-level singleton, so every test starts it off as if
// nothing had been fetched yet - the same arrangement
// `reservation-parameters/utilities.test.js` uses.
beforeEach(() => {
  capacityBlocks.invalidateCache();
});

// `RunCapabilities#setReservationConfig` (the component method this bug is
// in) is a thin, untestable-in-isolation wrapper around these two exported,
// pure-enough functions: it resolves a config with `getReservationParametersConfig`
// and feeds it to `isEnabledByReservationConfig`. Mounting the component itself
// would additionally require stubbing `preferences`, `dockerRegistries`,
// `fetchToolOS` and `fetchToolDefaultParameters` for no extra coverage of the
// bug this fixes, so - as with `LaunchPipelineForm.js` and `RunModal.open`'s
// dialog flow elsewhere on this branch - the fix is tested at this seam
// instead of through a render.
const privilegedCapability = {privileged: true};

describe('isEnabledByReservationConfig, fed a pool explicitly chosen among ' +
  'two backing the same instance type and region', () => {
  // Two pools backing one instance type in one region, with opposite
  // `kubeAssignPolicy.securityContext.privileged` settings - exactly the
  // scenario where resolving "the wrong pool" silently shows or hides a
  // privileged capability.
  const lowerIdPool = {
    id: 10,
    name: 'lower-id-pool',
    instanceType: 'm5.2xlarge',
    regionId: 1,
    launchConfig: {
      kubeAssignPolicy: {securityContext: {privileged: false}}
    }
  };
  const higherIdPool = {
    id: 20,
    name: 'higher-id-pool',
    instanceType: 'm5.2xlarge',
    regionId: 1,
    launchConfig: {
      kubeAssignPolicy: {securityContext: {privileged: true}}
    }
  };

  it('gates on the explicitly chosen pool\'s own config, not the lowest id ' +
    'sharing its type and region', async () => {
    stubApi({'/cluster/pool/filter': [lowerIdPool, higherIdPool]});

    const config = await getReservationParametersConfig(
      'm5.2xlarge',
      {regionId: 1, poolId: higherIdPool.id}
    );

    expect(isEnabledByReservationConfig(privilegedCapability, config)).toBe(true);
  });

  it('gates on the other explicitly chosen pool just as precisely', async () => {
    stubApi({'/cluster/pool/filter': [lowerIdPool, higherIdPool]});

    const config = await getReservationParametersConfig(
      'm5.2xlarge',
      {regionId: 1, poolId: lowerIdPool.id}
    );

    expect(isEnabledByReservationConfig(privilegedCapability, config)).toBe(false);
  });

  it('falls back to the lowest-id pool when no poolId is given, as before', async () => {
    stubApi({'/cluster/pool/filter': [higherIdPool, lowerIdPool]});

    const config = await getReservationParametersConfig('m5.2xlarge', {regionId: 1});

    expect(isEnabledByReservationConfig(privilegedCapability, config)).toBe(false);
  });
});
