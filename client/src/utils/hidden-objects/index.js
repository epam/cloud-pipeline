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

import {computed, observable} from 'mobx';
import ObjectTypes from './object-types';
import {
  checkObjectsHOC,
  checkObjectsWithParentHOC,
  HIDDEN_OBJECTS_INJECTION
} from './hoc';
import treeFilter from './tree-filter';
import injectTreeFilter from './inject-tree-filter';
import * as toolsFilters from './tools-filter';
import injectToolsFilters from './inject-tools-filters';

class HiddenObjects {
  @observable preferences;
  @observable authenticatedUserInfo;

  constructor (preferences, authenticatedUserInfo) {
    this.preferences = preferences;
    this.authenticatedUserInfo = authenticatedUserInfo;
    function treeFilterDetached (...opts) {
      return treeFilter(this)(...opts);
    }
    function toolsFilterDetached (...opts) {
      return toolsFilters.toolsFilter(this)(...opts);
    }
    function toolGroupsFilterDetached (...opts) {
      return toolsFilters.toolGroupsFilter(this)(...opts);
    }
    function toolRegistriesFilterDetached (...opts) {
      return toolsFilters.toolRegistriesFilter(this)(...opts);
    }
    function toolTreeFilterDetached (...opts) {
      return toolsFilters.toolsTreeFilter(this)(...opts);
    }
    this.treeFilter = treeFilterDetached.bind(this);
    this.toolsFilter = toolsFilterDetached.bind(this);
    this.toolGroupsFilter = toolGroupsFilterDetached.bind(this);
    this.toolRegistriesFilter = toolRegistriesFilterDetached.bind(this);
    this.toolTreeFilter = toolTreeFilterDetached.bind(this);
  }

  fetchIfNeededOrWait () {
    return Promise.all([
      this.preferences.fetchIfNeededOrWait(),
      this.authenticatedUserInfo.fetchIfNeededOrWait()
    ]);
  }

  fetch () {
    return Promise.all([
      this.preferences.fetch(),
      this.authenticatedUserInfo.fetch()
    ]);
  }

  @computed
  get loaded () {
    return this.preferences?.loaded && this.authenticatedUserInfo?.loaded;
  }

  @computed
  get hiddenObjects () {
    if (this.authenticatedUserInfo.loaded && this.authenticatedUserInfo.value.admin) {
      return {};
    }
    const o = this.preferences?.hiddenObjects;
    return Object.entries(o || {})
      .map(([key, value]) => ({
        [key]: new Set(Array.from(value).map(o => String(o).toLowerCase()))
      }))
      .reduce((acc, cur) => ({...acc, ...cur}), {});
  }

  isHidden (type, identifier) {
    if (type && identifier && this.hiddenObjects[type] &&
      this.hiddenObjects[type].has(String(identifier))) {
    }
    return type && identifier && this.hiddenObjects[type] &&
      this.hiddenObjects[type].has(String(identifier));
  }

  isStorageHidden (identifier) {
    return this.isHidden(ObjectTypes.storage, identifier);
  }

  isPipelineHidden (identifier) {
    return this.isHidden(ObjectTypes.pipeline, identifier);
  }

  isFolderHidden (identifier) {
    return this.isHidden(ObjectTypes.folder, identifier);
  }

  isParentHidden (object, folders = []) {
    if (!object) {
      return false;
    }
    const {parentId, parentFolderId} = object;
    const parentFolder = (folders || []).find(o => +(o.id) === (parentFolderId || parentId));
    if (parentFolder) {
      if (this.isFolderHidden(parentFolder.id)) {
        return true;
      }
      return this.isParentHidden(parentFolder, folders);
    }
    return false;
  }
}

HiddenObjects.checkPipelines = checkObjectsHOC(ObjectTypes.pipeline);
HiddenObjects.checkPipelineVersions =
  checkObjectsWithParentHOC(ObjectTypes.pipelineVersion, false);
HiddenObjects.checkConfigurations = checkObjectsHOC(ObjectTypes.configuration);
HiddenObjects.checkStorages = checkObjectsHOC(ObjectTypes.storage);
HiddenObjects.checkFolders = checkObjectsHOC(ObjectTypes.folder);
HiddenObjects.checkMetadataClassesWithParent =
  checkObjectsWithParentHOC(ObjectTypes.metadataClass, true);
HiddenObjects.checkMetadataFolders = checkObjectsHOC(ObjectTypes.metadataFolder);
HiddenObjects.checkTools = checkObjectsHOC(ObjectTypes.tool);
HiddenObjects.checkToolGroups = checkObjectsHOC(ObjectTypes.toolGroup);
HiddenObjects.checkToolRegistries = checkObjectsHOC(ObjectTypes.toolRegistry);

HiddenObjects.injectionName = HIDDEN_OBJECTS_INJECTION;
HiddenObjects.ObjectTypes = ObjectTypes;
HiddenObjects.injectTreeFilter = injectTreeFilter;
HiddenObjects.injectToolsFilters = injectToolsFilters;

export default HiddenObjects;
