/*
 * Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import LoadToolVersionSettings from '../../../../../models/tools/LoadToolVersionSettings';

function getToolVersionParameters (versions, version) {
  const versionInfo = versions.find(v => v.version === version);
  if (!versionInfo && !/^latest$/i.test(version)) {
    return getToolVersionParameters(versions, 'latest');
  }
  if (!versionInfo && versions.length > 0) {
    return getToolVersionParameters(versions, version[0].version);
  }
  if (!versionInfo) {
    return {};
  }
  const {
    settings = []
  } = versionInfo;
  const [defaultSettings] = settings;
  const {
    configuration = {}
  } = defaultSettings || [];
  return configuration.parameters;
}

export default function fetchToolDefaultParameters (dockerImage, dockerRegistries) {
  if (!dockerImage || !dockerRegistries) {
    return Promise.resolve({});
  }
  return new Promise((resolve) => {
    const [
      registryPath,
      groupName,
      imageAndVersion
    ] = dockerImage.split('/');
    const [image, version] = (imageAndVersion || '').split(':');
    let toolInfoRequest;
    dockerRegistries
      .fetchIfNeededOrWait()
      .then(() => {
        if (dockerRegistries.loaded) {
          const {
            registries = []
          } = dockerRegistries.value || {};
          const registry = registries.find(o => o.path === registryPath);
          if (!registry) {
            throw new Error(`Registry ${registryPath} not found`);
          }
          const {
            groups = []
          } = registry;
          const group = groups.find(g => g.name === groupName);
          if (!group) {
            throw new Error(`Group ${groupName} not found`);
          }
          const {
            tools = []
          } = group;
          const tool = tools.find(o => o.image === `${groupName}/${image}`);
          if (!tool) {
            throw new Error(`Tool ${groupName}/${image} not found`);
          }
          toolInfoRequest = new LoadToolVersionSettings(tool.id);
          return toolInfoRequest.fetch();
        } else {
          throw new Error(`Error fetching docker images: ${dockerRegistries.error}`);
        }
      })
      .then(() => {
        if (toolInfoRequest && toolInfoRequest.loaded) {
          return Promise.resolve(getToolVersionParameters(
            toolInfoRequest.value || [],
            version || 'latest'
          ));
        }
        throw new Error(
          `Error fetching tool info: ${toolInfoRequest ? toolInfoRequest.error : 'unknown'}`
        );
      })
      .then(resolve)
      .catch(e => {
        console.warn(e.message);
        resolve({});
      });
  });
}
