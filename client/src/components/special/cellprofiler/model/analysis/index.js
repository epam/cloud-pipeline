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
import {NamesAndTypes} from '../modules/names-and-types';
import {HCSSourceFile} from '../common/analysis-file';
import {
  findJobWithDockerImage,
  getAnalysisEndpointSetting,
  waitForJobToBeInitialized
} from './job-utilities';
import {AnalysisPipeline} from './pipeline';
import AnalysisApi from './analysis-api';
import runAnalysisPipeline, {getPipelineModules} from './analysis-pipeline-utilities';
import {savePipeline} from './analysis-pipeline-management';
import {submitBatchAnalysis} from './batch';
import {findSimilarAnalysis} from './similar-analysis';
import PhysicalSize from './physical-size';
import moment from 'moment-timezone';

const AUTOUPDATE = false;

class Analysis {
  static Events = {
    analysisDone: 'analysis done'
  };
  /**
   * @type {NamesAndTypes}
   */
  @observable namesAndTypes;
  /**
   * @type {AnalysisPipeline}
   */
  @observable pipeline;
  @observable physicalSize = new PhysicalSize();
  @observable error;
  @observable pending;
  @observable analysing;
  @observable analysisAPI;
  @observable pipelineId;
  @observable status;
  @observable userInfo;
  @observable changed = false;
  @observable alias;
  /**
   * @type {AnalysisOutputResult[]}
   */
  @observable analysisResults = [];
  analysisCache = [];
  @observable sourceFileChanged = true;
  @observable showAnalysisResults = true;
  @observable _hcsImageViewer;
  _analysisRequested = false;
  eventListeners = [];
  storageId;
  path;
  analysisDate;

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

  /**
   * @returns {AnalysisOutputResult[]}
   */
  @computed
  get defineResultsOutputs () {
    return this.analysisResults.filter(o => o.analysisOutput);
  }

  /**
   * @returns {AnalysisOutputResult[]}
   */
  @computed
  get analysisOutputImages () {
    return this.analysisResults.filter(o => !o.table && !o.xlxs && !o.analysisOutput);
  }

  @computed
  get modules () {
    if (!this.pipeline) {
      return [];
    }
    return [...this.pipeline.modules, this.pipeline.defineResults];
  }

  @computed
  get isEmpty () {
    if (!this.pipeline) {
      return [];
    }
    return this.pipeline.modules.length === 0;
  }

  @computed
  get batch () {
    return this.namesAndTypes && this.namesAndTypes.multipleFields;
  }

  @computed
  get hcsImageViewer () {
    return this._hcsImageViewer;
  }

  set hcsImageViewer (aViewer) {
    this._hcsImageViewer = aViewer;
    if (this.pipeline && this._hcsImageViewer) {
      (this.pipeline.graphicsOutput.renderOutlines)(this._hcsImageViewer);
    }
  }

  constructor (hcsImageViewer) {
    this.pipeline = new AnalysisPipeline(this);
    this.hcsImageViewer = hcsImageViewer;
    this.checkNeedAnalyze();
  }

  addEventListener (event, listener) {
    this.eventListeners.push({event, listener});
  }

  removeEventListeners (event, listener) {
    if (!event && !listener) {
      this.eventListeners = [];
    } else if (!listener) {
      this.eventListeners = this.eventListeners.filter(o => o.event === event);
    } else if (!event) {
      this.eventListeners = this.eventListeners.filter(o => o.listener === listener);
    } else {
      this.eventListeners = this.eventListeners
        .filter(o => o.event === event && o.listener === listener);
    }
  }

  fireEvent (eventName) {
    this.eventListeners
      .filter(o => o.event === eventName)
      .map(o => o.listener)
      .forEach(eventListener => {
        eventListener();
      });
  }

  setCurrentUser = (user) => {
    this.userInfo = user;
    if (this.userInfo) {
      (async () => {
        await this.userInfo.fetchIfNeededOrWait();
        if (this.pipeline.isNew && this.userInfo.loaded) {
          this.pipeline.author = (this.userInfo.value || {}).userName;
        }
      })();
    }
  }

  setSource = (storageId, path) => {
    this.storageId = storageId;
    this.path = path;
  };

  newPipeline = () => {
    this.pipeline = new AnalysisPipeline(this);
    (async () => {
      await this.userInfo.fetchIfNeededOrWait();
      if (this.userInfo.loaded) {
        this.pipeline.author = (this.userInfo.value || {}).userName;
      }
    })();
    this.changed = true;
    this.analysisRequested = true;
  };

  savePipeline = async (asNew = false) => {
    if (!this.pipeline) {
      return;
    }
    if (!this.pipeline.name) {
      throw new Error('Pipeline name must be specified');
    }
    if (asNew) {
      this.pipeline.path = undefined;
    }
    await this.userInfo.fetchIfNeededOrWait();
    this.pipeline.author = (this.userInfo.value || {}).userName;
    await savePipeline(this.pipeline);
  };

  /**
   * @param {AnalysisPipeline} pipeline
   */
  loadPipeline = (pipeline) => {
    if (!pipeline) {
      return;
    }
    pipeline.analysis = this;
    this.pipeline = pipeline;
    this.status = `Pipeline ${pipeline.name} opened`;
    this.error = undefined;
    this.changed = true;
    this.analysisRequested = true;
    this.pending = false;
  };

  destroy = () => {
    this.removeEventListeners();
    clearTimeout(this.analysisTimeout);
  };

  checkNeedAnalyze = () => {
    if (!AUTOUPDATE) {
      return;
    }
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

  @computed
  get available () {
    return this.namesAndTypes && this.namesAndTypes.available;
  }

  @action
  updatePhysicalSize (unitsInPixel, units) {
    this.physicalSize.update(unitsInPixel, units);
  }

  @action
  authenticate = () => {
    if (this.analysisAPI) {
      return this.analysisAPI.authenticate();
    }
  }

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
      actionResult = await action();
    } catch (error) {
      this.error = error.message;
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
  clearModulesState = () => {
    this.modules.forEach(module => {
      module.pending = false;
      module.done = false;
    });
  }

  @action
  buildPipeline = async () => {
    if (this.pipelineId) {
      return this.pipelineId;
    }
    try {
      this.clearModulesState();
      this.status = 'Building CellProfiler pipeline...';
      this.pending = true;
      this.error = undefined;
      await this.wrapAction(this.acquireJob, true);
      if (!this.analysisAPI) {
        throw new Error('HCS Analysis API could not be initialized');
      }
      const uuid = this.namesAndTypes.sourceDirectory;
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
      this.sourceFileChanged = true;
      this.analysisCache = [];
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
    }
  };

  getInputsPayload = () => {
    if (!this.namesAndTypes) {
      return {
        files: []
      };
    }
    const files = this.namesAndTypes.sourceFiles.map(aFile => ({
      x: aFile.x,
      y: aFile.y,
      z: aFile.z,
      timepoint: aFile.t,
      fieldId: aFile.fieldID,
      channel: aFile.c,
      channelName: aFile.channel
    }));
    const zPlanes = this.namesAndTypes.mergeZPlanes
      ? ([...new Set(files.map(aFile => aFile.z))].sort((a, b) => a - b))
      : undefined;
    return {
      files,
      zPlanes
    };
  }

  @action
  attachFileToPipeline = async () => {
    if (!this.analysisAPI || !this.pipelineId || !this.sourceFileChanged) {
      return;
    }
    try {
      this.clearModulesState();
      this.status = 'Attaching image to CellProfiler pipeline...';
      this.pending = true;
      this.error = undefined;
      await this.analysisAPI.attachFiles(
        this.pipelineId,
        this.getInputsPayload()
      );
      this.sourceFileChanged = false;
      this.analysisCache = [];
      this.status = `Image attached to pipeline: #${this.pipelineId}`;
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
    }
    if (this.pipeline && this.modules.length > 0) {
      this.analysisRequested = true;
    }
  };

  @action
  checkSimilarBatchAnalysis = async () => {
    try {
      this.analysing = true;
      this.pending = true;
      this.error = undefined;
      this.status = 'Checking batch analysis...';
      if (!this.namesAndTypes || !this.namesAndTypes.sourceDirectory) {
        throw new Error('HCS file\'s measurement UUID not specified');
      }
      /**
       * @type {BatchAnalysisSpecification}
       */
      const specification = {
        alias: this.alias,
        storage: this.storageId,
        path: this.path,
        pipeline: this.pipeline.exportPipeline(true),
        measurementUUID: this.namesAndTypes.sourceDirectory,
        inputs: this.getInputsPayload(),
        modules: getPipelineModules(this.modules)
          .map((module, idx) => ({
            moduleName: module.name,
            moduleId: idx + 1,
            parameters: module.getPayload()
          }))
      };
      const similar = await findSimilarAnalysis(specification);
      this.status = 'Batch analysis checked';
      return similar;
    } catch (error) {
      this.error = error.message;
      return [];
    } finally {
      this.pending = false;
      this.analysing = false;
    }
  };

  @action
  runBatch = async () => {
    try {
      this.analysing = true;
      this.pending = true;
      this.error = undefined;
      this.status = 'Submitting batch analysis...';
      if (!this.namesAndTypes || !this.namesAndTypes.sourceDirectory) {
        throw new Error('HCS file\'s measurement UUID not specified');
      }
      /**
       * @type {BatchAnalysisSpecification}
       */
      const specification = {
        alias: this.alias,
        storage: this.storageId,
        path: this.path,
        pipeline: this.pipeline.exportPipeline(true),
        measurementUUID: this.namesAndTypes.sourceDirectory,
        inputs: this.getInputsPayload(),
        modules: getPipelineModules(this.modules)
          .map((module, idx) => ({
            moduleName: module.name,
            moduleId: idx + 1,
            parameters: module.getPayload()
          }))
      };
      const batchAnalysis = await submitBatchAnalysis(
        specification
      );
      this.status = 'Batch analysis submitted';
      return batchAnalysis;
    } catch (error) {
      this.error = error.message;
      return undefined;
    } finally {
      this.pending = false;
      this.analysing = false;
    }
  };

  @action
  run = async (debug = true) => {
    /**
     * @param {{module: AnalysisModule, done: boolean}} options
     */
    const reportModuleStatus = (options) => {
      if (!options) {
        this.modules.forEach(module => {
          module.pending = false;
          module.done = true;
        });
      } else if (!debug) {
        this.modules.forEach(module => {
          module.pending = true;
          module.done = false;
        });
      } else {
        const idx = this.modules.indexOf(options.module);
        if (idx >= 0) {
          this.modules.forEach((module, moduleIdx) => {
            module.pending = module === options.module;
            module.done = idx > moduleIdx;
          });
          options.module.pending = !options.done;
          options.module.done = !!options.done;
        }
      }
    };
    try {
      this.analysisRequested = false;
      this.analysing = true;
      this.pending = true;
      this.error = undefined;
      this.status = 'Running analysis...';
      await this.wrapAction(this.buildPipeline);
      if (!this.pipelineId) {
        throw new Error(this.error || 'Error building analysis pipeline');
      }
      await this.wrapAction(this.attachFileToPipeline);
      if (this.sourceFileChanged) {
        throw new Error(this.error || 'Error attaching images to the analysis pipeline');
      }
      if (!debug) {
        this.modules.forEach(module => {
          module.pending = true;
          module.done = false;
        });
      }
      const {
        results,
        cache
      } = await runAnalysisPipeline(
        this.analysisAPI,
        this.pipelineId,
        this.modules,
        this.analysisCache,
        {
          debug,
          objectsOutput: !this.batch,
          callback: reportModuleStatus
        }
      );
      this.analysisDate = moment.utc().format('YYYY-MM-DD HH:mm:ss.SSS');
      this.analysisResults = results.filter(o => !o.object);
      const objectResults = results.filter(o => o.object);
      const lastOverlay = objectResults.length > 0
        ? undefined
        : this.analysisOutputImages.slice().pop();
      this.pipeline.graphicsOutput.update(
        objectResults,
        lastOverlay,
        this.hcsImageViewer
      );
      this.analysisCache = cache;
      this.showAnalysisResults = true;
      this.status = 'Analysis done';
      reportModuleStatus();
    } catch (error) {
      this.clearModulesState();
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
      this.analysing = false;
      this.fireEvent(Analysis.Events.analysisDone);
    }
  };

  acquireJob = async () => {
    if (this.analysisAPI) {
      return this.analysisAPI;
    }
    this.status = 'Configuration...';
    this.pending = true;
    let endpoint = await getAnalysisEndpointSetting();
    if (endpoint) {
      this.status = 'Testing CellProfiler endpoint availability...';
      const available = await AnalysisApi.check(endpoint);
      if (!available) {
        endpoint = undefined;
      }
    }
    if (!endpoint) {
      this.status = 'Acquiring CellProfiler job...';
      const job = await findJobWithDockerImage(this.userInfo);
      if (!job) {
        throw new Error('CellProfiler job is not found or access denied');
      }
      this.status = 'Acquiring CellProfiler job endpoint...';
      endpoint = await waitForJobToBeInitialized(job);
    }
    this.analysisAPI = new AnalysisApi(endpoint);
    this.status = 'Ready';
    this.pending = false;
    return this.analysisAPI;
  }

  /**
   * @param {HCSSourceFileOptions[]} hcsSourceFiles
   * @param {boolean} [mergeZPlanes=false]
   */
  changeFile (hcsSourceFiles, mergeZPlanes = false) {
    const onChange = () => {
      this.pipelineId = undefined;
      this.analysisResults = [];
      this.modules.forEach(aModule => {
        aModule.pending = false;
        aModule.done = false;
      });
      this.pipeline.graphicsOutput.detachResults(this.hcsImageViewer);
      this.analysisCache = [];
      this.sourceFileChanged = true;
    };
    let changed = false;
    if (!this.namesAndTypes) {
      this.namesAndTypes = new NamesAndTypes(hcsSourceFiles);
      changed = true;
    } else {
      changed = this.namesAndTypes.changeFiles(hcsSourceFiles);
    }
    changed = changed || (this.namesAndTypes.mergeZPlanes !== mergeZPlanes);
    this.namesAndTypes.mergeZPlanes = mergeZPlanes;
    if (HCSSourceFile.check(...hcsSourceFiles) && changed) {
      onChange();
    }
  }

  /**
   * @param {object} analysisModuleConfiguration
   * @returns {AnalysisModule}
   */
  @action
  add = async (analysisModuleConfiguration) => {
    let newModule;
    try {
      this.pending = true;
      this.error = undefined;
      newModule = this.pipeline.add(analysisModuleConfiguration);
    } catch (error) {
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
      this.analysisRequested = true;
    }
    return newModule;
  };

  /**
   * @param {AnalysisModule} aModule
   * @returns {boolean}
   */
  hasOutputImageForModule = (aModule) => {
    return !!this.getOutputImageForModule(aModule);
  };

  /**
   * @param {AnalysisModule} aModule
   * @returns {AnalysisOutputResult}
   */
  getOutputImageForModule = (aModule) => {
    if (!aModule) {
      return undefined;
    }
    return this.analysisOutputImages
      .find(o => o.originalModuleId === aModule.id);
  };

  getMissingInputs = () => {
    return this.pipeline ? this.pipeline.getMissingInputs() : [];
  }

  correctInputsForModules = (correctedInputs = {}) => {
    const corrected = this.pipeline
      ? this.pipeline.correctInputsForModules(correctedInputs)
      : false;
    if (corrected) {
      this.changed = true;
      this.analysisRequested = true;
    }
    return corrected;
  };
}

export {Analysis};
