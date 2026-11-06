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

import {getDockerImage} from './get-docker-image';

const dockerRegistries = (tool) => ({
  loaded: true,
  value: {
    registries: [{
      path: 'registry',
      groups: [{name: 'library', tools: [tool]}]
    }]
  }
});

describe('getDockerImage', () => {
  it('takes the version from the image tag', () => {
    const tool = {image: 'library/tool', defaultVersion: '1.2'};

    expect(getDockerImage('registry/library/tool:1.0', dockerRegistries(tool)).version).toBe('1.0');
  });

  it('takes the default version of a tool for an image without a tag', () => {
    const tool = {image: 'library/tool', defaultVersion: '1.2'};

    expect(getDockerImage('registry/library/tool', dockerRegistries(tool)).version).toBe('1.2');
  });

  it('takes the latest version of a tool without a default version, if there is no tag', () => {
    const tool = {image: 'library/tool'};

    expect(getDockerImage('registry/library/tool', dockerRegistries(tool)).version).toBe('latest');
  });

  it('finds nothing for an unknown tool', () => {
    const tool = {image: 'library/tool'};

    expect(getDockerImage('registry/library/other', dockerRegistries(tool))).toBeUndefined();
  });
});
