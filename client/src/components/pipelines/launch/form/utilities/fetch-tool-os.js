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
import dockerRegistries from '../../../../../models/tools/DockerRegistriesTree';

const cache = new Map();

async function fetchToolOS (dockerImage, tree = dockerRegistries) {
  if (!dockerImage) {
    return undefined;
  }
  try {
    await tree.fetchIfNeededOrWait();
    if (!tree.loaded) {
      throw new Error(`Error fetching docker images: ${tree.error}`);
    }
    const [
      registryPath,
      groupName,
      imageAndVersion
    ] = dockerImage.split('/');
    const [image, version] = (imageAndVersion || '').split(':');
    const {
      registries = []
    } = tree.value || {};
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
    const toolInfoRequest = new LoadToolInfo(tool.id);
    await toolInfoRequest.fetch();
    if (!toolInfoRequest.loaded) {
      throw new Error(
        `Error fetching tool info: ${toolInfoRequest ? toolInfoRequest.error : 'unknown'}`
      );
    }
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
      return [
        distribution,
        distributionVersion
      ]
        .filter(Boolean)
        .join(' ');
    }
  } catch (e) {
    console.warn(e.message);
  }
  return undefined;
}

export default async function fetchToolOSCached (dockerImage, tree = dockerRegistries) {
  if (!dockerImage) {
    return undefined;
  }
  if (!cache.has(dockerImage.toLowerCase())) {
    cache.set(dockerImage.toLowerCase(), fetchToolOS(dockerImage, tree));
  }
  return cache.get(dockerImage.toLowerCase());
}
