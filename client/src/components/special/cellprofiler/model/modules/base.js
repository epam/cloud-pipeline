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

import {action, computed, observable, makeObservable} from 'mobx';
import {AnalysisTypes} from '../common/analysis-types';
import generateId from '../common/generate-id';
import {BooleanParameter} from '../parameters';
import parseModuleConfiguration, {
  splitStringWithBrackets
} from './parse-module-configuration';
import parameterInitializer from '../parameters/parameter-initializer';
import allModules from './implementation';

class AnalysisModuleOutput {
  /**
   * @type {AnalysisTypes}
   */
  type = AnalysisTypes.void;
  value;
  name;
  cpModule;

  constructor () {
    makeObservable(this, {
      type: observable,
      value: observable,
      name: observable,
      cpModule: observable
    });
  }
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

function searchParameterCriteria (property) {
  return function search (name) {
    return (p) => p.parameter[property] === name;
  };
}

const searchParameterByName = searchParameterCriteria('name');
const searchParameterByParameterName = searchParameterCriteria('parameterName');

class AnalysisModule {
  id;
  name;
  title;
  outputsConfiguration;
  syncInfo;
  changed = false;
  hidden = false;
  sourceImageParameter;
  static predefined = false;
  get predefined () { return this.constructor.predefined; };
  /***
   * @type {ModuleParameterValue[]}
   */
  parameters = [];
  extraParameters = {};
  /***
   * @type {ModuleParameter[]}
   */
  parametersConfigurations = [];
  hiddenModules = [];
  /**
   * @type {AnalysisPipeline}
   */
  pipeline;
  subModules;
  /**
   * @type {AnalysisModule}
   */
  parentModule;
  pending = false;
  done = false;
  statusReporting = false;
  composed = false;

  /**
   * @typedef {Object} AnalysisInitializationOptions
   * @property {boolean} [hidden=false]
   * @property {string} [id]
   * @property {string} [name]
   * @property {string} [title]
   * @property {string[]} [parameters]
   * @property {{name: string?, type: string?, criteria: function?}[]} [outputs]
   * @property {string} [output]
   * @property {string} [sourceImageParameter]
   * @property {*[]|function} [subModules]
   * @property {boolean} [composed=false]
   */
  /**
   * @param {AnalysisPipeline} pipeline
   * @param {AnalysisInitializationOptions} [options]
   */
  constructor (pipeline, options = {}) {
    // TODO: consider makeAutoObservable
    makeObservable(this, {
      outputsConfiguration: observable,
      syncInfo: observable,
      changed: observable,
      hidden: observable,
      sourceImageParameter: observable,
      parameters: observable,
      extraParameters: observable,
      parametersConfigurations: observable,
      hiddenModules: observable,
      pipeline: observable,
      pending: observable,
      done: observable,
      statusReporting: observable,
      analysis: computed,
      channels: computed,
      outputs: computed,
      fileInputParameters: computed,
      outputParameters: computed,
      sourceImage: computed,
      modules: computed,
      order: computed,
      modulesBefore: computed,
      modulesAfter: computed,
      isFirst: computed,
      isLast: computed,
      update: action,
      moveUp: action,
      moveDown: action,
      remove: action
    });
    const {
      hidden = false,
      name,
      title,
      parameters = [],
      outputs = [],
      composed = false,
      subModules = [],
      id,
      sourceImageParameter
    } = parseModuleConfiguration(options);
    this.uuid = generateId();
    this.id = id || `module_#${this.uuid}`;
    this.hidden = hidden;
    this.pipeline = pipeline;
    this.name = name;
    this.title = title || name;
    this.sourceImageParameter = sourceImageParameter;
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
    this.subModules = subModules;
  }

  setParameterDefaultValues = (onlyEmpty = true) => {
    this.parameters.forEach(aParameter => {
      if (
        aParameter &&
        (aParameter.isEmpty || !onlyEmpty) &&
        aParameter.parameter &&
        aParameter.parameter.defaultValue
      ) {
        aParameter.value = aParameter.parameter.defaultValue;
      }
    });
  }

  /**
   * @returns {Analysis}
   */
  get analysis () {
    if (!this.pipeline) {
      return undefined;
    }
    return this.pipeline.analysis;
  }

  /**
   * @returns {string[]}
   */
  get channels () {
    if (!this.pipeline) {
      return [];
    }
    return this.pipeline.channels;
  }

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

  /**
   * @returns {ModuleParameterValue[]}
   */
  get fileInputParameters () {
    return (this.parameters || [])
      .filter(
        (parameter) => parameter.parameter && parameter.parameter.type === AnalysisTypes.file
      );
  }

  get outputParameters () {
    return this.outputsConfiguration.map((outputConfiguration) => {
      const {
        name,
        criteria
      } = outputConfiguration;
      const parameterConfiguration = this.getParameterConfiguration(name);
      if (parameterConfiguration && criteria(this)) {
        return parameterConfiguration;
      }
      return undefined;
    }).filter(Boolean);
  }

  get sourceImage () {
    if (!this.pipeline || !this.sourceImageParameter) {
      return undefined;
    }
    return this.getParameterValue(this.sourceImageParameter);
  }

  get displayName () {
    if (this.predefined || !this.pipeline) {
      return this.title;
    }
    const idx = this.order + 1;
    if (idx === 0) {
      return this.title;
    }
    return `#${idx} ${this.title}`;
  }

  get payload () {
    return this.getPayload();
  }

  initialize () {
    this.parametersConfigurations.push(new BooleanParameter({
      name: 'advanced',
      title: 'Show advanced settings',
      value: false,
      exportParameter: false
    }));
  }

  /**
   * @param {ModuleParameter} parameter
   */
  registerParameters (...parameter) {
    this.parametersConfigurations.push(...parameter);
  }

  /**
   * @param name
   * @returns {ModuleParameter}
   */
  getParameterConfiguration (name) {
    return this.parametersConfigurations.find(config => config.name === name) ||
      this.parametersConfigurations.find(config => config.parameterName === name);
  }

  /**
   * @param {string} name
   * @returns {ModuleParameterValue}
   */
  getParameterValueObject (name) {
    return this.parameters.find(searchParameterByName(name)) ||
      this.parameters.find(searchParameterByParameterName(name));
  }

  getParameterValue (name, ...modifier) {
    if (/^uuid$/i.test(name)) {
      return this.uuid;
    }
    if (/^id$/i.test(name)) {
      return this.id;
    }
    const parameterValue = this.getParameterValueObject(name);
    if (parameterValue) {
      return parameterValue.getValue(...modifier);
    } else {
      console.warn(`parameter not found: ${name}`);
    }
    return undefined;
  }

  getBooleanParameterValue (name) {
    return ['true', 'on', 'yes'].includes(`${this.getParameterValue(name)}`.toLowerCase());
  }

  setParameterValue (name, value) {
    const parameterValue = this.parameters.find(searchParameterByName(name)) ||
      this.parameters.find(searchParameterByParameterName(name));
    if (parameterValue) {
      parameterValue.applyValue(value);
    } else {
      console.log(`module["${this.name}"].setParameterValue(${name},`, value, '): NOT FOUND');
    }
  }

  getAllVisibleParameters () {
    return this.parameters.filter(parameter => parameter.parameter.visible);
  }

  update () {}

  /**
   * @returns {AnalysisModule[]}
   */
  get modules () {
    if (this.pipeline) {
      return (this.pipeline.modules || []);
    }
    return [];
  }

  get order () {
    return this.modules.indexOf(this);
  }

  /**
   * @returns {AnalysisModule[]}
   */
  get modulesBefore () {
    const order = this.order;
    if (order < 0) {
      return [];
    }
    return this.modules.slice(0, order);
  }
  /**
   * @returns {AnalysisModule[]}
   */
  get modulesAfter () {
    const order = this.order;
    if (order < 0) {
      return [];
    }
    return this.modules.slice(order + 1);
  }

  get isFirst () {
    return this.modulesBefore.filter(cpModule => !cpModule.hidden).length === 0;
  }

  get isLast () {
    return this.modulesAfter.filter(cpModule => !cpModule.hidden).length === 0;
  }

  moveUp () {
    if (this.pipeline && !this.isFirst) {
      const modules = collapseModules([...this.pipeline.modules]);
      const index = modules.indexOf(this);
      if (index > 0) {
        modules.splice(index, 1);
        modules.splice(index - 1, 0, this);
        this.pipeline.modules = expandModules(modules);
        this.pipeline.changed = true;
        if (this.analysis) {
          this.analysis.analysisRequested = true;
        }
      }
    }
  }

  moveDown () {
    if (this.pipeline && !this.isLast) {
      const modules = collapseModules([...this.pipeline.modules]);
      const index = modules.indexOf(this);
      if (index < modules.length - 1) {
        modules.splice(index, 1);
        modules.splice(index + 1, 0, this);
        this.pipeline.modules = expandModules(modules);
        this.pipeline.changed = true;
        if (this.analysis) {
          this.analysis.analysisRequested = true;
        }
      }
    }
  }

  remove () {
    if (this.pipeline) {
      const modules = collapseModules([...this.pipeline.modules]);
      const index = modules.indexOf(this);
      modules.splice(index, 1);
      this.pipeline.modules = expandModules(modules);
      this.pipeline.changed = true;
      if (this.analysis) {
        this.analysis.analysisRequested = true;
      }
    }
  }

  /**
   * Returns module's parameters payload
   * @param {boolean} [validate=false]
   * @returns {*}
   */
  getPayload = (validate = false) => {
    return this.parameters
      .filter((parameter) =>
        !/^advanced$/i.test(parameter.parameter.name) && !parameter.parameter.local
      )
      .reduce((payload, item) => ({
        ...payload,
        ...item.getPayload(validate)
      }), {...this.extraParameters});
  }

  exportModule (json = false) {
    const properties = [
      this.hidden ? 'hidden' : false,
      this.composed ? 'composed' : false
    ].filter(Boolean);
    let propertiesString;
    if (properties.length > 0) {
      propertiesString = `[${properties.join('|')}]`;
    }
    const parametersPayload = this.parameters
      .filter(parameterValue => parameterValue.parameter &&
        parameterValue.parameter.exportParameter
      )
      .map(parameterValue => parameterValue.exportParameterValue())
      .filter(parameterValue => parameterValue && parameterValue.length);
    if (json) {
      return {
        name: this.name,
        hidden: this.hidden,
        composed: this.composed,
        parameters: parametersPayload
      };
    }
    return [
      [
        this.name,
        propertiesString
      ].filter(o => o && o.length > 0).join(':'),
      ...parametersPayload
        .map(parameterValue => `\t${parameterValue}`)
    ].join('\n');
  }
  /**
   * @param {string} name
   * @param {object} [values]
   * @param {{id: string?,pipeline: AnalysisPipeline?}} [options]
   * @returns {AnalysisModule|undefined}
   */
  static createModule (name, values, options) {
    const {
      id,
      pipeline,
      ...restOptions
    } = options || {};
    const moduleConfiguration = allModules.find(aModule => aModule.name === name);
    if (moduleConfiguration) {
      const cpModule = new AnalysisModule(
        pipeline,
        {
          id,
          ...restOptions,
          ...moduleConfiguration
        });
      Object.entries(values || {}).forEach(([parameter, value]) => {
        try {
          cpModule.setParameterValue(parameter, value);
        } catch (e) {
          console.warn(`Error setting parameter value. ${parameter} =`, value);
        }
      });
      return cpModule;
    }
    return undefined;
  }

  /**
   * @param {string} moduleContent
   * @param {{pipeline: AnalysisPipeline?, throwError: boolean?}} options
   * @returns {undefined}
   */
  static importModule (moduleContent, options = {}) {
    if (!moduleContent) {
      return undefined;
    }
    const {
      throwError = false,
      pipeline
    } = options || {};
    try {
      const [
        header,
        ...parametersConfig
      ] = moduleContent.split(/[\r]?\n/);

      const processOption = option => {
        const [optionName, ...optionValue] = (option || '').split(':');
        return {
          [optionName]: optionValue.length > 0 ? optionValue.join(':') : true
        };
      };
      const [name, ...optionsParts] = header.split(':');
      let options = {};
      const e = /^\[(.*)]$/.exec(optionsParts.join(':'));
      if (e && e[1]) {
        options = splitStringWithBrackets(e[1], '|')
          .map(processOption)
          .reduce((r, c) => ({...r, ...c}), {});
      }
      const formatParameter = rawParameter => {
        const pe = /^\s*(.+)\s*$/.exec(rawParameter);
        if (pe && pe[1]) {
          return pe[1];
        }
        return rawParameter;
      };
      const parseValue = (value) => {
        try {
          return JSON.parse(value);
        } catch (_) {
          return value;
        }
      };
      const parameters = parametersConfig
        .map(formatParameter)
        .filter(Boolean)
        .map(processOption)
        .map(aParameter => Object.entries(aParameter || {}))
        .reduce((r, c) => ([...r, ...c]), [])
        .map(([name, value]) => ({
          [name]: parseValue(value)
        }))
        .reduce((r, c) => ({...r, ...c}), {});
      return AnalysisModule.createModule(name, parameters, {...options, pipeline});
    } catch (error) {
      console.warn(`Error importing module: ${error.message}`);
      if (throwError) {
        throw error;
      }
    }
    return undefined;
  }

  getMissingInputs = () => this.fileInputParameters
    .map((fileParameter) => {
      const values = (fileParameter.parameter.values || []).map((value) => value.value);
      const value = fileParameter.getValue();
      if (value && !values.includes(value)) {
        return value;
      }
      return undefined;
    })
    .filter(Boolean);

  correctInputsForModules = (correctedInputs = {}) => {
    let corrected = false;
    this.fileInputParameters
      .forEach((fileParameter) => {
        const values = (fileParameter.parameter.values || [])
          .map((value) => value.value);
        const value = fileParameter.getValue();
        if (value && !values.includes(value)) {
          corrected = true;
          // apply corrected value (if exists) or undefined
          fileParameter.applyValue(correctedInputs[value]);
        }
      });
    return corrected;
  };
}

export {
  AnalysisModule,
  AnalysisModuleOutput
};
