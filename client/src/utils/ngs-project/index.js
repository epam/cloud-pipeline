/*
 * Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import {inject} from 'mobx-react';
import NgsProjectInfo from './ngs-project-info';
import NgsProjectMachineRuns from "./ngs-project-machine-runs";

/**
 * @typedef {Object} NgsProjectConfiguration
 * @property {String} [folderIdProperty]
 * @property {String} [folderRequestProperty]
 */

/**
 * Injects ngsProjectInfo instance
 * @param {NgsProjectConfiguration} configuration
 * @returns {function(*=): any}
 */
export function ngsProjectConfiguration (configuration = {}) {
  const {
    folderIdProperty = 'folderId',
    folderRequestProperty = 'folder'
  } = configuration;
  return (WrappedComponent) => inject('preferences', 'folders')(
    inject((stores = {}, props = {}) => {
      const {preferences, folders} = stores;
      const folderId = props[folderIdProperty];
      const folder = props[folderRequestProperty];
      return {
        ngsProjectInfo: new NgsProjectInfo({folder, folderId}, preferences, folders)
      };
    })(WrappedComponent)
  );
}

/**
 * @typedef {Object} NgsProjectMachineRunsConfiguration
 * @property {String} [metadataClassProperty]
 */

/**
 * Injects ngsProjectMachineRuns instance
 * @param {NgsProjectMachineRunsConfiguration} configuration
 * @returns {function(*=): any}
 */
export function ngsProjectMachineRunsConfiguration (configuration = {}) {
  const {
    metadataClassProperty = 'metadataClass'
  } = configuration;
  return (WrappedComponent) => inject('preferences')(
    inject((stores = {}, props = {}) => {
      const {preferences} = stores;
      const metadataClass = props[metadataClassProperty];
      return {
        ngsProjectMachineRuns: new NgsProjectMachineRuns({metadataClass}, preferences)
      };
    })(WrappedComponent)
  );
}

export function ngsProjectMachineRuns (WrappedComponent) {
  return ngsProjectMachineRunsConfiguration({})(WrappedComponent);
}

export default function ngsProject (WrappedComponent) {
  return ngsProjectConfiguration({})(WrappedComponent);
}
