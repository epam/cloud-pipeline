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
import preferences from '../../../models/preferences/PreferencesLoad';
import {canCommitRun, canPauseRun} from './stopRun';

const unavailableNodeTags = {NODE_UNAVAILABLE: 'true'};

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
