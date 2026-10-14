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

import getDockerImageVersion from './get-docker-image-version';

describe('getDockerImageVersion', () => {
  it('keeps the case of the version', () => {
    expect(getDockerImageVersion('registry.example.com:443/library/my-tool:2023R1'))
      .toBe('2023R1');
  });

  it('is not confused by the registry port', () => {
    expect(getDockerImageVersion('registry.example.com:443/library/my-tool')).toBeUndefined();
  });

  it('gives nothing for an empty image', () => {
    expect(getDockerImageVersion(undefined)).toBeUndefined();
  });
});
