/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

export const TOP_USED_FILTER = {
  id: 'top-used',
  name: 'Top used tools',
  title: 'Top used tools',
  description: 'Top 5 tools by usage frequency'
};

export const DEFAULT_FILTER = {
  groups: [TOP_USED_FILTER],
  showTopUsed: true
};

function isPersonalGroup (group) {
  const {
    name = '',
    owner: ownerName = ''
  } = group || {};
  const owner = (ownerName || '')
    .replace(/[^a-zA-Z0-9-]/g, '-')
    .toLowerCase();
  return name.toLowerCase() === owner;
}

function validateOS (tool = {}, mask) {
  if (/^all$/i.test(mask)) {
    return true;
  }
  if (!tool.toolOSVersion) {
    return false;
  }
  const {distribution, version} = tool.toolOSVersion;
  const os = `${distribution} ${version}`.trim();
  const osMasks = mask
    .replace(/\./g, '\\.')
    .replace(/\*/g, '.*')
    .split(',')
    .filter(Boolean);
  return osMasks.some(mask => new RegExp(`^${mask.trim()}$`, 'i').test(os));
}

export default function extractTools (groups = [], currentFilter) {
  if (!currentFilter) {
    return [];
  }
  const {
    my,
    personal,
    sensitive,
    labels,
    os,
    interactive,
    gpu,
    shared
  } = currentFilter.filters || {};
  const tools = (groups || [])
    .reduce((acc, current) => ([
      ...acc,
      ...(current.tools || []).map((aTool) => ({
        ...aTool,
        personal: isPersonalGroup(current),
        my: current.privateGroup,
        interactive: aTool.endpoints && aTool.endpoints.length > 0,
        shared: isPersonalGroup(current) && !current.privateGroup
      }))
    ]), []);
  const checkBooleanProperty = (tool, property, criteria) => typeof criteria === 'boolean'
    ? !!(tool[property]) === criteria
    : true;
  const checkSensitive = (tool) => checkBooleanProperty(tool, 'allowSensitive', sensitive);
  const checkInteractive = (tool) => checkBooleanProperty(tool, 'interactive', interactive);
  const checkGPUEnabled = (tool) => checkBooleanProperty(tool, 'gpuEnabled', gpu);
  const checkOS = (tool) => typeof os === 'string' && os.length > 0 ? validateOS(tool, os) : true;
  const checkMy = (tool) => checkBooleanProperty(tool, 'my', my);
  const checkPersonal = (tool) => checkBooleanProperty(tool, 'personal', personal);
  const checkShared = (tool) => checkBooleanProperty(tool, 'shared', shared);
  const checkLabels = (tool) => labels && labels.length > 0
    ? (tool.labels || []).some(label => labels.includes(label))
    : true;
  return tools.filter((tool) =>
    checkSensitive(tool) &&
    checkInteractive(tool) &&
    checkGPUEnabled(tool) &&
    checkOS(tool) &&
    checkMy(tool) &&
    checkPersonal(tool) &&
    checkShared(tool) &&
    checkLabels(tool)
  );
};
