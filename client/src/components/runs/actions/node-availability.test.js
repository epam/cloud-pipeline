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
import runNodeIsUnavailable from './node-availability';

describe('runNodeIsUnavailable', () => {
  it('is true when the run has the NODE_UNAVAILABLE tag the API sets', () => {
    expect(runNodeIsUnavailable(pipelineRun({tags: {NODE_UNAVAILABLE: 'true'}}))).toBe(true);
  });

  it('matches the tag name in any case', () => {
    expect(runNodeIsUnavailable(pipelineRun({tags: {node_unavailable: 'true'}}))).toBe(true);
  });

  it('is false when the tag value is "false", as the run tag chip is hidden then', () => {
    expect(runNodeIsUnavailable(pipelineRun({tags: {NODE_UNAVAILABLE: 'false'}}))).toBe(false);
  });

  it('is false when the run has other tags only', () => {
    const run = pipelineRun({
      tags: {IDLE: 'true', NODE_UNAVAILABLE_date: '2026-10-14 10:00:00.000'}
    });

    expect(runNodeIsUnavailable(run)).toBe(false);
  });

  it('is false when the run has no tags', () => {
    expect(runNodeIsUnavailable(pipelineRun({tags: undefined}))).toBe(false);
  });

  it('is false when there is no run', () => {
    expect(runNodeIsUnavailable(undefined)).toBe(false);
  });
});
