/*
 * Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {makeAutoObservable} from 'mobx';
import {getLaunchMode, getToolConfiguration, LAUNCH_MODES} from './utils';
import {
  CP_CAP_AUTOSCALE,
  CP_CAP_AUTOSCALE_WORKERS,
  CP_CAP_KUBE,
  CP_CAP_SGE,
  CP_CAP_SLURM,
  CP_CAP_SPARK,
} from '../../../pipelines/launch/form/utilities/parameters';

class LaunchFormStore {
  environment = {
    disk: '',
    dockerImage: '',
    instanceType: '',
    cmd: '',
    isSpot: false,
    cluster: false,
    autoScaled: false,
    nodeCount: 0,
    defaultNodeCount: 0,
    sge: false,
    slurm: false,
    spark: false,
    kube: false,
  };

  parameters = {};
  runLaunched = false;
  mode = undefined; // tool || pipeline

  _toolInfo = undefined;
  _configuration = undefined;
  _pipelineConfiguration = undefined;
  _toolVersion = undefined;
  _versions = undefined;
  _pipeline = undefined;
  _pipelineVersion = undefined;
  _error = '';
  _pending = true;

  constructor() {
    makeAutoObservable(this);
  }

  get error() {
    return this._error;
  }

  get pending() {
    return this._pending;
  }

  get toolInfo() {
    return this._toolInfo;
  }

  get configuration() {
    return this._configuration;
  }

  get toolVersion() {
    return this._toolVersion;
  }

  get versions() {
    return this._versions?.versions || [];
  }

  get pipeline() {
    return this._pipeline;
  }

  get pipelineVersion() {
    return this._pipelineVersion;
  }

  get pipelineConfiguration() {
    return this._pipelineConfiguration;
  }

  set error(error) {
    this._error = error;
  }

  setRunLaunched(value) {
    this.runLaunched = value;
  }

  updateField(key, value) {
    if (Object.hasOwn(this, key)) {
      this[key] = value;
    } else {
      console.error(`[LaunchFormStore] Field '${key}' does not exist`);
    }
  }

  updateParameter(key, value) {
    if (this.parameters[key]) {
      this.parameters[key].value = value;
    } else {
      console.error(`[LaunchFormStore] Parameter '${key}' not found`);
    }
  }

  initializeData({
    data,
    toolInfo,
    toolVersion,
    pipelineVersion,
    pipelineConfiguration,
    pipeline,
    versions,
  }) {
    if (!data) {
      return;
    }
    const {parameters} = data || {};
    const getParameterValue = (parameter) => {
      if (parameters) {
        const p = parameters[parameter];
        if (p) {
          return p.value;
        }
      }
      return undefined;
    };
    const getParameterEnabled = (parameter) => {
      const value = getParameterValue(parameter);
      if (value !== undefined) {
        return `${value}`.toLowerCase() === 'true';
      }
      return false;
    };
    const getParameterIntValue = (parameter) => {
      const value = getParameterValue(parameter);
      if (value !== undefined) {
        if (typeof value === 'number') {
          return value;
        }
        if (!Number.isNaN(Number(value))) {
          return Number(value);
        }
      }
      return undefined;
    };
    this.mode = getLaunchMode(data);
    if (this.mode === LAUNCH_MODES.tool) {
      const {configuration = {}} = getToolConfiguration(data.configuration, toolVersion) || {};
      this._configuration = configuration;
    }
    this._toolInfo = toolInfo;
    this._toolVersion = toolVersion;
    this._pipelineConfiguration = pipelineConfiguration;
    this._versions = versions;
    this._pipeline = pipeline;
    this._pipelineVersion = pipelineVersion;
    this.environment.disk = data.disk;
    this.environment.isSpot = data.is_spot;
    this.environment.dockerImage = data.dockerImage;
    this.environment.instanceType = data.instanceType;
    this.environment.cmd = data.cmd;
    const nodeCountValue = data.node_count || 0;
    const autoScaled = getParameterEnabled(CP_CAP_AUTOSCALE);
    const autoScaledWorkers = getParameterIntValue(CP_CAP_AUTOSCALE_WORKERS);
    const cluster = nodeCountValue > 0 || autoScaled;
    let nodeCount = nodeCountValue;
    let defaultNodesCount = 0;
    if (autoScaled) {
      defaultNodesCount = nodeCountValue;
      nodeCount = autoScaledWorkers;
    }
    this.environment.cluster = cluster;
    this.environment.autoScaled = autoScaled;
    this.environment.nodeCount = nodeCount;
    this.environment.defaultNodesCount = defaultNodesCount;
    this.environment.sge = cluster && getParameterEnabled(CP_CAP_SGE);
    this.environment.slurm = cluster && getParameterEnabled(CP_CAP_SLURM);
    this.environment.spark = cluster && getParameterEnabled(CP_CAP_SPARK);
    this.environment.kube = cluster && getParameterEnabled(CP_CAP_KUBE);
    if (parameters && typeof parameters === 'object') {
      this.parameters = parameters || {};
    }
    this._pending = false;
  }
}

export default LaunchFormStore;
