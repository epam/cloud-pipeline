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

import PipelineRunInfo from '../../../../models/pipelines/PipelineRunInfo';
import PipelineRunSingleFilter from '../../../../models/pipelines/PipelineRunSingleFilter';
import RunTasks from '../../../../models/pipelines/RunTasks';
import PipelineLanguage from '../../../../models/pipelines/PipelineLanguage';
import RunCount, {ALL_STATUSES} from '../../../../models/pipelines/RunCount';
import continuousFetch from '../../../../utils/continuous-fetch';
import {checkCommitAllowedForTool} from '../../actions';

async function getShowActiveWorkersOnly (run, preferences) {
  const {
    pipelineRunParameters = [],
    status
  } = run || {};
  if (/^(stopped|failure|success)$/i.test(status)) {
    return false;
  }
  const showActiveWorkersOnlyParameter = (pipelineRunParameters || [])
    .find(parameter => parameter.name === 'CP_SHOW_ACTIVE_WORKERS_ONLY');
  if (preferences && !showActiveWorkersOnlyParameter) {
    await preferences.fetchIfNeededOrWait();
    return preferences.uiRunsClusterDetailsShowActiveOnly;
  }
  if (showActiveWorkersOnlyParameter) {
    return /^true$/i.test(
      showActiveWorkersOnlyParameter.value || ''
    );
  }
  return true;
}

export default async function fetchRunInfo (
  runIdentifier,
  dataCallback,
  options
) {
  const {
    maxNestedRunsToDisplay = 10,
    preferences,
    dockerRegistries
  } = options || {};
  let stopped = false;
  const runInfo = new PipelineRunInfo(runIdentifier);
  const runTasks = new RunTasks(runIdentifier);
  const totalNestedRuns = new RunCount({
    parentId: Number(runIdentifier),
    statuses: ALL_STATUSES,
    onlyMasterJobs: false
  });
  await Promise.all([
    runInfo.fetch(),
    runTasks.fetch(),
    totalNestedRuns.fetch(),
    dockerRegistries ? dockerRegistries.fetchIfNeededOrWait() : false
  ].filter(Boolean));
  let hasNestedRuns = totalNestedRuns.runsCount > 0;
  if (runInfo.error || !runInfo.loaded) {
    throw new Error(runInfo.error || 'Error fetching run info');
  }
  const showActiveWorkersOnly = await getShowActiveWorkersOnly(runInfo.value, preferences);
  const {
    status = 'RUNNING',
    dockerImage,
    pipelineId,
    version
  } = runInfo.value;
  const commitAllowed = await checkCommitAllowedForTool(dockerImage, dockerRegistries);
  if (typeof dataCallback === 'function') {
    dataCallback({
      run: runInfo.value,
      runTasks: runTasks.value || [],
      showActiveWorkersOnly,
      language: undefined,
      commitAllowed,
      hasNestedRuns,
      totalNestedRuns: 0,
      nestedRunsPending: true
    });
  }
  const nestedRuns = new PipelineRunSingleFilter(
    {
      page: 1,
      pageSize: maxNestedRunsToDisplay,
      eagerGrouping: false,
      parentId: runIdentifier,
      statuses: showActiveWorkersOnly
        ? ['RUNNING']
        : []
    },
    false
  );
  await nestedRuns.filter();
  let pipelineLanguage;
  if (pipelineId && version) {
    pipelineLanguage = new PipelineLanguage(pipelineId, version);
  }
  let currentStatus = status;
  const isAutoUpdate = () => /^(running|pausing|resuming)$/i.test(currentStatus);
  const call = async () => {
    if (isAutoUpdate() && !stopped) {
      await Promise.all([
        runInfo.fetch(),
        nestedRuns.filter(),
        runTasks.fetch(),
        pipelineLanguage ? pipelineLanguage.fetchIfNeededOrWait() : false
      ].filter(Boolean));
      if (runInfo.networkError) {
        throw new Error(runInfo.networkError);
      }
    }
  };
  const commit = () => {
    const run = runInfo.value || {};
    const {
      status: nextStatus
    } = run;
    currentStatus = nextStatus;
    const error = runInfo.error;
    hasNestedRuns = hasNestedRuns || (nestedRuns.total || 0) > 0;
    if (typeof dataCallback === 'function' && !stopped) {
      dataCallback({
        run,
        nestedRuns: nestedRuns.value || [],
        hasNestedRuns,
        totalNestedRuns: nestedRuns.total || 0,
        nestedRunsPending: false,
        error,
        showActiveWorkersOnly,
        runTasks: runTasks.value || [],
        language: pipelineLanguage && pipelineLanguage.loaded ? pipelineLanguage.value : undefined,
        commitAllowed
      });
    }
  };
  commit();
  let stop = () => {
    stopped = true;
  };
  const reFetch = async () => new Promise((resolve) => {
    runInfo.fetch()
      .catch(() => {})
      .then(() => commit())
      .then(() => resolve());
  });
  if (isAutoUpdate()) {
    const {
      stop: stopAutoUpdate
    } = continuousFetch({
      call,
      afterInvoke: commit,
      fetchImmediate: false
    });
    stop = () => {
      stopped = true;
      stopAutoUpdate();
    };
  }
  return {
    stop,
    fetch: reFetch
  };
}
