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
  launchJobWithDockerImage,
  waitForJobToBeInitialized
} from './job-utilities';
import {AnalysisPipeline} from './pipeline';
import AnalysisApi from './analysis-api';
import runAnalysisPipeline from './analysis-pipeline-utilities';
import {loadPipeline, savePipeline} from './analysis-pipeline-management';
import PhysicalSize from './physical-size';

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
  @observable _active = false;
  /**
   * @type {AnalysisOutputResult[]}
   */
  @observable analysisResults = [];
  analysisCache = [];
  @observable showAnalysisResults = true;
  hcsImageViewer;
  _analysisRequested = false;
  eventListeners = [];

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
   * @returns {AnalysisOutputResult}
   */
  @computed
  get analysisOutput () {
    return this.analysisResults.find(o => o.analysisOutput);
  }

  @computed
  get modules () {
    if (!this.pipeline) {
      return [];
    }
    return [...this.pipeline.modules, this.pipeline.defineResults];
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
    const path = await savePipeline(this.pipeline);
    if (path) {
      this.pipeline.path = path;
    }
  };

  /**
   * @param {AnalysisPipelineFile} pipelineFile
   * @returns {Promise<void>}
   */
  loadPipeline = async (pipelineFile) => {
    if (!pipelineFile) {
      return;
    }
    try {
      this.pending = true;
      this.status = `Opening pipeline ${pipelineFile.name}...`;
      const pipeline = pipelineFile.pipeline || (await loadPipeline(pipelineFile));
      if (!pipeline) {
        throw new Error(`Error opening pipeline ${pipelineFile.name}: empty pipeline`);
      }
      pipeline.analysis = this;
      this.pipeline = pipeline;
      this.status = `Pipeline ${pipeline.name} opened`;
      this.error = undefined;
      this.changed = true;
      this.analysisRequested = true;
    } catch (error) {
      this.error = error.message;
    } finally {
      this.pending = false;
    }
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
  updatePhysicalSize (unitsInPixel, units) {
    this.physicalSize.update(unitsInPixel, units);
  }

  @action
  activate = async (active = true) => {
    this._active = active;
    if (active && !this.pipelineId) {
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
        wells = [],
        images = [],
        timePoints = [],
        zCoordinates = []
      } = this.namesAndTypes.sourceFile || {};
      const files = [];
      timePoints.forEach(timePoint => {
        zCoordinates.forEach(z => {
          wells.forEach(well => {
            const {
              x: column = 0,
              y: row = 0
            } = well;
            images.forEach(image => {
              names.forEach((channel, index) => {
                files.push({
                  x: row,
                  y: column,
                  z,
                  timepoint: timePoint,
                  fieldId: image,
                  channel: index + 1,
                  channelName: channel
                });
              });
            });
          });
        });
      });
      await this.analysisAPI.attachFiles(this.pipelineId, ...files);
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
  run = async () => {
    if (!this.active || !this.pipelineId || !this.analysisAPI) {
      this.modules.forEach(module => {
        module.pending = false;
        module.done = false;
      });
      return;
    }
    /**
     * @param {{module: AnalysisModule, done: boolean}} options
     */
    const reportModuleStatus = (options) => {
      if (!options) {
        this.modules.forEach(module => {
          module.pending = false;
          module.done = true;
        });
        return;
      }
      const idx = this.modules.indexOf(options.module);
      if (idx >= 0) {
        this.modules.slice(0, idx).forEach(module => {
          module.pending = false;
          module.done = true;
        });
        options.module.pending = !options.done;
        options.module.done = !!options.done;
      }
    };
    try {
      this.analysisRequested = false;
      this.analysing = true;
      this.pending = true;
      this.error = undefined;
      this.status = 'Running analysis...';
      const {
        results,
        cache
      } = await runAnalysisPipeline(
        this.analysisAPI,
        this.pipelineId,
        this.modules,
        this.analysisCache,
        {debug: true, callback: reportModuleStatus}
      );
      this.analysisResults = results.filter(o => !o.object);
      this.pipeline.objectsOutlines.update(results, this.hcsImageViewer);
      this.analysisCache = cache;
      this.showAnalysisResults = true;
      this.status = 'Analysis done';
    } catch (error) {
      this.modules.forEach(module => {
        module.pending = false;
        module.done = false;
      });
      this.error = error.message;
      console.warn(error);
    } finally {
      this.pending = false;
      this.analysing = false;
      this.fireEvent(Analysis.Events.analysisDone);
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
      this.modules.forEach(aModule => {
        aModule.pending = false;
        aModule.done = false;
      });
      this.pipeline.objectsOutlines.detachResults(this.hcsImageViewer);
      this.analysisCache = [];
      (async () => {
        await this.buildPipeline();
        if (fileChanged) {
          await this.attachFileToPipeline();
        }
      })();
    };
    let changed = false;
    if (!this.namesAndTypes) {
      this.namesAndTypes = new NamesAndTypes(hcsSourceFile);
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
}

export {Analysis};
