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
import {renderWithStores, stubApi, waitFor} from '@test';
import LaunchCommand from './launch-command';

const preferences = {
  fetchIfNeededOrWait: () => Promise.resolve(),
  getPreferenceValue: () => undefined
};

describe('LaunchCommand request', () => {
  it('does not send the launch form\'s capacityBlockPoolId to the API', async () => {
    const api = stubApi({'/run/cmd': 'pipe run'});
    const payload = {
      instanceType: 'm5.large',
      capacityBlockPoolId: 30,
      podAssignPolicy: {selector: {label: 'pool_id', value: '30'}},
      params: {}
    };

    renderWithStores(<LaunchCommand payload={payload} />, {preferences});

    await waitFor(() => expect(api.calls).toHaveLength(1));
    const {pipelineStart} = JSON.parse(api.calls[0].body);
    expect(pipelineStart).not.toHaveProperty('capacityBlockPoolId');
    expect(pipelineStart.instanceType).toBe('m5.large');
    expect(pipelineStart.podAssignPolicy).toEqual(payload.podAssignPolicy);
  });
});
