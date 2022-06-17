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
import {AnalysisModule} from '../modules/base';
import analysisPipeline from './analysis-pipeline';

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
  @observable analysisResults = [];
  @observable showAnalysisResults = true;
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
    if (requested) {
      this.scheduleAnalyzeCheck();
    }
  }

  @computed
  get hasAnalysisResult () {
    return this.analysisResults.length > 0;
  }

  @computed
  get analysisResult () {
    if (!this.showAnalysisResults) {
      return undefined;
    }
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
    return;
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
      const names = this.namesAndTypes.outputs.map(output => output.name);
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
      const files = names.map((name, index) => ({
        x: row,
        y: column,
        z,
        timepoint: time,
        fieldId: image,
        channel: index + 1,
        channelName: name
      }));
      await this.analysisAPI.attachFiles(this.pipelineId, ...files);
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
      this.analysisResults = await analysisPipeline(
        this.analysisAPI,
        this.pipelineId,
        this.modules
      );
      this.showAnalysisResults = true;
      this.status = 'Analysis done';
    } catch (error) {
      this.error = error.message;
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
      this.analysisResults = [];
      (async () => {
        await this.buildPipeline();
        if (fileChanged) {
          await this.attachFileToPipeline();
        }
      })();
    };
    let changed = false;
    if (!this.namesAndTypes) {
      this.namesAndTypes = new NamesAndTypes(this, hcsSourceFile);
      changed = true;
    } else {
      changed = this.namesAndTypes.changeFile(hcsSourceFile);
    }
    if (HCSSourceFile.check(hcsSourceFile) && changed) {
      onChange(changed);
    }
  }

  /**
   * @param {object} analysisModuleConfiguration
   * @returns {AnalysisModule}
   */
  @action
  add = async (analysisModuleConfiguration) => {
    const newModule = new AnalysisModule(this, analysisModuleConfiguration);
    this.modules.push(newModule);
    try {
      this.pending = true;
      this.error = undefined;
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
