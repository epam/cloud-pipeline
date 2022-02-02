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

const MACHINE_RUN_METADATA_CLASS_NAME = 'MachineRun';

function getMachineRunMetadataClassName (preferences) {
  if (preferences && preferences.loaded) {
    // todo: fetch machine run class name from preferences
  }
  return MACHINE_RUN_METADATA_CLASS_NAME;
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
    return this.isMachineRunsMetadataClass && /^SampleSheet$/i.test(column);
  }
}

export default NgsProjectMachineRuns;
