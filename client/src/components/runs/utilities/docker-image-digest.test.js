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

import {pipelineRun} from '@test';
import {hasDockerImageDigest} from './docker-image-digest';

const DIGEST = 'sha256:6c3c624b58dbbcd3c0dd82b4c53f04194d1247c6eebdaab7c610cf7d66709b3b';

it('is true for a run pinned to a digest', () => {
  expect(hasDockerImageDigest(pipelineRun({actualDockerImageDigest: DIGEST}))).toBe(true);
});

it('is false for a run launched with the image tag', () => {
  expect(hasDockerImageDigest(pipelineRun())).toBe(false);
  expect(hasDockerImageDigest(pipelineRun({actualDockerImageDigest: ''}))).toBe(false);
  expect(hasDockerImageDigest(undefined)).toBe(false);
});
