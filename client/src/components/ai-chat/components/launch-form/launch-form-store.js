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

import {observable, action, computed} from 'mobx';
import {getLaunchMode, getToolConfiguration, LAUNCH_MODES} from './utils';

class LaunchFormStore {
  @observable environment = {
    disk: '',
    dockerImage: '',
    instanceType: '',
    cmd: '',
    isSpot: false
  };
  @observable parameters = {};

  @observable runLaunched = false;
  @observable mode = undefined; // tool || pipeline

  @observable _toolInfo = undefined;
  @observable _configuration = undefined;
  @observable _pipelineConfiguration = undefined;
  @observable _toolVersion = undefined;
  @observable _versions = undefined;
  @observable _pipeline = undefined;
  @observable _pipelineVersion = undefined;

  @observable _error = '';
  @observable _pending = true;

  @computed get error () {
    return this._error;
  }

  @computed get pending () {
    return this._pending;
  }

  @computed get toolInfo () {
    return this._toolInfo;
  }

  @computed get configuration () {
    return this._configuration;
  }

  @computed get toolVersion () {
    return this._toolVersion;
  }

  @computed get versions () {
    return this._versions?.versions || [];
  }

  @computed get pipeline () {
    return this._pipeline;
  }

  @computed get pipelineVersion () {
    return this._pipelineVersion;
  }

  @computed get pipelineConfiguration () {
    return this._pipelineConfiguration;
  }

  set error (error) {
    this._error = error;
  }

  @action
  setRunLaunched (value) {
    this.runLaunched = value;
  }

  @action updateField (key, value) {
    if (this.hasOwnProperty(key)) {
      this[key] = value;
    } else {
      console.error(`[LaunchFormStore] Field '${key}' does not exist`);
    }
  }

  @action updateParameter (key, value) {
    if (this.parameters[key]) {
      this.parameters[key].value = value;
    } else {
      console.error(`[LaunchFormStore] Parameter '${key}' not found`);
    }
  }

  @action
  initializeData ({
    data,
    toolInfo,
    toolVersion,
    pipelineVersion,
    pipelineConfiguration,
    pipeline,
    versions
  }) {
    if (!data) {
      return;
    }
    const {parameters} = data || {};
    this.mode = getLaunchMode(data);
    if (this.mode === LAUNCH_MODES.tool) {
      const {configuration = {}} = getToolConfiguration(
        data.configuration,
        toolVersion
      ) || {};
      this._configuration = configuration;
    }
    console.log('INITIALIZE', {
      data,
      toolInfo,
      toolVersion,
      versions
    });
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
    if (parameters && typeof parameters === 'object') {
      this.parameters = parameters || {};
    }
    this._pending = false;
  }
}

export default LaunchFormStore;
