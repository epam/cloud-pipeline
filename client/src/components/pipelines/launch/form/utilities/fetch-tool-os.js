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

import LoadToolInfo from '../../../../../models/tools/LoadToolInfo';

export default function fetchToolOS (dockerImage, dockerRegistries) {
  if (!dockerImage || !dockerRegistries) {
    return Promise.resolve(undefined);
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
          toolInfoRequest = new LoadToolInfo(tool.id);
          return toolInfoRequest.fetch();
        } else {
          throw new Error(`Error fetching docker images: ${dockerRegistries.error}`);
        }
      })
      .then(() => {
        if (toolInfoRequest && toolInfoRequest.loaded) {
          const {
            versions = []
          } = toolInfoRequest.value || {};
          const versionInfo = versions.find(v => v.version === version);
          if (
            versionInfo &&
            versionInfo.scanResult &&
            versionInfo.scanResult.toolOSVersion &&
            versionInfo.scanResult.toolOSVersion.distribution
          ) {
            const {
              distribution,
              version: distributionVersion = ''
            } = versionInfo.scanResult.toolOSVersion;
            const os = [
              distribution,
              distributionVersion
            ]
              .filter(Boolean)
              .join(' ');
            return Promise.resolve(os);
          } else {
            return Promise.resolve(undefined);
          }
        } else {
          throw new Error(
            `Error fetching tool info: ${toolInfoRequest ? toolInfoRequest.error : 'unknown'}`
          );
        }
      })
      .then(resolve)
      .catch(e => {
        console.warn(e.message);
        resolve(undefined);
      });
  });
}
