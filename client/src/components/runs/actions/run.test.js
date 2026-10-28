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
import {renderWithStores, screen, stubApi, flushPromises} from '@test';
import capacityBlocks from '../../../models/cluster/capacity-blocks';
import {run, RunConfirmation, RunSpotConfirmationWithPrice} from './run';

const image = 'registry/library/ubuntu:latest';

function launchedBodies (api) {
  return api.calls
    .filter(call => call.method === 'POST' && call.path === '/run')
    .map(call => JSON.parse(call.body));
}

// The store is a module-level singleton (like `models/preferences/PreferencesLoad`
// elsewhere), so every test starts it off the same way: as if nothing had been
// fetched yet.
beforeEach(() => {
  capacityBlocks.invalidateCache();
});

describe('run without confirmation', () => {
  it('sends every payload with its run name as the alias tag, keeping its tags', async () => {
    const api = stubApi({'/run': {id: 1}});
    const payloads = [
      {dockerImage: image, instanceType: 'm5.large', tags: {owner: 'me'}, runNameAlias: 'first'},
      {dockerImage: image, instanceType: 'm5.large', tags: {team: 'core'}, runNameAlias: 'second'}
    ];

    await run({props: {}})(payloads, false);

    const bodies = launchedBodies(api);
    expect(bodies).toHaveLength(2);
    expect(bodies[0].tags).toEqual({owner: 'me', alias: 'first'});
    expect(bodies[1].tags).toEqual({team: 'core', alias: 'second'});
    bodies.forEach(body => expect('runNameAlias' in body).toBe(false));
  });

  it('leaves the tags as they were when no run name is set', async () => {
    const api = stubApi({'/run': {id: 1}});
    const payload = {dockerImage: image, instanceType: 'm5.large', tags: {owner: 'me'}};

    await run({props: {}})(payload, false);

    const [body] = launchedBodies(api);
    expect(body.tags).toEqual({owner: 'me'});
  });
});

describe('a capacity-block pool chosen before launch', () => {
  // Two pools backing the same instance type and region - the case the whole
  // "Reserved capacity blocks" group exists for: the user chose one specific
  // pool, not "an m5.2xlarge with whichever pool happens to match first".
  // `generateLaunchPayload` (`LaunchPipelineForm.js`) sets `capacityBlockPoolId`
  // on the payload exactly like this once the user has picked one.
  const lowerIdPool = {
    id: 10,
    name: 'lower-id-pool',
    instanceType: 'm5.2xlarge',
    regionId: 1,
    launchConfig: {
      cpuRequestsEnabled: true,
      cpuRequestsReserved: 1,
      additionalParameters: {CP_CAP_LOWER: 'true'}
    }
  };
  const higherIdPool = {
    id: 20,
    name: 'higher-id-pool',
    instanceType: 'm5.2xlarge',
    regionId: 1,
    launchConfig: {
      gpuRequestsEnabled: true,
      gpuRequestsReserved: 0,
      additionalParameters: {CP_CAP_HIGHER: 'true'}
    }
  };

  it('launches with the explicitly chosen pool\'s own config, not the lowest ' +
    'id sharing its type and region, and never sends capacityBlockPoolId itself', async () => {
    const api = stubApi({'/run': {id: 1}, '/cluster/pool/filter': [lowerIdPool, higherIdPool]});
    const payload = {
      dockerImage: image,
      instanceType: 'm5.2xlarge',
      cloudRegionId: 1,
      capacityBlockPoolId: higherIdPool.id
    };

    await run({props: {}})(payload, false);

    const [body] = launchedBodies(api);
    expect(body.params.CP_CAP_HIGHER).toEqual({value: 'true'});
    expect(body.params.CP_CAP_LOWER).toBeUndefined();
    expect('capacityBlockPoolId' in body).toBe(false);
  });

  it('launches a plain instance type a pool backs with no reservation, and strips ' +
    'the reservation parameters a relaunch carries over', async () => {
    const api = stubApi({'/run': {id: 1}, '/cluster/pool/filter': [lowerIdPool, higherIdPool]});
    const payload = {
      dockerImage: image,
      instanceType: 'm5.2xlarge',
      cloudRegionId: 1,
      params: {
        CP_CAP_REQUESTS_CPU: {value: '1'},
        CP_CAP_LOWER: {value: 'true'},
        SOME_OTHER_PARAMETER: {value: 'kept'}
      }
    };

    await run({props: {}})(payload, false);

    const [body] = launchedBodies(api);
    expect(body.params.CP_CAP_REQUESTS_CPU).toBeUndefined();
    expect(body.params.CP_CAP_LOWER).toBeUndefined();
    expect(body.params.SOME_OTHER_PARAMETER).toEqual({value: 'kept'});
    expect(body.podAssignPolicy).toBeUndefined();
  });
});

describe('RunConfirmation estimated price', () => {
  const pool = {id: 30, name: 'pool-30', instanceType: 'm5.2xlarge', regionId: 1};
  const instanceTypes = [
    {name: 'm5.2xlarge', instanceFamily: 'General purpose', vcpu: 8, memory: 32, regionId: 1}
  ];
  const loaded = (value) => observable({
    loaded: true,
    pending: false,
    value,
    fetchIfNeededOrWait: () => Promise.resolve()
  });

  async function renderDialog (element) {
    stubApi({
      '/cluster/pool/filter': [pool],
      '/pipeline/price': {pricePerHour: 1, minimumTimePrice: 1, maximumTimePrice: 1},
      '/dockerRegistry/loadTree': {registries: []}
    });
    await capacityBlocks.load();
    const stores = {
      preferences: loaded([]),
      dockerRegistries: loaded({registries: []}),
      usersInfo: loaded([]),
      authenticatedUserInfo: loaded({admin: true})
    };
    renderWithStores(React.cloneElement(element, stores), stores);
    await flushPromises();
  }

  const confirmation = (instanceType) => (
    <RunConfirmation
      instanceType={instanceType}
      instanceTypes={instanceTypes}
      cloudRegionId={1}
      dockerImage={image}
      showInstanceTypeSelection={false}
    />
  );

  it('is shown for a plain instance type, even where a pool backs it', async () => {
    await renderDialog(confirmation('m5.2xlarge'));

    expect(await screen.findByText(/Estimated price:/)).toBeInTheDocument();
  });

  it('is hidden for a pool picked from "Reserved capacity blocks"', async () => {
    await renderDialog(confirmation('pool:30'));

    expect(screen.queryByText(/Estimated price/)).toBeNull();
  });

  it('is hidden when the launch form passes the chosen pool as capacityBlockPoolId', async () => {
    // The launch form puts only the plain type into the payload, and the
    // chosen pool into `capacityBlockPoolId`.
    await renderDialog(
      <RunSpotConfirmationWithPrice
        instanceType="m5.2xlarge"
        capacityBlockPoolId={pool.id}
        availableInstanceTypes={instanceTypes}
        cloudRegionId={1}
        dockerImage={image}
        runInfo={{name: 'ubuntu', payload: {dockerImage: image, instanceType: 'm5.2xlarge'}}}
      />
    );

    expect(screen.queryByText(/Estimated price/)).toBeNull();
  });
});
