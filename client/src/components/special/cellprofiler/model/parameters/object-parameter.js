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

/**
 * @param {AnalysisModule} cpModule
 * @returns {*[]}
 */
function getObjectsForModule (cpModule) {
  if (cpModule) {
    return cpModule.modulesBefore
      .filter((cpModule) => !cpModule.hidden)
      .reduce((outputs, cpModule) => ([...outputs, ...cpModule.outputs]), [])
      .filter((output) => output.type === AnalysisTypes.object)
      .map((output) => output.name);
  }
  return [];
}

class ObjectParameter extends ModuleParameter {
  /**
   * @param {ModuleParameterOptions} options
   */
  constructor (options = {}) {
    super({
      ...options,
      type: AnalysisTypes.object,
      isList: true
    });
  }

  @computed
  get values () {
    return this.wrapValuesWithEmptyValue(
      getObjectsForModule(this.cpModule)
        .map((object) => ({
          value: object,
          id: object,
          key: object,
          title: object
        })));
  }

  get defaultValue () {
    let defaultValue;
    if (typeof this._defaultValue === 'function') {
      defaultValue = this._defaultValue(this.cpModule);
    } else if (this._defaultValue !== undefined) {
      defaultValue = this._defaultValue;
    }
    const firstValue = this.values[0];
    const predefinedValue = defaultValue
      ? this.values.find(o => o.value === defaultValue)
      : undefined;
    if (predefinedValue) {
      return predefinedValue.value;
    }
    return firstValue ? firstValue.value : undefined;
  }
}

export {ObjectParameter, getObjectsForModule};
