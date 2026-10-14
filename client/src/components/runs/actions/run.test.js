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
import {run} from './run';

const image = 'registry/library/ubuntu:latest';

function launchedBodies (api) {
  return api.calls
    .filter(call => call.method === 'POST' && call.path === '/run')
    .map(call => JSON.parse(call.body));
}

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
