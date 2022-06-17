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

import {action, computed, observable} from 'mobx';
import {AnalysisTypes} from '../common/analysis-types';
import generateId from '../common/generate-id';
import {getComputedValue} from '../modules/implementation/parse-module-configuration';

function mapListItem (listItem) {
  if (typeof listItem !== 'object') {
    return {
      id: listItem,
      value: listItem,
      key: listItem,
      title: listItem
    };
  }
  if (typeof listItem === 'object' && listItem.title && listItem.value) {
    return {
      id: listItem.value,
      value: listItem.value,
      key: listItem.value,
      title: listItem.title
    };
  }
  return undefined;
}

/**
 * @typedef {Object} ModuleParameterOptions
 * @property {string} name
 * @property {string} [parameterName]
 * @property {function(*, AnalysisModule)} [valueFormatter]
 * @property {function(*, AnalysisModule)} [valueParser]
 * @property {string} [title]
 * @property {string} [type]
 * @property {boolean} [isList]
 * @property {boolean} [isRange]
 * @property {boolean} [local]
 * @property {boolean} [required]
 * @property {*} [value]
 * @property {boolean} [advanced]
 * @property {boolean} [hidden]
 * @property {string} [computed]
 * @property {function} [visibilityHandler]
 * @property {*[]|function} [values]
 * @property {function(ModuleParameterValue, string, object)} [renderer]
 * @property {{min: number?, max: number?}} [range]
 */

class ModuleParameter {
  id;
  name;
  local;
  parameterName;
  renderer;
  title;
  type;
  isList;
  isRange;
  /**
   * @type {AnalysisModule}
   */
  cpModule;
  required;
  advanced;
  visibilityHandler;
  valueFormatter;
  valueParser;
  _defaultValue;
  _values;
  range;
  hidden;
  computed;

  /**
   * @param {ModuleParameterOptions} [options]
   */
  constructor (options = {}) {
    this.id = `parameter_#${generateId()}`;
    const {
      name,
      parameterName = name,
      type = AnalysisTypes.string,
      isList = false,
      isRange = false,
      title = name,
      required = false,
      value,
      advanced = false,
      visibilityHandler,
      values,
      renderer,
      valueParser = (o => o),
      valueFormatter = (o => o),
      local = false,
      range,
      hidden = false,
      computed
    } = options;
    this.name = name;
    this.parameterName = parameterName;
    this.title = title;
    this.type = type;
    this.isList = isList;
    this.isRange = isRange;
    this.required = required;
    this._defaultValue = value;
    this.advanced = advanced;
    this.visibilityHandler = visibilityHandler;
    this._values = values;
    this.renderer = renderer;
    this.valueFormatter = valueFormatter;
    this.valueParser = valueParser;
    this.local = local;
    this.range = range;
    this.hidden = hidden;
    this.computed = computed;
  }

  @computed
  get values () {
    if (typeof this._values === 'function') {
      return (this._values(this.cpModule) || [])
        .map(mapListItem)
        .filter(Boolean);
    }
    if (this._values !== undefined && this._values.length) {
      return this._values
        .map(mapListItem)
        .filter(Boolean);
    }
    return [];
  }
  @computed
  get visible () {
    if (this.hidden) {
      return false;
    }
    if (this.name === 'advanced' && this.cpModule) {
      return this.cpModule.parametersConfigurations.some(config => config.advanced);
    }
    if (this.advanced) {
      const advancedValue = this.cpModule ? this.cpModule.getParameterValue('advanced') : undefined;
      if (advancedValue === false) {
        return false;
      }
    }
    if (typeof this.visibilityHandler === 'function') {
      return this.visibilityHandler(this.cpModule);
    }
    return true;
  }
  get defaultValue () {
    if (this._defaultValue !== undefined) {
      return this._defaultValue;
    }
    if (this.isList) {
      return [];
    }
    if (this.isRange) {
      return [undefined, undefined];
    }
    switch (this.type) {
      case AnalysisTypes.integer:
      case AnalysisTypes.float:
        return 0;
      default:
        return undefined;
    }
  }

  createModuleParameterValue () {
    return new ModuleParameterValue(this);
  }
}

class ModuleParameterValue {
  /**
   * @type {ModuleParameter}
   */
  @observable parameter;
  @observable _value;

  /**
   * @param {ModuleParameter} parameter
   */
  constructor (parameter) {
    this.parameter = parameter;
    this.value = parameter.defaultValue;
  }

  @computed
  get value () {
    if (this.parameter && this.parameter.computed) {
      return getComputedValue(this.parameter.computed, this.parameter.cpModule);
    }
    return this._value;
  }

  set value (aValue) {
    this._value = aValue;
  }

  getPayload () {
    if (!this.parameter) {
      return {};
    }
    const {type, isRange, valueFormatter} = this.parameter;
    const formattedValue = valueFormatter(this.value, this.parameter.cpModule);
    if (isRange) {
      return {
        [this.parameter.parameterName]: (formattedValue || []).map(idx =>
          Number.isNaN(Number(idx)) ? 0 : Number(idx)
        )
      };
    }
    switch (type) {
      case AnalysisTypes.string:
        return {
          [this.parameter.parameterName]:
            `${formattedValue === undefined ? '' : formattedValue}`
        };
      default:
        return {[this.parameter.parameterName]: formattedValue};
    }
  }

  applyValue (value) {
    if (!this.parameter) {
      return;
    }
    this.value = this.parameter.valueParser(value, this.parameter.cpModule);
    this.parameter.cpModule.changed = true;
  }

  @action
  reportChanged = () => {
    if (
      !this.parameter ||
      !this.parameter.cpModule ||
      this.parameter.cpModule.predefined
    ) {
      return;
    }
    this.parameter.cpModule.changed = true;
    if (this.parameter.cpModule.analysis) {
      this.parameter.cpModule.analysis.changed = true;
      this.parameter.cpModule.analysis.analysisRequested = true;
    }
  };
}

export {
  ModuleParameter,
  ModuleParameterValue
};
