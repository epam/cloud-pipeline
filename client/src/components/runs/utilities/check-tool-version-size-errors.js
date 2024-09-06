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

import LoadToolVersionSettings from '../../../models/tools/LoadToolVersionSettings';

export default async function checkToolVersionSizeErrors (
  docker,
  preferences,
  dockerRegistries
) {
  const getTool = (dockerImage) => {
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
  const getToolVersionSettings = async (docker) => {
    const [r, g, iv] = docker.split('/');
    const [i, version] = iv.split(':');
    const dockerImage = [r, g, i].join('/');
    const tool = await getTool(dockerImage);
    if (!tool) {
      return;
    }
    const toolSettings = new LoadToolVersionSettings(tool.id);
    await toolSettings.fetch();
    return (toolSettings.value || []).find(v => v.version === version);
  };
  await preferences?.fetchIfNeededOrWait();
  await dockerRegistries?.fetchIfNeededOrWait();
  const versionSettings = await getToolVersionSettings(docker);
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
};
