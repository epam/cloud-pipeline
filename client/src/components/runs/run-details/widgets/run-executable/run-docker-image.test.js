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

import React from 'react';
import {hover, pipelineRun, renderWithStores, screen} from '@test';
import RunDockerImage from './run-docker-image';

const DOCKER_IMAGE = 'registry:443/library/centos:7';
const DIGEST = 'sha256:6c3c624b58dbbcd3c0dd82b4c53f04194d1247c6eebdaab7c610cf7d66709b3b';

// no registry is known, so the image is shown by its name, without a tool link
const dockerRegistries = {loaded: true, value: {registries: []}};

async function hoverDockerImage (run) {
  renderWithStores(<RunDockerImage run={run} />, {dockerRegistries});
  await hover(await screen.findByText('centos:7'));
}

it('shows the image digest beside the image name of a pinned run', async () => {
  await hoverDockerImage(pipelineRun({dockerImage: DOCKER_IMAGE, actualDockerImageDigest: DIGEST}));

  expect(await screen.findByText(DOCKER_IMAGE)).toBeInTheDocument();
  expect(await screen.findByText(DIGEST)).toBeInTheDocument();
});

it('shows only the image name of a run launched with the image tag', async () => {
  await hoverDockerImage(pipelineRun({dockerImage: DOCKER_IMAGE}));

  expect(await screen.findByText(DOCKER_IMAGE)).toBeInTheDocument();
  expect(screen.queryByText(/^sha256:/)).not.toBeInTheDocument();
});
