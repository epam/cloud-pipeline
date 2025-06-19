/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

export const LAUNCH_MODES = {
  pipeline: 'pipeline',
  tool: 'tool'
};

export function getToolConfiguration (configurationName = '', toolVersion) {
  if (!toolVersion) {
    return undefined;
  }
  const currentConfiguration = toolVersion.settings
    .find(({name}) => name === configurationName);
  const defaultConfiguration = toolVersion.settings
    .find(({name}) => name === 'default');
  return currentConfiguration || defaultConfiguration;
}

export function getLaunchMode (data = {}) {
  if (data.pipelineId !== undefined || data.pipelineName) {
    return LAUNCH_MODES.pipeline;
  }
  return LAUNCH_MODES.tool;
}
