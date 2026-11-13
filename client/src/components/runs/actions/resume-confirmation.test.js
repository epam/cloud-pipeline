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

import {screen, click, waitFor, stubApi} from '@test';
import capacityBlocks from '../../../models/cluster/capacity-blocks';
import confirmResume from './resume-confirmation';

const run = {
  id: 1,
  pipelineId: 100,
  instance: {
    cloudRegionId: 1,
    spot: false,
    nodeType: 'm5.2xlarge'
  }
};

const instanceTypes = [
  {name: 'm5.2xlarge', instanceFamily: 'General purpose', vcpu: 8, memory: 32, regionId: 1}
];

const pool = {id: 351, instanceType: 'm5.2xlarge', name: 'pool-351', regionId: 1};

beforeEach(() => {
  capacityBlocks.invalidateCache();
  stubApi({
    '/cluster/instance/allowed?regionId=1&spot=false': {
      'cluster.allowed.instance.types': instanceTypes,
      'cluster.allowed.price.types': ['on_demand']
    },
    '/cluster/pool/filter': [pool],
    '/preferences': []
  });
});

describe('resume-confirmation, capacity blocks', () => {
  it('offers no capacity-block group and no pool tag: a resume is never reserved', async () => {
    // Pre-load the pools, the way `index.test.js` does, so a group or a tag
    // would already be there on the dialog's first render if one were shown.
    await capacityBlocks.load();

    const resultPromise = confirmResume({id: run.id, run});

    await waitFor(() => expect(
      screen.getByRole('button', {name: /RESUME/})
    ).not.toBeDisabled());
    await click(screen.getByRole('combobox'));

    // A resumed run has no field for a pool, so no pool is offered, and the
    // plain type carries no tag: picking it launches without a reservation.
    expect(screen.queryByText('Reserved capacity blocks')).toBeNull();
    expect(screen.queryByText('pool-351')).toBeNull();
    await click(screen.getByRole('menuitem', {name: /m5\.2xlarge/}));
    await click(screen.getByText('RESUME'));

    const result = await resultPromise;
    expect(result.instanceType).toBe('m5.2xlarge');
  });
});
