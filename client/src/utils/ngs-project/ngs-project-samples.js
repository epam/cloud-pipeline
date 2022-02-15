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
import {getMachineRunMetadataClassName} from './ngs-project-machine-runs';

const UI_NGS_PROJECT_SAMPLE_PREFERENCE = 'ngs.preprocessing.sample.metadata.class.name';
const SAMPLE_METADATA_CLASS_NAME = 'Sample';

export function getSampleMetadataClassName (preferences) {
  if (preferences && preferences.loaded) {
    return preferences.getPreferenceValue(UI_NGS_PROJECT_SAMPLE_PREFERENCE) ||
      SAMPLE_METADATA_CLASS_NAME;
  }
  return SAMPLE_METADATA_CLASS_NAME;
}

class NgsProjectSamples {
  @observable preferences;
  @observable metadataClass;
  @observable folderId;
  @observable entityFields;
  constructor (options = {}, preferences) {
    this.preferences = preferences;
    const {
      metadataClass,
      folderId,
      entityFields
    } = options;
    this.metadataClass = metadataClass;
    this.entityFields = entityFields;
    this.folderId = folderId;
  }

  @computed
  get isSamplesMetadataClass () {
    const metadataClassName = getSampleMetadataClassName(this.preferences);
    return metadataClassName &&
      this.metadataClass &&
      metadataClassName.toLowerCase() === this.metadataClass.toLowerCase();
  }

  getMachineRunField (className) {
    return new Promise((resolve) => {
      if (this.entityFields && this.preferences) {
        Promise.all([
          this.entityFields.fetchIfNeededOrWait(),
          this.preferences.fetchIfNeededOrWait()
        ])
          .then(() => {
            const machineRunClassName = getMachineRunMetadataClassName(this.preferences);
            const metadataClassName = className || getSampleMetadataClassName(this.preferences);
            const fieldInfo = (this.entityFields.value || [])
              .find(o => o.metadataClass && o.metadataClass.name === metadataClassName);
            const {fields = []} = fieldInfo || {};
            const referenceField = fields.find(o => o.reference && o.type === machineRunClassName);
            if (referenceField) {
              resolve({
                className: metadataClassName,
                fieldName: referenceField.name
              });
            } else {
              resolve();
            }
          })
          .catch(() => resolve());
      } else {
        resolve();
      }
    });
  }
}

export default NgsProjectSamples;
