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

import {observable, computed} from 'mobx';
import {
  getMachineRunMetadataClassName
} from './ngs-project-machine-runs';
import {
  getSampleMetadataClassName
} from './ngs-project-samples';

const UI_PROJECT_INDICATOR_PREFERENCE = 'ui.project.indicator';
// todo: change NGS project indicator preference name when backend is ready
const UI_NGS_PROJECT_INDICATOR_PREFERENCE = 'ui.ngs.project.indicator';

const DEFAULT_PROJECT_INDICATOR = {key: 'type', value: 'project'};
const DEFAULT_NGS_PROJECT_INDICATOR = {key: 'project-type', value: 'ngs-project'};

function buildMetadataCheckFunction (key, value) {
  return function check (metadata) {
    return metadata &&
      metadata[key] &&
      metadata[key].value &&
      (value || '').toLowerCase() === (metadata[key].value || '').toLowerCase();
  };
}

function buildMetadataCondition (string, defaultOptions = {}) {
  if (string) {
    const e = /^[\s]*(.*)[\s]*=[\s]*(.*)[\s]*$/.exec(string);
    if (e && e.length >= 3) {
      const key = e[1];
      const value = e[2];
      if (key && value) {
        return buildMetadataCheckFunction(key, value);
      }
    }
  }
  const {
    key,
    value
  } = defaultOptions;
  if (key && value) {
    return buildMetadataCheckFunction(key, value);
  }
  return () => false;
}

class NgsProjectInfo {
  @observable preferences;
  @observable ngsProjectFolder;
  @observable folderId;
  constructor (options = {}, preferences, folders) {
    this.preferences = preferences;
    const {
      folder,
      folderId
    } = options;
    this.folderId = folderId;
    if (folder || (folders && folderId)) {
      this.ngsProjectFolder = folder || folders.load(folderId);
    }
  }

  get pending () {
    return this.ngsProjectFolder && this.ngsProjectFolder.pending;
  }

  get loaded () {
    return this.ngsProjectFolder && this.ngsProjectFolder.loaded;
  }

  get error () {
    return this.ngsProjectFolder && this.ngsProjectFolder.error;
  }

  @computed
  get isNGSProject () {
    if (
      this.preferences &&
      this.preferences.loaded &&
      this.ngsProjectFolder &&
      this.loaded
    ) {
      const uiProjectIndicator = this.preferences
        .getPreferenceValue(UI_PROJECT_INDICATOR_PREFERENCE);
      const uiNGSProjectIndicator = this.preferences
        .getPreferenceValue(UI_NGS_PROJECT_INDICATOR_PREFERENCE);
      const folderIsProject = buildMetadataCondition(
        uiProjectIndicator,
        DEFAULT_PROJECT_INDICATOR
      );
      const projectIsNGSProject = buildMetadataCondition(
        uiNGSProjectIndicator,
        DEFAULT_NGS_PROJECT_INDICATOR
      );
      const {
        objectMetadata
      } = this.ngsProjectFolder.value;
      return folderIsProject(objectMetadata) && projectIsNGSProject(objectMetadata);
    }
    return false;
  }

  @computed
  get machineRunClassName () {
    if (
      this.preferences &&
      this.preferences.loaded
    ) {
      return getMachineRunMetadataClassName(this.preferences);
    }
    return undefined;
  }

  @computed
  get sampleClassName () {
    if (
      this.preferences &&
      this.preferences.loaded
    ) {
      return getSampleMetadataClassName(this.preferences);
    }
    return undefined;
  }

  async fetchPreferences () {
    if (this.preferences) {
      try {
        await this.preferences.fetchIfNeededOrWait();
      } catch (_) {}
    }
  }

  isMachineRunClassName (className) {
    const machineRunClassName = this.machineRunClassName;
    return className &&
      machineRunClassName &&
      className.toLowerCase() === machineRunClassName.toLowerCase();
  }

  isSampleClassName (className) {
    const sampleClassName = this.sampleClassName;
    return className &&
      sampleClassName &&
      className.toLowerCase() === sampleClassName.toLowerCase();
  }

  getMachineRunLink (entityFieldsRequest) {
    const {entityFields, metadataClasses} = this.props;
    if (entityFields.loaded && metadataClasses.loaded) {
      const mappedEntityFields = (entityFields.value || [])
        .map(e => e);
      const ignoreClasses = new Set(mappedEntityFields.map(f => f.metadataClass.id));
      const otherClasses = (metadataClasses.value || [])
        .filter(({id}) => !ignoreClasses.has(id))
        .map(metadataClass => ({
          fields: [],
          metadataClass: {...metadataClass, outOfProject: true}
        }));
      return [
        ...mappedEntityFields,
        ...otherClasses
      ];
    }
    return [];
  }
}

export default NgsProjectInfo;
