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
import parseModuleConfiguration from './implementation/parse-module-configuration';
import parameterInitializer from '../parameters/parameter-initializer';

const IGNORED_PARAMETERS = [
  'Select objects to display',
  'Select outline color'
];

class AnalysisModuleOutput {
  /**
   * @type {AnalysisTypes}
   */
  @observable type = AnalysisTypes.void;
  @observable value;
  @observable name;
  @observable cpModule;
}

/**
 * Return non-hidden modules
 * @param {AnalysisModule[]} modules
 * @returns {AnalysisModule[]}
 */
function collapseModules (modules = []) {
  return modules.filter(cpModule => !cpModule.hidden);
}

/**
 * Return modules with hidden dependencies
 * @param {AnalysisModule[]} modules
 * @returns {AnalysisModule[]}
 */
function expandModules (modules = []) {
  return modules.reduce(
    (result, cpModule) => ([...result, cpModule, ...cpModule.hiddenModules]),
    []
  );
}

class AnalysisModule {
  id;
  name;
  title;
  @observable outputsConfiguration;
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
  @observable hiddenModules = [];
  /**
   * @type {Analysis}
   */
  @observable analysis;
  pipeline;
  composed = false;

  /**
   * @typedef {Object} AnalysisInitializationOptions
   * @property {boolean} [hidden=false]
   * @property {string} [name]
   * @property {string} [title]
   * @property {string[]} [parameters]
   * @property {string[]} [outputs]
   * @property {string} [output]
   * @property {*[]|function} [pipeline]
   * @property {boolean} [composed=false]
   */
  /**
   * @param {Analysis} analysis
   * @param {AnalysisInitializationOptions} [options]
   */
  constructor (analysis, options = {}) {
    const {
      hidden = false,
      name,
      title,
      parameters = [],
      outputs = [],
      composed = false,
      pipeline = []
    } = parseModuleConfiguration(options);
    this.id = `module_#${generateId()}`;
    this.hidden = hidden;
    this.analysis = analysis;
    this.name = name;
    this.title = title || name;
    this.initialize();
    this.registerParameters(...parameters.map(parameterInitializer));
    this.outputsConfiguration = outputs;
    this.parametersConfigurations.forEach((configuration) => {
      configuration.cpModule = this;
    });
    this.parameters = this.parametersConfigurations
      .map((configuration) => configuration.createModuleParameterValue());
    this.changed = true;
    this.composed = composed;
    this.pipeline = pipeline;
  }
  @computed
  get outputs () {
    return this.outputsConfiguration.map((outputConfiguration) => {
      const {
        name,
        type,
        criteria
      } = outputConfiguration;
      const value = this.getParameterValue(name);
      if (value && criteria(this)) {
        return {
          name: value,
          type,
          cpModule: this
        };
      }
      return undefined;
    }).filter(Boolean);
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
    return this.parametersConfigurations.find(config => config.name === name) ||
      this.parametersConfigurations.find(config => config.parameterName === name);
  }
  getParameterValue (name) {
    if (/^uuid$/i.test(name)) {
      return this.id;
    }
    const parameterValue = this.parameters.find(parameter => parameter.parameter.name === name) ||
      this.parameters.find(parameter => parameter.parameter.parameterName === name);
    if (parameterValue) {
      return parameterValue.value;
    }
    return undefined;
  }
  setParameterValue (name, value) {
    const parameterValue = this.parameters.find(parameter => parameter.parameter.name === name) ||
      this.parameters.find(parameter => parameter.parameter.parameterName === name);
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
    return this.modules.slice(0, order).filter(cpModule => !cpModule.hidden).length === 0;
  }
  @computed
  get isLast () {
    const order = this.order;
    return this.modules.slice(order + 1).filter(cpModule => !cpModule.hidden).length === 0;
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
}

export {
  AnalysisModule,
  AnalysisModuleOutput
};
