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

import {action, computed, isObservableArray, observable} from 'mobx';
import {AnalysisTypes} from '../common/analysis-types';
import generateId from '../common/generate-id';
import {
  getComputedValue,
  getComputedValueLink,
  modifyValue,
  reverseModifyValue
} from '../modules/parse-module-configuration';

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
 * @property {bool} [multiple]
 * @property {boolean} [isRange]
 * @property {boolean} [local]
 * @property {boolean} [required]
 * @property {*} [value]
 * @property {boolean} [advanced]
 * @property {boolean} [hidden]
 * @property {string|function} [computed]
 * @property {function} [visibilityHandler]
 * @property {*[]|function} [values]
 * @property {function(ModuleParameterValue, string, object)} [renderer]
 * @property {{min: number?, max: number?}} [range]
 * @property {string} [emptyValue]
 * @property {boolean} [showTitle=true]
 * @property {boolean} [exportParameter=true]
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
  multiple;
  isRange;
  /**
   * @type {AnalysisModule}
   */
  @observable cpModule;
  @observable _required;
  advanced;
  visibilityHandler;
  valueFormatter;
  valueParser;
  _defaultValue;
  _values;
  range;
  hidden;
  computed;
  emptyValue;
  showTitle;
  exportParameter = true;

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
      multiple = false,
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
      computed,
      emptyValue,
      showTitle = true,
      exportParameter = true
    } = options;
    this.name = name;
    this.parameterName = parameterName;
    this.title = title;
    this.type = type;
    this.isList = isList;
    this.multiple = multiple;
    this.isRange = isRange;
    this._required = required;
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
    this.emptyValue = emptyValue;
    this.showTitle = showTitle;
    this.exportParameter = exportParameter;
  }

  @computed
  get pipeline () {
    if (!this.cpModule) {
      return undefined;
    }
    return this.cpModule.pipeline;
  }

  @computed
  get physicalSize () {
    if (!this.pipeline) {
      return undefined;
    }
    return this.pipeline.physicalSize;
  }

  @computed
  get analysis () {
    if (!this.pipeline) {
      return undefined;
    }
    return this.pipeline.analysis;
  }

  @computed
  get channels () {
    if (!this.pipeline) {
      return [];
    }
    return this.pipeline.channels;
  }

  @computed
  get isOutput () {
    return this.cpModule &&
      !!this.cpModule.outputParameters.find(o => o === this);
  }

  wrapValuesWithEmptyValue = (values = []) => {
    const _values = [];
    if (this.isList && !this.multiple && this.emptyValue) {
      _values.push(mapListItem(this.emptyValue));
    }
    return _values.concat(values);
  };

  @computed
  get values () {
    if (typeof this._values === 'function') {
      return this.wrapValuesWithEmptyValue(
        (this._values(this.cpModule) || [])
          .map(mapListItem)
          .filter(Boolean)
      );
    } else if (this._values !== undefined && this._values.length) {
      return this.wrapValuesWithEmptyValue(
        this._values
          .map(mapListItem)
          .filter(Boolean)
      );
    }
    return this.wrapValuesWithEmptyValue([]);
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
  @computed
  get required () {
    return this._required && this.visible;
  }
  get defaultValue () {
    if (typeof this._defaultValue === 'function') {
      return this._defaultValue(this.cpModule);
    }
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
  get cpModule () {
    if (!this.parameter) {
      return undefined;
    }
    return this.parameter.cpModule;
  }

  @computed
  get pipeline () {
    if (!this.parameter) {
      return undefined;
    }
    return this.parameter.pipeline;
  }

  @computed
  get analysis () {
    if (!this.parameter) {
      return undefined;
    }
    return this.parameter.analysis;
  }

  @computed
  get channels () {
    if (!this.parameter) {
      return undefined;
    }
    return this.parameter.channels;
  }

  @computed
  get value () {
    return this.getValue();
  }

  set value (aValue) {
    this.setValue(aValue);
  }

  @computed
  get isEmpty () {
    const aValue = this.value;
    return aValue === undefined ||
      (this.parameter && this.parameter.isList && this.parameter.emptyValue === aValue) ||
      (typeof aValue === 'string' && aValue.trim() === '') ||
      (typeof aValue === 'object' && aValue.length === 0);
  }

  get isOutput () {
    if (!this.parameter) {
      return true;
    }
    return this.parameter.isOutput;
  }

  get isInvalid () {
    if (!this.parameter) {
      return true;
    }
    return (this.parameter.required && this.isEmpty) ||
      (
        this.parameter.isOutput &&
        this.cpModule &&
        this.cpModule.modulesBefore.filter((cpModule) => !cpModule.hidden)
          .reduce((outputs, cpModule) => ([...outputs, ...cpModule.outputs]), [])
          .filter((output) => output.name === this.value).length > 0
      );
  }

  get payload () {
    return this.getPayload();
  }

  getPayload (validate = false, exportLocal = false, useSystemNames = false) {
    if (!this.parameter) {
      return {};
    }
    const {
      type,
      isRange,
      valueFormatter,
      isList,
      multiple,
      local,
      required
    } = this.parameter;
    if (local && !exportLocal) {
      return {};
    }
    const multipleFormatter = o => {
      if (!multiple) {
        return o;
      }
      return o && (isObservableArray(o) || Array.isArray(o))
        ? o
        : [o].filter(oo => oo !== undefined);
    };
    const name = useSystemNames
      ? this.parameter.name
      : this.parameter.parameterName;
    let formattedValue = valueFormatter(multipleFormatter(this.value), this.parameter.cpModule);
    if (isRange) {
      return {
        [name]: (formattedValue || []).map(idx =>
          Number.isNaN(Number(idx)) ? 0 : Number(idx)
        )
      };
    }
    if (isList && !formattedValue) {
      formattedValue = valueFormatter(multipleFormatter(this.parameter.emptyValue));
    }
    const isEmpty = formattedValue === undefined ||
      (typeof formattedValue === 'string' && formattedValue.trim() === '') ||
      (typeof formattedValue === 'object' && formattedValue.length === 0);
    if (validate && this.parameter.visible && required && isEmpty) {
      const moduleName = this.parameter.cpModule
        ? (this.parameter.cpModule.title || this.parameter.cpModule.name)
        : '';
      const parameterName = this.parameter.title || this.parameter.name;
      const moduleString = moduleName ? ` of the "${moduleName}" module ` : '';
      throw new Error(
        `"${parameterName}" parameter${moduleString}is required`
      );
    }
    switch (type) {
      case AnalysisTypes.string:
        return {
          [name]:
            `${formattedValue === undefined ? '' : formattedValue}`
        };
      default:
        return {[name]: formattedValue};
    }
  }

  getValue (...modifier) {
    let result = this._value;
    if (this.parameter && this.parameter.computed) {
      result = getComputedValue(this.parameter.computed, this.parameter.cpModule);
    }
    if (
      this.parameter &&
      this.parameter.isList &&
      !this.parameter.multiple &&
      (!result || `${result}`.trim() === '')
    ) {
      result = this.parameter.emptyValue;
    }
    return modifyValue(
      result,
      this.pipeline,
      ...modifier
    );
  }

  setValue (aValue, ...modifier) {
    let result = aValue;
    if (
      this.parameter &&
      this.parameter.isList &&
      !this.parameter.multiple &&
      (!result || `${result}`.trim() === '')
    ) {
      result = this.parameter.emptyValue;
    }
    if (this.parameter && this.parameter.multiple) {
      result = aValue && (isObservableArray(aValue) || Array.isArray(aValue))
        ? aValue
        : [aValue].filter(o => o !== undefined);
    }
    result = reverseModifyValue(result, this.pipeline, ...modifier);
    if (this.parameter && this.parameter.computed) {
      const linkInfo = getComputedValueLink(this.parameter.computed, this.parameter.cpModule);
      if (linkInfo) {
        linkInfo.parameterValue.setValue(result, ...linkInfo.modifiers);
        return;
      }
    }
    this._value = result;
  }

  applyValue (value) {
    if (!this.parameter) {
      return;
    }
    try {
      this.value = this.parameter.valueParser(value, this.parameter.cpModule);
    } catch (e) {
      console.warn('Error applying value to parameter', this.parameter.name, 'value:', value);
    }
    this.parameter.cpModule.changed = true;
  }

  @action
  reportChanged = () => {
    if (!this.cpModule || this.cpModule.predefined) {
      return;
    }
    this.cpModule.changed = true;
    if (this.analysis) {
      this.analysis.changed = true;
      this.analysis.analysisRequested = true;
    }
  };
  exportParameterValue = () => {
    const payload = this.getPayload(false, true, true);
    return Object.entries(payload)
      .map(([name, value]) => `${name}:${JSON.stringify(value)}`);
  }
}

export {
  ModuleParameter,
  ModuleParameterValue
};
