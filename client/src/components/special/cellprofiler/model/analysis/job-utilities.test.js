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
import {getDockerImageInfo} from './job-utilities';

const settings = (version, instanceType) => ({
  version,
  settings: [{configuration: {instance_size: instanceType}}]
});

function stubTool (tool) {
  stubApi({
    '/tool/load?image=library/tool&registry=registry': tool,
    '/tool/1/settings?version=latest': [settings('latest', 'latest.type')],
    '/tool/1/settings?version=1.2': [settings('1.2', 'default.type')]
  });
}

describe('getDockerImageInfo', () => {
  it('uses the default version of a tool for an image without a tag', async () => {
    stubTool({id: 1, registry: 'registry', image: 'library/tool', defaultVersion: '1.2'});

    const info = await getDockerImageInfo('registry/library/tool');

    expect(info.dockerImage).toBe('registry/library/tool:1.2');
    expect(info.settings).toEqual({instance_size: 'default.type'});
  });

  it('uses the latest version of a tool without a default version', async () => {
    stubTool({id: 1, registry: 'registry', image: 'library/tool'});

    const info = await getDockerImageInfo('registry/library/tool');

    expect(info.dockerImage).toBe('registry/library/tool:latest');
    expect(info.settings).toEqual({instance_size: 'latest.type'});
  });

  it('uses the version of an image with a tag', async () => {
    stubTool({id: 1, registry: 'registry', image: 'library/tool', defaultVersion: '1.2'});

    const info = await getDockerImageInfo('registry/library/tool:latest');

    expect(info.dockerImage).toBe('registry/library/tool:latest');
  });
});
