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

import {fn} from '@test';
import {getDockerImage, parseDockerImage} from './get-docker-image';

describe('parseDockerImage', () => {
  it('splits a registry/group/tool:version image, lower-cased', () => {
    expect(parseDockerImage(' CP-Docker:443/Library/Ubuntu:18.04 ')).toEqual({
      registry: 'cp-docker:443',
      group: 'library',
      tool: 'ubuntu',
      version: '18.04'
    });
  });

  it('uses the latest version when the image has none', () => {
    expect(parseDockerImage('cp-docker:443/library/ubuntu').version).toBe('latest');
  });

  it('returns undefined for an image in any other format', () => {
    expect(parseDockerImage()).toBeUndefined();
    expect(parseDockerImage('ubuntu:latest')).toBeUndefined();
    expect(parseDockerImage('library/ubuntu:latest')).toBeUndefined();
    expect(parseDockerImage('cp-docker:443/library/sub/ubuntu:latest')).toBeUndefined();
  });
});

describe('getDockerImage', () => {
  const tool = {id: 17, image: 'library/ubuntu'};
  const dockerRegistries = {
    loaded: true,
    value: {
      registries: [{
        path: 'cp-docker:443',
        groups: [{name: 'library', tools: [tool]}]
      }]
    }
  };

  it('finds the tool of an image, case-insensitively', () => {
    expect(getDockerImage('CP-DOCKER:443/library/Ubuntu:18.04', dockerRegistries)).toEqual({
      dockerImage: 'CP-DOCKER:443/library/Ubuntu:18.04',
      registry: dockerRegistries.value.registries[0],
      group: dockerRegistries.value.registries[0].groups[0],
      tool,
      version: '18.04'
    });
  });

  it('returns undefined for an unknown tool or an image in another format', () => {
    expect(getDockerImage('cp-docker:443/library/centos', dockerRegistries)).toBeUndefined();
    expect(getDockerImage('library/ubuntu', dockerRegistries)).toBeUndefined();
  });

  it('requests the registries and returns undefined while they load', () => {
    const fetchIfNeededOrWait = fn();
    expect(getDockerImage('cp-docker:443/library/ubuntu', {loaded: false, fetchIfNeededOrWait}))
      .toBeUndefined();
    expect(fetchIfNeededOrWait).toHaveBeenCalled();
  });
});
