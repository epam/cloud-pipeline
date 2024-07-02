/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import {action, observable} from 'mobx';
import preferencesLoad from '../../models/preferences/PreferencesLoad';
import Pipeline from '../../models/pipelines/Pipeline';
import PipelineFiles from '../../models/pipelines/Source';
import PipelineConfigurations from '../../models/pipelines/PipelineConfigurations';
import {PipelineRunner} from '../../models/pipelines/PipelineRunner';
import VersionFile from '../../models/pipelines/VersionFile';
import {base64toString} from '../base64';

function extractScriptParameters (text = '') {
  const [parametersRow] = text
    .split('\n')
    .map(row => row.trim())
    .filter(row => row.startsWith('#sys_job'));
  if (!parametersRow) {
    return {};
  }
  const parameterString = parametersRow.replace('#sys_job', '');
  if (!parameterString) {
    return {};
  }
  try {
    return JSON.parse(parameterString);
  } catch (e) {
    throw new Error('JSON validation error in system job parameters from script');
  }
}

/**
 * @typedef {Object} SystemJob
 * @property {string} identifier
 * @property {string} path
 * @property {number} pipelineId
 * @property {string} pipelineVersion
 * @property {string} outputTask
 * @property {*} configuration
 */

class SystemJobs {
  /**
   * @type {boolean}
   */
  @observable pending = true;
  /**
   * @type {string}
   */
  @observable error = undefined;
  /**
   * @type {boolean}
   */
  @observable loaded = false;
  /**
   * @type {SystemJob[]}
   */
  @observable jobs = [];

  /**
   * @param {SystemJob} job
   * @param {string} [parameters]
   * @returns {Promise<void>}
   */
  async launchJob (job, parameters) {
    const {
      systemJobsScriptsLocation: scriptsLocation,
      systemJobsOutputPipelineTask: outputTask
    } = preferencesLoad;
    const {
      instance_size: instanceType,
      instance_disk: hddSize = 15,
      docker_image: dockerImage,
      timeout = 0,
      cmd_template: cmdTemplate,
      parameters: pipelineParameters,
      is_spot: isSpot
    } = job.configuration || {};
    const params = Object.entries(pipelineParameters || {})
      .map(([key, parameter]) => ({
        [key]: {
          value: parameter.value,
          type: parameter.type
        }
      }))
      .reduce((r, c) => ({...r, ...c}), {});
    params['CP_SYSTEM_JOB'] = {
      value: job.identifier
    };
    params['CP_SYSTEM_SCRIPTS_LOCATION'] = {
      value: scriptsLocation
    };
    params['CP_SYSTEM_JOBS_OUTPUT_TASK'] = {
      value: outputTask
    };
    if (parameters) {
      params['CP_SYSTEM_JOB_PARAMS'] = {
        value: parameters
      };
    }
    const payload = {
      pipelineId: job.pipelineId,
      version: job.pipelineVersion,
      instanceType,
      hddSize,
      timeout,
      dockerImage,
      isSpot,
      cmdTemplate,
      params,
      tags: {
        'CP_SYSTEM_JOB': job.identifier
      }
    };
    const request = new PipelineRunner();
    await request.send(payload);
    if (request.error) {
      throw new Error(request.error);
    }
  }

  @action
  async update () {
    this.pending = true;
    try {
      await preferencesLoad.fetchIfNeededOrWait();
      const {
        systemJobsPipelineId: pipelineId,
        systemJobsScriptsLocation: scriptsLocation,
        systemJobsOutputPipelineTask: outputTask
      } = preferencesLoad;
      if (!pipelineId) {
        throw new Error('System jobs not configured');
      }
      const pipelineRequest = new Pipeline(pipelineId);
      await pipelineRequest.fetch();
      if (pipelineRequest.error || !pipelineRequest.loaded) {
        throw new Error(pipelineRequest.error || 'Error loading system jobs pipeline info');
      }
      const {
        currentVersion = {}
      } = pipelineRequest.value;
      const {
        name: currentVersionName
      } = currentVersion;
      if (!currentVersionName) {
        throw new Error('Error loading system jobs pipeline latest version');
      }
      const configurations = new PipelineConfigurations(
        pipelineId,
        currentVersionName
      );
      const scripts = new PipelineFiles(
        pipelineId,
        currentVersionName,
        scriptsLocation
      );
      await Promise.all([scripts.fetch(), configurations.fetch()]);
      if (scripts.error || !scripts.loaded) {
        throw new Error(scripts.error || 'Error loading system jobs scripts');
      }
      if (configurations.error || !configurations.loaded) {
        throw new Error(configurations.error || 'Error loading system jobs configuration');
      }
      const configuration = (configurations.value || [])
        .find((aConfig) => aConfig.default) || configurations[0];
      this.jobs = (scripts.value || [])
        .filter((aFile) => /^blob$/i.test(aFile.type))
        .map((aFile) => ({
          pipelineId,
          pipelineVersion: currentVersionName,
          identifier: aFile.name,
          path: aFile.path,
          outputTask,
          configuration: configuration ? configuration.configuration : {}
        }));
      this.error = undefined;
      this.loaded = true;
    } catch (error) {
      this.error = error.message;
      this.loaded = false;
    } finally {
      this.pending = false;
    }
  }

  async fetchJobParameters (job) {
    if (!job) {
      return {};
    }
    if (!job.parametersPromise) {
      job.parametersPromise = new Promise((resolve, reject) => {
        const request = new VersionFile(
          job.pipelineId,
          job.path,
          job.pipelineVersion
        );
        request
          .fetch()
          .then(() => {
            if (request.error) {
              return Promise.reject(request.error);
            }
            try {
              const content = base64toString(request.response || '');
              resolve(extractScriptParameters(content) || {});
            } catch (error) {
              return Promise.reject(error);
            }
          })
          .catch(reject);
      });
    }
    return job.parametersPromise;
  }
}

export default SystemJobs;
