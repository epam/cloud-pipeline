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

import getVersionRunningInfo from './getVersionInfo';

const scanPolicy = {
  maxCriticalVulnerabilities: 0,
  maxHighVulnerabilities: 0,
  maxMediumVulnerabilities: 0
};

const preferences = {
  toolScanningEnabledForRegistry: () => true
};

const blackListed = {scanResult: {fromBlackList: true}};

const versions = {
  latest: blackListed,
  '1.2': blackListed,
  '1.1': blackListed
};

const tooltipFor = (version, tool) => getVersionRunningInfo(
  version,
  versions,
  scanPolicy,
  false,
  preferences,
  {},
  tool
).tooltip;

describe('getVersionRunningInfo', () => {
  it('names the default version set for a tool so', () => {
    expect(tooltipFor('1.2', {defaultVersion: '1.2'}))
      .toBe('The default version is on the black list. Try to find another one.');
  });

  it('keeps naming the latest version so, while a default version is not set', () => {
    expect(tooltipFor('latest', {}))
      .toBe('The latest version is on the black list. Try to find an older one.');
    expect(tooltipFor('latest', undefined))
      .toBe('The latest version is on the black list. Try to find an older one.');
  });

  it('names no other version', () => {
    expect(tooltipFor('latest', {defaultVersion: '1.2'}))
      .toBe('The version is on the black list. Try to find another one.');
    expect(tooltipFor('1.1', {}))
      .toBe('The version is on the black list. Try to find another one.');
  });
});
