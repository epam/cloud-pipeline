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

import ObjectTypes from './object-types';
import {FETCH_ID_SYMBOL} from '../../models/preferences/PreferencesLoad';
import {HIDDEN_OBJECTS_CACHE_ID} from '../../models/tools/DockerRegistriesTree';

function toolEntityFilter (hiddenObjects, type) {
  return function filter (entity) {
    return !hiddenObjects || !hiddenObjects.isHidden(type, entity?.id);
  };
}

export function toolsFilter (hiddenObjects) {
  return toolEntityFilter(hiddenObjects, ObjectTypes.tool);
}

export function toolGroupsFilter (hiddenObjects) {
  return toolEntityFilter(hiddenObjects, ObjectTypes.toolGroup);
}

export function toolRegistriesFilter (hiddenObjects) {
  return toolEntityFilter(hiddenObjects, ObjectTypes.toolRegistry);
}

export function toolsTreeFilter (hiddenObjects) {
  return function filter (tree) {
    if (
      tree &&
      tree[HIDDEN_OBJECTS_CACHE_ID] &&
      tree[HIDDEN_OBJECTS_CACHE_ID].id === hiddenObjects.preferences[FETCH_ID_SYMBOL]
    ) {
      return tree[HIDDEN_OBJECTS_CACHE_ID].cache;
    }
    const {
      registries: unprocessedRegistries = [],
      ...rest
    } = tree || {};
    const registries = unprocessedRegistries
      .filter(toolRegistriesFilter(hiddenObjects))
      .map(registry => {
        const {groups: unprocessedGroups = [], ...groupsRest} = registry || {};
        return {
          ...groupsRest,
          groups: unprocessedGroups
            .filter(toolGroupsFilter(hiddenObjects))
            .map(group => {
              const {tools: unprocessedTools = [], ...toolsRest} = group || {};
              return {
                ...toolsRest,
                tools: unprocessedTools.filter(toolsFilter(hiddenObjects))
              };
            })
        };
      });
    const result = {
      ...rest,
      registries
    };
    tree[HIDDEN_OBJECTS_CACHE_ID] = {
      id: hiddenObjects.preferences[FETCH_ID_SYMBOL],
      cache: result
    };
    return result;
  };
}
