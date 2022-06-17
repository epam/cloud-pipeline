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

import {computed} from 'mobx';
import {ModuleParameter} from './base';
import {AnalysisTypes} from '../common/analysis-types';

function getFilesForModule (cpModule) {
  if (cpModule && cpModule.analysis) {
    const idx = cpModule.analysis.modules.indexOf(cpModule);
    return [
      cpModule.analysis.namesAndTypes,
      ...cpModule.analysis.modules.slice(0, idx)
    ]
      .filter(Boolean)
      .filter((cpModule) => !cpModule.hidden)
      .reduce((outputs, cpModule) => ([...outputs, ...cpModule.outputs]), [])
      .filter((output) => output.type === AnalysisTypes.file)
      .map((output) => output.name);
  }
  return [];
}

class FileParameter extends ModuleParameter {
  /**
   * @param {ModuleParameterOptions} options
   */
  constructor (options = {}) {
    super({
      ...options,
      type: AnalysisTypes.file,
      isList: true
    });
  }

  @computed
  get values () {
    return getFilesForModule(this.cpModule).map((output) => ({
      value: output,
      id: output,
      key: output,
      title: output
    }));
  }

  get defaultValue () {
    const firstValue = this.values[0];
    return firstValue ? firstValue.value : undefined;
  }
}

export {FileParameter, getFilesForModule};
