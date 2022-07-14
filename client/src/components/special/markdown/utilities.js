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

import {inject, observer} from 'mobx-react';
import HiddenObjects from '../../../utils/hidden-objects';
import {generateTreeData, ItemTypes} from '../../pipelines/model/treeStructureFunctions';

/**
 * @typedef {Object} CloudPipelineLinksProps
 * @property {Object} preferences
 * @property {Object} pipelinesLibrary
 * @property {Object} dockerRegistries
 * @property {function} hiddenObjectsTreeFilter,
 * @property {function} hiddenToolsTreeFilter
 */

/**
 * @typedef {Object} CloudPipelineLink
 * @property {string|number} id
 * @property {string} type
 * @property {string} url
 * @property {string} displayName
 */

/**
 * Replaces `#[link]` format with `@[link]`
 * @param {string} raw
 * @returns {string}
 */
export function prepareCloudPipelineLinks (raw) {
  let text = raw || '';
  const elementLinkRegex = /#\[([A-Za-z]+):([\d]+):([^W\]]+)\]/;
  let matchResult = text.match(elementLinkRegex);
  while (matchResult && matchResult.length === 4) {
    const type = matchResult[1];
    const identifier = matchResult[2];
    const name = matchResult[3];
    const start = matchResult.index;
    const end = matchResult.index + matchResult[0].length;
    text = text.substring(0, start) + `@[${type}:${identifier}:${name}]` + text.substring(end);
    matchResult = text.match(elementLinkRegex);
  }
  return text;
}

/**
 * Replaces link "target" attribute
 * @param {string} html
 * @param {string} target
 * @returns {string}
 */
export function processLinks (html, target = '_blank') {
  return (html || '').replace(/<a href/ig, `<a target="${target}" href`);
}

/**
 * Injects cloud pipeline objects requests
 * @param WrappedComponent
 * @returns {any}
 */
export function injectCloudPipelineLinksHelpers (WrappedComponent) {
  return inject('pipelinesLibrary', 'dockerRegistries', 'preferences')(
    HiddenObjects.injectTreeFilter(
      HiddenObjects.injectToolsFilters(
        observer(
          WrappedComponent
        )
      )
    )
  );
}

/**
 * Fetches cloud pipeline objects
 * @param {CloudPipelineLinksProps} options
 * @returns {Promise<unknown[]>}
 */
export function fetchCloudPipelineLinks (options = {}) {
  const {
    dockerRegistries,
    pipelinesLibrary,
    preferences
  } = options;
  return Promise.all([
    dockerRegistries ? dockerRegistries.fetchIfNeededOrWait() : Promise.resolve(),
    pipelinesLibrary ? pipelinesLibrary.fetchIfNeededOrWait() : Promise.resolve(),
    preferences ? preferences.fetchIfNeededOrWait() : Promise.resolve()
  ]);
}

/**
 * Gets cloud pipeline object links
 * @param {CloudPipelineLinksProps} options
 * @returns {CloudPipelineLink[]}
 */
export function getCloudPipelineLinks (options = {}) {
  const links = [];
  const {
    dockerRegistries,
    pipelinesLibrary,
    hiddenObjectsTreeFilter,
    hiddenToolsTreeFilter
  } = options;
  if (pipelinesLibrary && pipelinesLibrary.loaded) {
    const items = generateTreeData(
      pipelinesLibrary.value,
      {
        types: [ItemTypes.folder, ItemTypes.pipeline, ItemTypes.configuration, ItemTypes.storage],
        filter: hiddenObjectsTreeFilter()
      }
    );
    const makeFlat = (children) => {
      const result = [];
      for (let i = 0; i < (children || []).length; i++) {
        const child = children[i];
        if (child.type === ItemTypes.folder) {
          result.push(...makeFlat(child.children));
        } else {
          result.push({
            id: child.id,
            type: child.type,
            displayName: child.name,
            url: child.url()
          });
        }
      }
      return result;
    };
    links.push(...makeFlat(items));
  }
  if (dockerRegistries && dockerRegistries.loaded && hiddenToolsTreeFilter) {
    const {registries} = hiddenToolsTreeFilter(dockerRegistries.value);
    for (let r = 0; r < registries.length; r++) {
      const registry = registries[r];
      for (let g = 0; g < (registry.groups || []).length; g++) {
        const group = registry.groups[g];
        for (let t = 0; t < (group.tools || []).length; t++) {
          const tool = group.tools[t];
          const [, toolName] = tool.image.split('/');
          links.push({
            type: 'tool',
            displayName: toolName,
            id: tool.id,
            url: `tool/${tool.id}`
          });
        }
      }
    }
  }
  return links;
}

export function getCloudPipelineUrl (relativeUri) {
  return `#${relativeUri.startsWith('/') ? relativeUri : `/${relativeUri}`}`;
}

export function getCloudPipelineAbsoluteURL (relativeUri, options = {}) {
  const {preferences} = options;
  const base = preferences.loaded
    ? (preferences.getPreferenceValue('base.pipe.distributions.url') || '')
    : '';
  return `${base}${getCloudPipelineUrl(relativeUri)}`;
}

export function getCloudPipelineAbsoluteURLFn (options = {}) {
  const {preferences} = options;
  const base = preferences && preferences.loaded
    ? (preferences.getPreferenceValue('base.pipe.distributions.url') || '')
    : '';
  return (relativeUri) => `${base}${getCloudPipelineUrl(relativeUri)}`;
}
