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

import {getToolLaunchPayload} from './payload-utilities';

const tool = {
  registry: 'registry.example.com:443',
  image: 'library/my-tool'
};

const versionSettings = (version, configuration) => ({
  version,
  settings: [{configuration}]
});

describe('getToolLaunchPayload', () => {
  it('keeps the case of the version tag in the docker image', () => {
    const payload = getToolLaunchPayload({tool, toolVersion: '2023R1'});
    expect(payload.docker_image).toBe('registry.example.com:443/library/my-tool:2023R1');
  });

  it('uses the latest tag when no version is given', () => {
    const payload = getToolLaunchPayload({tool});
    expect(payload.docker_image).toBe('registry.example.com:443/library/my-tool:latest');
  });

  it('matches the version settings regardless of the case', () => {
    const settings = [
      versionSettings('latest', {instance_size: 'm5.xlarge'}),
      versionSettings('2023r1', {instance_size: 'c5.4xlarge'})
    ];
    const payload = getToolLaunchPayload({tool, settings, toolVersion: '2023R1'});
    expect(payload.instance_size).toBe('c5.4xlarge');
    expect(payload.docker_image).toBe('registry.example.com:443/library/my-tool:2023R1');
  });
});
