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

const UI_NGS_PROJECT_MACHINE_RUN_PREFERENCE = 'ngs.preprocessing.machine.run.metadata.class.name';
const MACHINE_RUN_METADATA_CLASS_NAME = 'MachineRun';
const UI_NGS_PROJECT_SAMPLE_SHEET_COLUMN_PREFERENCE = 'ngs.preprocessing.samplesheet.link.column';
const SAMPLE_SHEET_COLUMN_NAME = 'SampleSheet';

export function getMachineRunMetadataClassName (preferences) {
  if (preferences && preferences.loaded) {
    return preferences.getPreferenceValue(UI_NGS_PROJECT_MACHINE_RUN_PREFERENCE) ||
      MACHINE_RUN_METADATA_CLASS_NAME;
  }
  return MACHINE_RUN_METADATA_CLASS_NAME;
}

export function getSampleSheetColumnName (preferences) {
  if (preferences && preferences.loaded) {
    return preferences.getPreferenceValue(UI_NGS_PROJECT_SAMPLE_SHEET_COLUMN_PREFERENCE) ||
      SAMPLE_SHEET_COLUMN_NAME;
  }
  return SAMPLE_SHEET_COLUMN_NAME;
}

class NgsProjectMachineRuns {
  @observable preferences;
  @observable metadataClass;
  constructor (options = {}, preferences) {
    this.preferences = preferences;
    const {
      metadataClass
    } = options;
    this.metadataClass = metadataClass;
  }

  @computed
  get isMachineRunsMetadataClass () {
    const metadataClassName = getMachineRunMetadataClassName(this.preferences);
    return metadataClassName &&
      this.metadataClass &&
      metadataClassName.toLowerCase() === this.metadataClass.toLowerCase();
  }

  isSampleSheetValue (column) {
    return this.isMachineRunsMetadataClass &&
      getSampleSheetColumnName(this.preferences) === column;
  }
}

export default NgsProjectMachineRuns;
