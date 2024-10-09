/*
 * Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import LoadToolInfo from '../../../models/tools/LoadToolInfo';
import LoadToolVersionSettings from '../../../models/tools/LoadToolVersionSettings';

function getTool (dockerImage, dockerRegistries) {
  if (!dockerImage) {
    return Promise.resolve(null);
  } else {
    return new Promise((resolve) => {
      dockerRegistries.fetchIfNeededOrWait()
        .then(() => {
          const {registries = []} = dockerRegistries.value;
          for (let r = 0; r < registries.length; r++) {
            const {groups = []} = registries[r];
            for (let g = 0; g < groups.length; g++) {
              const {tools = []} = groups[g];
              for (let t = 0; t < tools.length; t++) {
                const image = `${registries[r].path}/${tools[t].image}`.toLowerCase();
                if (dockerImage.toLowerCase() === image) {
                  resolve(tools[t]);
                  return;
                }
              }
            }
          }
          resolve(null);
        })
        .catch(() => resolve(null));
    });
  }
};

async function getToolVersion (tool, version) {
  const toolInfo = new LoadToolInfo(tool.id);
  await toolInfo.fetch();
  const {versions = []} = toolInfo?.value || {};
  return versions.find(v => v.version === version);
}

async function getToolVersionSettings (tool, version) {
  const toolSettings = new LoadToolVersionSettings(tool.id);
  await toolSettings.fetch();
  return (toolSettings.value || []).find(v => v.version === version);
};

function getToolSizeErrors (versionSettings, preferences) {
  if (versionSettings) {
    const {size} = versionSettings;
    const {soft = 0, hard = 0} = preferences.launchToolSizeLimits || {};
    return {
      soft: soft ? size > soft : false,
      hard: hard ? size > hard : false
    };
  }
  return {
    soft: undefined,
    hard: undefined
  };
}

function getToolAllowedWarning (currentVersion, preferences) {
  if (!currentVersion?.scanResult?.toolOSVersion) {
    return;
  }
  const replacePlaceholders = (string = '') => {
    let result = string;
    const {distribution, version} = currentVersion.scanResult.toolOSVersion;
    if (/{os}/i.test(result)) {
      result = result.replace(/{os}/ig, `${distribution} ${version}`);
    }
    if (/{distribution}/i.test(result)) {
      result = result.replace(/{distribution}/ig, `${distribution}`);
    }
    if (/{version}/i.test(result)) {
      result = result.replace(/{version}/ig, `${version}`);
    }
    return result;
  };
  const {isAllowedWarning} = currentVersion.scanResult.toolOSVersion;
  return isAllowedWarning && !!preferences.toolOSWarningText
    ? replacePlaceholders(preferences.toolOSWarningText)
    : undefined;
}

export default async function checkToolVersionErrors (
  docker = '',
  preferences,
  dockerRegistries
) {
  let warnings = {
    size: {
      soft: undefined,
      hard: undefined
    },
    allowedWarning: undefined
  };
  if (!docker) {
    return warnings;
  }
  const [r, g, iv] = docker.split('/');
  const [i, version] = iv.split(':');
  const dockerImage = [r, g, i].join('/');
  const tool = await getTool(dockerImage, dockerRegistries);
  if (!tool) {
    return warnings;
  }
  await Promise.all([
    preferences?.fetchIfNeededOrWait(),
    dockerRegistries?.fetchIfNeededOrWait()
  ]);
  const [currentVersion, versionSettings] = await Promise.all([
    getToolVersion(tool, version),
    getToolVersionSettings(tool, version)
  ]);
  const allowedWarning = getToolAllowedWarning(currentVersion, preferences);
  const size = getToolSizeErrors(versionSettings, preferences);
  if (allowedWarning || size) {
    warnings = {
      ...warnings,
      ...(size ? {size} : {}),
      ...(allowedWarning ? {allowedWarning} : {})
    };
  }
  return warnings;
};
