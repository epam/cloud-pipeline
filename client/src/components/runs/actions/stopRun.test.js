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

import {pipelineRun, stubApi} from '@test';
import preferences from '../../../models/preferences/PreferencesLoad';
import {canCommitRun, canPauseRun, checkCommitAllowedForTool} from './stopRun';

const unavailableNodeTags = {NODE_UNAVAILABLE: 'true'};

// Both checks read the `ui.run.actions` preference from the preferences
// singleton, so it is loaded (empty) before each test.
beforeEach(async () => {
  stubApi({'/preferences': []});
  preferences.invalidateCache();
  await preferences.fetch();
});

describe('canPauseRun', () => {
  it('allows to pause a running on-demand run', () => {
    expect(canPauseRun(pipelineRun(), preferences)).toBeTruthy();
  });

  it('does not allow to pause a run whose node is unavailable', () => {
    const run = pipelineRun({tags: unavailableNodeTags});

    expect(canPauseRun(run, preferences)).toBeFalsy();
  });
});

describe('canCommitRun', () => {
  it('allows to commit a running run', () => {
    expect(canCommitRun(pipelineRun())).toBeTruthy();
  });

  it('does not allow to commit a run whose node is unavailable', () => {
    expect(canCommitRun(pipelineRun({tags: unavailableNodeTags}))).toBeFalsy();
  });
});

describe('checkCommitAllowedForTool', () => {
  const dockerRegistries = (tool) => ({
    fetchIfNeededOrWait: () => Promise.resolve(),
    value: {
      registries: [{
        path: 'registry',
        groups: [{name: 'library', tools: [tool]}]
      }]
    }
  });

  // the latest version allows a commit, the "1.2" one does not
  beforeEach(() => {
    stubApi({
      '/tool/1/settings?version=latest': [{version: 'latest', allowCommit: true}],
      '/tool/1/settings?version=1.2': [{version: '1.2', allowCommit: false}]
    });
  });

  it('checks the default version of a tool for an image without a tag', async () => {
    const tool = {id: 1, image: 'library/tool', defaultVersion: '1.2'};

    expect(await checkCommitAllowedForTool('registry/library/tool', dockerRegistries(tool)))
      .toBe(false);
  });

  it('checks the latest version of a tool without a default version', async () => {
    const tool = {id: 1, image: 'library/tool'};

    expect(await checkCommitAllowedForTool('registry/library/tool', dockerRegistries(tool)))
      .toBe(true);
  });

  it('checks the version of an image with a tag', async () => {
    const tool = {id: 1, image: 'library/tool', defaultVersion: '1.2'};

    expect(await checkCommitAllowedForTool('registry/library/tool:latest', dockerRegistries(tool)))
      .toBe(true);
  });
});
