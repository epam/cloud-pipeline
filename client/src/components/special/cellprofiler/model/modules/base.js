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
import {BooleanParameter} from '../parameters';
import {getOutputFileAccessInfo} from '../analysis/output-utilities';

const IGNORED_PARAMETERS = [
  'Select objects to display',
  'Select outline color'
];

function splitStringByUppercaseLetters (string) {
  if (!string) {
    return '';
  }
  const parts = [];
  let tmp = string.slice();
  const findNextPart = () => {
    if (tmp.length === 0) {
      return undefined;
    }
    const res = /^(.+?)([A-Z]|$)/.exec(tmp);
    if (res && res.length > 0) {
      tmp = tmp.slice(res[1].length);
      return res[1];
    }
    return undefined;
  };
  let part;
  do {
    part = findNextPart();
    if (part) {
      parts.push(part);
    }
  } while (part);
  return parts.join(' ');
}

class AnalysisModuleOutput {
  /**
   * @type {AnalysisTypes}
   */
  @observable type = AnalysisTypes.void;
  @observable value;
  @observable name;
  @observable module;
}

/**
 * Return non-hidden modules
 * @param {AnalysisModule[]} modules
 * @returns {AnalysisModule[]}
 */
function collapseModules (modules = []) {
  return modules.filter(module => !module.hidden);
}

/**
 * Return modules with hidden dependencies
 * @param {AnalysisModule[]} modules
 * @returns {AnalysisModule[]}
 */
function expandModules (modules = []) {
  return modules.reduce(
    (result, module) => ([...result, module, ...module.hiddenModules]),
    []
  );
}

class AnalysisModule {
  static get identifier () {
    return this.name;
  }
  static get moduleTitle () {
    return splitStringByUppercaseLetters(this.identifier);
  }
  id;
  name;
  title;
  @observable syncInfo;
  @observable changed = false;
  @observable hidden = false;
  static predefined = false;
  get predefined () { return this.constructor.predefined; };
  /***
   * @type {ModuleParameterValue[]}
   */
  @observable parameters = [];
  @observable extraParameters = {};
  /***
   * @type {ModuleParameter[]}
   */
  @observable parametersConfigurations = [];
  /**
   * @type {AnalysisModuleOutput[]}
   */
  @observable moduleOutputs = [];
  @observable executionResults = [];
  @observable hiddenModules = [];
  /**
   * @type {Analysis}
   */
  @observable analysis;
  /**
   * @param {Analysis} analysis
   * @param {boolean} [hidden=false]
   */
  constructor (analysis, hidden = false) {
    this.id = `module_#${generateId()}`;
    this.hidden = hidden;
    this.analysis = analysis;
    this.name = this.constructor.identifier;
    this.title = this.constructor.moduleTitle;
    this.initialize();
    this.parametersConfigurations.forEach((configuration) => {
      configuration.module = this;
    });
    this.parameters = this.parametersConfigurations
      .map((configuration) => configuration.createModuleParameterValue());
    this.changed = true;
  }
  @computed
  get outputs () {
    return this.moduleOutputs;
  }
  @computed
  get hasExecutionResults () {
    return this.executionResults.length > 0;
  }
  get displayName () {
    if (this.predefined || !this.analysis) {
      return this.title;
    }
    const idx = this.analysis.modules.indexOf(this) + 1;
    return `#${idx} ${this.title}`;
  }
  initialize () {
    this.parametersConfigurations.push(new BooleanParameter({
      name: 'advanced',
      title: 'Show advanced settings',
      value: false
    }));
  }

  /**
   * @param {ModuleParameter} parameter
   */
  registerParameters (...parameter) {
    this.parametersConfigurations.push(...parameter);
  }
  getParameterConfiguration (name) {
    return this.parametersConfigurations.find(config => config.name === name);
  }
  getParameterValue (name) {
    const parameterValue = this.parameters.find(parameter => parameter.parameter.name === name);
    if (parameterValue) {
      return parameterValue.value;
    }
    return undefined;
  }
  setParameterValue (name, value) {
    const parameterValue = this.parameters.find(parameter => parameter.parameter.name === name);
    if (parameterValue) {
      parameterValue.value = value;
    }
  }
  getAllVisibleParameters () {
    return this.parameters.filter(parameter => parameter.parameter.visible);
  }
  @action
  update () {}

  @computed
  get modules () {
    if (this.analysis) {
      return (this.analysis.modules || []);
    }
    return [];
  }

  @computed
  get order () {
    return this.modules.indexOf(this);
  }
  @computed
  get isFirst () {
    const order = this.order;
    return this.modules.slice(0, order).filter(module => !module.hidden).length === 0;
  }
  @computed
  get isLast () {
    const order = this.order;
    return this.modules.slice(order + 1).filter(module => !module.hidden).length === 0;
  }
  @action
  moveUp () {
    if (this.analysis && !this.isFirst) {
      const modules = collapseModules([...this.analysis.modules]);
      const index = modules.indexOf(this);
      if (index > 0) {
        modules.splice(index, 1);
        modules.splice(index - 1, 0, this);
        this.analysis.modules = expandModules(modules);
        this.analysis.changed = true;
        this.analysis.analysisRequested = true;
      }
    }
  }
  @action
  moveDown () {
    if (this.analysis && !this.isLast) {
      const modules = collapseModules([...this.analysis.modules]);
      const index = modules.indexOf(this);
      if (index < modules.length - 1) {
        modules.splice(index, 1);
        modules.splice(index + 1, 0, this);
        this.analysis.modules = expandModules(modules);
        this.analysis.changed = true;
        this.analysis.analysisRequested = true;
      }
    }
  }
  @action
  remove () {
    if (this.analysis) {
      const modules = collapseModules([...this.analysis.modules]);
      const index = modules.indexOf(this);
      modules.splice(index, 1);
      this.analysis.modules = expandModules(modules);
      this.analysis.changed = true;
      this.analysis.analysisRequested = true;
    }
  }

  getPayload = () => {
    return this.parameters
      .filter((parameter) =>
        !/^advanced$/i.test(parameter.parameter.name) && !parameter.parameter.local
      )
      .reduce((payload, item) => ({
        ...payload,
        ...item.getPayload()
      }), {...this.extraParameters});
  }

  applySettings = (settings) => {
    this.extraParameters = {};
    Object.entries(settings || {})
      .forEach(([parameterName, value]) => {
        const parameter = this.parameters.find(p => p.parameter.parameterName === parameterName);
        if (parameter) {
          parameter.applyValue(value);
        } else if (!IGNORED_PARAMETERS.includes(parameterName)) {
          this.extraParameters[parameterName] = value;
        }
      });
  };

  clearExecutionResults = () => {
    this.executionResults = [];
  }

  setExecutionResults = async (module) => {
    this.clearExecutionResults();
    if (module) {
      const {outputs = []} = module;
      this.executionResults = await Promise.all(outputs.slice().map(getOutputFileAccessInfo));
    }
  };
}

export {
  AnalysisModule,
  AnalysisModuleOutput
};
