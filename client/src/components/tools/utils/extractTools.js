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
    os,
    interactive,
    gpu
  } = currentFilter.filters || {};
  let tools = [];
  if (my) {
    const myGroup = groups.filter(g => g.privateGroup)[0];
    tools = (myGroup || {}).tools || [];
  } else if (personal) {
    tools = groups
      .filter(group => isPersonalGroup(group))
      .reduce((acc, group) => [...acc, ...group.tools], []);
  } else {
    tools = (groups || [])
      .reduce((acc, current) => {
        acc.push(...(current.tools || []));
        return acc;
      }, []);
  }
  tools = tools.filter(tool => {
    return (sensitive === undefined || sensitive === null
      ? true
      : tool.allowSensitive === sensitive
    ) && (interactive === undefined || sensitive === null
      ? true
      : tool.endpoints && tool.endpoints.length > 0
    ) && (gpu === undefined || gpu === null
      ? true
      : tool.gpuEnabled === gpu
    ) && (os === undefined || os === null
      ? true
      : validateOS(tool, os)
    );
  });
  return tools;
};
