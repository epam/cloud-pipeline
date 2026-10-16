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

import {
  getTaglessImageVersion,
  getToolDefaultVersion,
  isCustomDefaultVersion,
  LATEST_VERSION
} from './tool-default-version';

const toolWithDefault = {defaultVersion: '1.2'};
const toolWithoutDefault = {};

describe('getTaglessImageVersion', () => {
  it('is the latest version for a tool without a default version', () => {
    expect(getTaglessImageVersion(toolWithoutDefault)).toBe(LATEST_VERSION);
    expect(getTaglessImageVersion(undefined)).toBe(LATEST_VERSION);
  });

  it('is the default version, if it is set', () => {
    expect(getTaglessImageVersion(toolWithDefault)).toBe('1.2');
  });
});

describe('getToolDefaultVersion', () => {
  it('prefers the default version', () => {
    expect(getToolDefaultVersion(toolWithDefault, ['latest', '1.2', '1.1'])).toBe('1.2');
  });

  it('falls back to the latest version, if the default one does not exist', () => {
    expect(getToolDefaultVersion(toolWithDefault, ['latest', '1.1'])).toBe(LATEST_VERSION);
  });

  it('uses the latest version for a tool without a default version', () => {
    expect(getToolDefaultVersion(toolWithoutDefault, ['1.1', 'latest'])).toBe(LATEST_VERSION);
  });

  it('uses the only version, if there is no latest one', () => {
    expect(getToolDefaultVersion(toolWithoutDefault, ['1.1'])).toBe('1.1');
    expect(getToolDefaultVersion(toolWithDefault, ['1.1'])).toBe('1.1');
  });

  it('has no version to use among several ones without the latest one', () => {
    expect(getToolDefaultVersion(toolWithoutDefault, ['1.1', '1.0'])).toBeUndefined();
    expect(getToolDefaultVersion(toolWithoutDefault, [])).toBeUndefined();
    expect(getToolDefaultVersion(undefined, undefined)).toBeUndefined();
  });
});

describe('isCustomDefaultVersion', () => {
  it('is true for an existing default version', () => {
    expect(isCustomDefaultVersion(toolWithDefault, ['latest', '1.2'])).toBe(true);
  });

  it('is false if the default version is not set or does not exist', () => {
    expect(isCustomDefaultVersion(toolWithoutDefault, ['latest', '1.2'])).toBe(false);
    expect(isCustomDefaultVersion(toolWithDefault, ['latest'])).toBe(false);
  });
});
