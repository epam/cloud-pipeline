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
import getToolLaunchingOptions from './get-tool-launching-options';

const LATEST_DISK = 10;
const DEFAULT_DISK = 20;

const settings = (version, disk) => ({
  version,
  settings: [{default: true, configuration: {instance_disk: disk}}]
});

const stores = {
  awsRegions: {
    fetchIfNeededOrWait: () => Promise.resolve(),
    loaded: true,
    value: [{id: 1, default: true}]
  },
  preferences: {
    fetchIfNeededOrWait: () => Promise.resolve(),
    loaded: true,
    useSpot: false,
    getPreferenceValue: () => undefined
  }
};

const tool = (defaultVersion) => ({
  id: 1,
  registry: 'registry',
  image: 'library/tool',
  defaultVersion
});

beforeEach(() => {
  stubApi({
    '/tool/1/settings': [settings('latest', LATEST_DISK), settings('1.2', DEFAULT_DISK)]
  }).reply(/\/cluster\/instance\/allowed/, {});
});

describe('getToolLaunchingOptions', () => {
  it('launches the default version, if a version is not specified', async () => {
    const payload = await getToolLaunchingOptions(stores, tool('1.2'));

    expect(payload.dockerImage).toBe('registry/library/tool:1.2');
    expect(payload.hddSize).toBe(DEFAULT_DISK);
  });

  it('launches the latest version of a tool without a default version', async () => {
    const payload = await getToolLaunchingOptions(stores, tool());

    expect(payload.dockerImage).toBe('registry/library/tool:latest');
    expect(payload.hddSize).toBe(LATEST_DISK);
  });

  it('uses the default version settings for a version without own settings', async () => {
    const payload = await getToolLaunchingOptions(stores, tool('1.2'), '1.0');

    expect(payload.dockerImage).toBe('registry/library/tool:1.0');
    expect(payload.hddSize).toBe(DEFAULT_DISK);
  });
});
