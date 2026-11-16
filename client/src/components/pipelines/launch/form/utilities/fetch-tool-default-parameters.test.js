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
import fetchToolDefaultParameters, {
  getToolVersionParameters
} from './fetch-tool-default-parameters';

const versionSettings = (version, parameters) => ({
  version,
  settings: [{configuration: {parameters}}]
});

const latestParameters = {LATEST: {value: 'latest'}};
const defaultParameters = {DEFAULT: {value: '1.2'}};
const firstParameters = {FIRST: {value: '1.0'}};

describe('getToolVersionParameters', () => {
  const versions = [
    versionSettings('1.0', firstParameters),
    versionSettings('latest', latestParameters),
    versionSettings('1.2', defaultParameters)
  ];

  it('returns the parameters of the requested version', () => {
    expect(getToolVersionParameters(versions, '1.0', '1.2')).toEqual(firstParameters);
  });

  it('falls back to the default version', () => {
    expect(getToolVersionParameters(versions, '2.0', '1.2')).toEqual(defaultParameters);
  });

  it('falls back to the latest version, if no default version is given', () => {
    expect(getToolVersionParameters(versions, '2.0')).toEqual(latestParameters);
  });

  it('falls back to the first version, if the requested and default ones have no settings', () => {
    const withoutDefault = [versionSettings('1.0', firstParameters)];

    expect(getToolVersionParameters(withoutDefault, '2.0', '1.2')).toEqual(firstParameters);
    expect(getToolVersionParameters(withoutDefault, '2.0')).toEqual(firstParameters);
  });

  it('returns nothing for a tool without settings', () => {
    expect(getToolVersionParameters([], '2.0', '1.2')).toEqual({});
  });
});

describe('fetchToolDefaultParameters', () => {
  const tool = {id: 1, image: 'library/tool', defaultVersion: '1.2'};
  const dockerRegistries = {
    loaded: true,
    fetchIfNeededOrWait: () => Promise.resolve(),
    value: {
      registries: [{
        path: 'registry',
        groups: [{name: 'library', tools: [tool]}]
      }]
    }
  };

  beforeEach(() => {
    stubApi({
      '/tool/1/settings': [
        versionSettings('latest', latestParameters),
        versionSettings('1.2', defaultParameters)
      ]
    });
  });

  it('uses the default version of a tool for an image without a tag', async () => {
    expect(await fetchToolDefaultParameters('registry/library/tool', dockerRegistries))
      .toEqual(defaultParameters);
  });

  it('uses the version of an image with a tag', async () => {
    expect(await fetchToolDefaultParameters('registry/library/tool:latest', dockerRegistries))
      .toEqual(latestParameters);
  });
});
