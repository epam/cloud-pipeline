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

const versionSettings = (version, instanceType) => ({
  version,
  settings: [{configuration: {instance_size: instanceType}}]
});

const settings = [
  versionSettings('latest', 'latest.type'),
  versionSettings('1.2', 'default.type')
];

const tool = (defaultVersion) => ({
  registry: 'registry',
  image: 'library/tool',
  instanceType: 'tool.type',
  defaultVersion
});

describe('getToolLaunchPayload', () => {
  it('launches the default version, if a version is not specified', () => {
    const payload = getToolLaunchPayload({tool: tool('1.2'), settings});

    expect(payload.docker_image).toBe('registry/library/tool:1.2');
    expect(payload.instance_size).toBe('default.type');
  });

  it('launches the latest version of a tool without a default version', () => {
    const payload = getToolLaunchPayload({tool: tool(), settings});

    expect(payload.docker_image).toBe('registry/library/tool:latest');
    expect(payload.instance_size).toBe('latest.type');
  });

  it('uses the default version settings for a version without own settings', () => {
    const payload = getToolLaunchPayload({tool: tool('1.2'), settings, toolVersion: '1.0'});

    expect(payload.docker_image).toBe('registry/library/tool:1.0');
    expect(payload.instance_size).toBe('default.type');
  });

  it('uses the settings of the specified version', () => {
    const payload = getToolLaunchPayload({tool: tool('1.2'), settings, toolVersion: 'latest'});

    expect(payload.docker_image).toBe('registry/library/tool:latest');
    expect(payload.instance_size).toBe('latest.type');
  });
});
