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

import {action, computed, observable, observe} from 'mobx';
import {NamesAndTypes} from '../modules';
import {HCSSourceFile} from '../common/analysis-file';
import {
  findJobWithDockerImage,
  launchJobWithDockerImage,
  waitForJobToBeInitialized
} from './job-utilities';
import AnalysisApi from './analysis-api';

class Analysis {
  /**
   * @type {NamesAndTypes}
   */
  @observable namesAndTypes;
  /**
   * @type {AnalysisModule[]}
   */
  @observable modules = [];
  @observable error;
  @observable pending;
  @observable analysing;
  @observable analysisAPI;
  @observable pipelineId;
  @observable status;
  @observable userInfo;
  @observable changed = false;
  @observable _active = false;
  hcsImageViewer;
  _analysisRequested = false;

  @computed
  get analysisRequested () {
    return this._analysisRequested;
  }

  set analysisRequested (requested) {
    if (this._analysisRequested !== requested) {
      this._analysisRequested = requested;
    }
    this.scheduleAnalyzeCheck();
  }

  @computed
  get analysisResults () {
    return this.modules.reduce((results, module) => {
      if (module.executionResults && module.executionResults.length) {
        return [...results, ...module.executionResults];
      }
      return [];
    }, []);
  }

  @computed
  get analysisResult () {
    return this.analysisResults.slice().pop();
  }

  constructor (hcsImageViewer) {
    this.hcsImageViewer = hcsImageViewer;
    observe(this, 'analysisResult', () => {
      const result = this.analysisResult;
      if (this.hcsImageViewer) {
        this.hcsImageViewer.setOverlayImages(result ? [result] : []);
      }
    });
    this.checkNeedAnalyze();
  }

  destroy = () => {
    clearTimeout(this.analysisTimeout);
  };

  checkNeedAnalyze = () => {
    if (this.analysisRequested && !this.analysing && !!this.pipelineId) {
      (this.run)();
    }
    this.scheduleAnalyzeCheck();
  };

  scheduleAnalyzeCheck = () => {
    clearTimeout(this.analysisTimeout);
    const INTERVAL_MS = 2000;// 2 seconds
    this.analysisTimeout = setTimeout(this.checkNeedAnalyze, INTERVAL_MS);
  };

  /**
   * Describes if the analysis is active (i.e., opened by user)
   * @returns {boolean}
   */
  @computed
  get active () {
    return this._active;
  }

  @computed
  get available () {
    return this.namesAndTypes && this.namesAndTypes.available;
  }

  @computed
  get ready () {
    return this.available && this.analysisAPI && this.pipelineId;
  }

  @action
  activate = async (active = true) => {
    this._active = active;
    if (active) {
      await this.buildPipeline();
      await this.attachFileToPipeline();
    }
  };

  @action
  deactivate = () => {
    this._active = false;
  };

  @action
  wrapAction = async (action, throwError = false) => {
    if (typeof action !== 'function') {
      return;
    }
    const previousStatus = this.status;
    const previousPending = this.pending;
    let actionError;
    let actionResult;
    try {
      if (typeof action.then === 'function') {
        actionResult = await action();
      } else {
        actionResult = action();
      }
    } catch (error) {
      this.error = error;
      actionError = error;
      console.warn(error);
    } finally {
      this.pending = previousPending;
      this.status = previousStatus;
    }
    if (throwError && actionError) {
      throw new Error(actionError);
    }
    return actionResult;
  };

  @action
  buildPipeline = async () => {
    if (!this.active) {
      return;
    }
    if (this.pipelineId) {
      return this.pipelineId;
    }
    try {
      this.status = 'Building CellProfiler pipeline...';
      this.pending = true;
      this.error = undefined;
      await this.wrapAction(this.acquireJob, true);
      if (!this.analysisAPI) {
        throw new Error('HCS Analysis API could not be initialized');
      }
      const {sourceDirectory: uuid} = this.namesAndTypes.sourceFile || {};
      if (!uuid) {
        throw new Error('MeasurementUUID is not defined');
      }
      this.status = `Building CellProfiler pipeline ${uuid}`;
      const pipeline = await this.analysisAPI.buildPipeline(uuid);
      this.pipelineId = pipeline ? pipeline.pipelineId : undefined;
      if (!this.pipelineId) {
        throw new Error('Error creating pipeline: pipelineId is not defined');
      }
      this.status = `CellProfiler pipeline: #${this.pipelineId}`;
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
    }
  };

  @action
  attachFileToPipeline = async () => {
    if (!this.active || !this.analysisAPI) {
      return;
    }
    try {
      this.status = 'Attaching image to CellProfiler pipeline...';
      this.pending = true;
      this.error = undefined;
      let channel;
      if (this.namesAndTypes && this.namesAndTypes.channel) {
        channel = this.namesAndTypes.channel;
      }
      const {
        well = {x: 0, y: 0},
        image,
        time,
        z
      } = this.namesAndTypes.sourceFile || {};
      const {
        x: column = 0,
        y: row = 0
      } = well;
      const hcsImage = {
        x: row,
        y: column,
        z,
        timepoint: time,
        fieldId: image,
        channel
      };
      await this.analysisAPI.attachFiles(this.pipelineId, hcsImage);
      this.status = `Image attached to pipeline: #${this.pipelineId}`;
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
    }
    if (this.modules.length > 0) {
      this.analysisRequested = true;
    }
  };

  /**
   * @param {AnalysisModule} module
   * @returns {Promise<void>}
   */
  @action
  removeModule = async (module) => {
    if (
      !this.active ||
      !this.pipelineId ||
      !this.analysisAPI ||
      !module ||
      !module.syncInfo
    ) {
      return;
    }
    try {
      this.status = `Removing module ${module.displayName} #${module.syncInfo.moduleId}...`;
      await this.analysisAPI.removeModule(this.pipelineId, module.syncInfo.moduleIndex);
      this.status = `Module ${module.displayName} #${module.syncInfo.moduleId} removed`;
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
    }
  };

  /**
   * @param {AnalysisModule} module
   * @param {{clear: boolean?, skipModuleIdCheck: boolean?}} [options]
   * @returns {Promise<void>}
   */
  @action
  updateModule = async (module, options = {}) => {
    const {
      clear = false,
      skipModuleIdCheck = false
    } = options;
    if (!this.active || !this.pipelineId || !this.analysisAPI || !module) {
      return undefined;
    }
    let moduleInfo;
    try {
      const exists = !!module.syncInfo;
      const label = exists ? 'Updating' : 'Creating';
      const labelDone = exists ? 'updated' : 'created';
      this.status = `${label} module ${module.displayName}...`;
      this.pending = true;
      this.error = undefined;
      if (exists) {
        await this.wrapAction(
          () => this.analysisAPI.removeModule(this.pipelineId, module.syncInfo.moduleIndex),
          true
        );
      }
      const newModuleId = module.order + 1;
      await this.analysisAPI.createModule(
        this.pipelineId,
        {
          moduleName: module.name,
          moduleId: newModuleId,
          parameters: clear ? {} : module.getPayload()
        }
      );
      if (!skipModuleIdCheck) {
        moduleInfo = await this.analysisAPI.getPipelineModuleAtIndex(
          this.pipelineId,
          module.order
        );
        if (!moduleInfo || module.name !== moduleInfo.name) {
          throw new Error(`Error updating module ${module.name}`);
        }
        module.syncInfo = {
          moduleId: moduleInfo.id,
          moduleIndex: newModuleId
        };
        this.status = `Module ${module.displayName} ${labelDone}: #${module.syncInfo.moduleId}`;
      } else {
        this.status = `Module ${module.displayName} ${labelDone}`;
      }
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
    }
    return moduleInfo;
  };

  @action
  updateModules = async () => {
    if (!this.active || !this.pipelineId || !this.analysisAPI) {
      return;
    }
    try {
      this.pending = true;
      this.error = undefined;
      this.status = 'Updating modules...';
      this.changed = false;
      let pipeline = await this.analysisAPI.getPipeline(this.pipelineId);
      if (!pipeline) {
        throw new Error('Error fetching pipeline info');
      }
      const structure = (pipeline.modules || []);
      let changed = false;
      let changedIndex = this.modules.length;
      for (let idx = 0; idx < this.modules.length; idx++) {
        const module = this.modules[idx];
        if (
          !module.syncInfo ||
          !module.syncInfo.moduleId ||
          !structure[idx] ||
          structure[idx].id !== module.syncInfo.moduleId ||
          module.changed
        ) {
          changed = true;
          changedIndex = idx;
          break;
        }
      }
      this.modules.slice(changedIndex).forEach((module) => {
        module.syncInfo = undefined;
      });
      const modulesToRemoveCount = structure.length - changedIndex;
      const removeModules = async (idx = 0) => {
        if (idx >= modulesToRemoveCount) {
          return;
        }
        await this.analysisAPI.removeModule(this.pipelineId, changedIndex + 1);
        return removeModules(idx + 1);
      };
      await removeModules();
      if (!changed) {
        return;
      }
      /**
       * @type {AnalysisModule[]}
       */
      const updateActions = this.modules.slice(changedIndex);
      const doNext = async (index = 0) => {
        if (index >= updateActions.length) {
          return;
        }
        await this.wrapAction(() => this.updateModule(
          updateActions[index],
          {skipModuleIdCheck: true}
        ), true);
        updateActions[index].changed = false;
        return doNext(index + 1);
      };
      await doNext();
      pipeline = await this.analysisAPI.getPipeline(this.pipelineId);
      const {modules = []} = pipeline || {};
      modules.forEach((module, idx) => {
        if (this.modules[idx]) {
          this.modules[idx].syncInfo = {
            moduleIndex: idx,
            moduleId: module.id
          };
        }
      });
      this.status = 'Modules updated';
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
    }
  };

  @action
  fetchStatus = async () => {
    try {
      this.pending = true;
      this.error = undefined;
      this.status = 'Fetching analysis status...';
      const pipelineStatus = await this.analysisAPI.getPipelineStatus(this.pipelineId);
      const {
        status = 'unknown'
      } = pipelineStatus || {};
      this.status = `Analysis status: ${status}`;
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
    }
  }

  @action
  fetchStatusUntilDone = async () => {
    this.pending = true;
    const FETCH_STATUS_INTERVAL_MS = 100;
    const info = await this.analysisAPI.getPipeline(this.pipelineId);
    if (!info) {
      throw new Error('Error fetching analysis status');
    }
    const {state = 'unknown'} = info;
    this.status = `Analysis status: ${state.toLowerCase()}`;
    this.pending = false;
    if (/^config/i.test(state)) {
      throw new Error(`Updating CellProfiler pipeline... Try to re-launch analysis`);
    }
    if (/^running$/i.test(state)) {
      return new Promise((resolve, reject) => {
        setTimeout(
          () => this.fetchStatusUntilDone().then(resolve).catch(reject),
          FETCH_STATUS_INTERVAL_MS
        );
      });
    }
    return info;
  }

  runPipeline = async (attempt = 0) => {
    if (!this.pipelineId) {
      return;
    }
    const MAX_ATTEMPTS = 5;
    const ATTEMPT_INTERVAL_MS = 100;
    if (attempt > MAX_ATTEMPTS) {
      throw new Error(`Error launching analysis pipeline: max attempts exceeded`);
    }
    await this.analysisAPI.runPipeline(this.pipelineId);
    const pipeline = await this.analysisAPI.getPipeline(this.pipelineId);
    const {state} = pipeline;
    if (/^config/i.test(state)) {
      const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
      await wait(ATTEMPT_INTERVAL_MS);
      return this.runPipeline(attempt + 1);
    }
  };

  @action
  run = async () => {
    if (!this.active || !this.pipelineId || !this.analysisAPI) {
      return;
    }
    try {
      this.analysisRequested = false;
      this.analysing = true;
      this.pending = true;
      this.error = undefined;
      this.status = 'Running analysis...';
      await this.wrapAction(this.updateModules, true);
      await this.runPipeline();
      const pipeline = await this.wrapAction(
        this.fetchStatusUntilDone,
        true
      );
      if (!pipeline) {
        throw new Error(`Error fetching analysis status`);
      }
      const {
        state,
        message,
        modules = []
      } = pipeline;
      await Promise.all(
        modules.map((module, idx) => {
          if (this.modules[idx]) {
            return this.modules[idx].setExecutionResults(module);
          }
          return Promise.resolve();
        })
      );
      this.status = `Analysis: ${(state || 'unknown').toLowerCase()}`;
      if (!/^finished$/i.test(state) && message) {
        throw new Error(message);
      }
    } catch (error) {
      this.error = error.message;
      this.modules.forEach(module => module.clearExecutionResults());
      console.warn(error);
    } finally {
      this.pending = false;
      this.analysing = false;
    }
  };

  acquireJob = async () => {
    if (this.analysisAPI || !this.active) {
      return this.analysisAPI;
    }
    this.status = 'Acquiring CellProfiler job...';
    this.pending = true;
    let job = await findJobWithDockerImage(this.userInfo);
    if (!job) {
      job = await launchJobWithDockerImage();
    }
    if (!job) {
      throw new Error('CellProfiler job cannot be initialized');
    }
    this.status = 'Acquiring CellProfiler job endpoint...';
    const jobEndpoint = await waitForJobToBeInitialized(job);
    this.analysisAPI = new AnalysisApi(jobEndpoint);
    this.status = `CellProfiler job: #${job.id} (${this.analysisAPI.endpoint})`;
    this.pending = false;
    return this.analysisAPI;
  }

  /**
   * @param {HCSSourceFileOptions} hcsSourceFile
   */
  changeFile (hcsSourceFile) {
    const onChange = (fileChanged) => {
      this.pipelineId = undefined;
      (async () => {
        await this.buildPipeline();
        if (fileChanged) {
          this.modules.forEach(module => module.clearExecutionResults());
          await this.attachFileToPipeline();
        }
      })();
    };
    let changed = false;
    if (!this.namesAndTypes) {
      this.namesAndTypes = new NamesAndTypes(this, hcsSourceFile);
      changed = true;
      observe(this.namesAndTypes, 'channel', () => {
        onChange(true);
      });
    } else {
      changed = this.namesAndTypes.changeFile(hcsSourceFile);
    }
    if (HCSSourceFile.check(hcsSourceFile) && changed) {
      onChange(changed);
    }
  }

  /**
   * @param {function} AnalysisModuleClass
   * @returns {AnalysisModule}
   */
  @action
  add = async (AnalysisModuleClass) => {
    const newModule = new AnalysisModuleClass(this);
    this.modules.push(newModule);
    this.modules.push(...newModule.hiddenModules);
    const updateModuleFn = (module) => new Promise((resolve, reject) => {
      this.updateModule(module, {clear: true})
        .then(info => {
          module.applySettings(info.settings);
          resolve();
        })
        .catch(reject);
    });
    try {
      this.pending = true;
      this.error = undefined;
      await Promise.all([newModule, ...newModule.hiddenModules].map(updateModuleFn));
      newModule.update();
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
      this.analysisRequested = true;
    }
    return newModule;
  };
}

export {Analysis};
