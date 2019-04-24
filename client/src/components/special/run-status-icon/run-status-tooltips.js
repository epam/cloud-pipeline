/*
 * Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import React from 'react';
import Statuses from './run-statuses';

export default {
  [Statuses.failure]: {
    description: 'Run execution failure may occur for a lot of reasons: ' +
      'Compute node failed to initialize, environment cannot be prepared, ' +
      'job errored itself, etc. Review the logs of the job tasks, ' +
      'which are marked with the red exclamation icon',
    title: <b>Failed</b>
  },
  [Statuses.paused]: {
    description: 'Run is paused. At this moment compute node is already ' +
      'stopped but keeps it\'s state. Such a job may be resumed - this will ' +
      'restore the environment and all the changes, that were made before pausing',
    title: <b>Paused</b>
  },
  [Statuses.pausing]: {
    description: 'Run is being paused. At this moment compute node will be stopped ' +
      '(but persisted) and the docker image state will be kept as well. ' +
      'Further restart of the paused job will restore the environment and all the changes, ' +
      'that were made before pausing',
    title: <b>Pausing</b>
  },
  [Statuses.pulling]: {
    description: 'Compute node is available and a job is assigned. ' +
      'System is now pulling the docker image to setup an environment. ' +
      'ETA for this phase varies depending on the image size, ' +
      'but in average it shall take about one minute',
    title: <b>Pulling</b>
  },
  [Statuses.queued]: {
    description: 'Job is waiting in the queue for the available compute node. ' +
      'Typically a job will sit in the queue for a couple of second and ' +
      'proceed to the initialization phase. But if this state lasts for a long time - ' +
      'it may mean that a cluster capacity is reached',
    title: <b>Queued</b>
  },
  [Statuses.resuming]: {
    description: 'A paused run is being resumed. ' +
      'At this moment compute node is starting back from the stopped state. ' +
      'Docker image and the local filesystem will contain all the modification made previously',
    title: <b>Resuming</b>
  },
  [Statuses.running]: {
    description: 'Compute environment is pulled to the compute node. ' +
      'The system is preparing the requested assets and runs a job. ' +
      'During this stage - input data will be copies to the compute node, ' +
      'data storage mounted, cluster configuration set, etc. ' +
      'Such steps can be visible via corresponding tasks logs in the run details page. ' +
      'Once all preparations are done - job entrypoint script will be executed',
    title: <b>Running</b>
  },
  [Statuses.scheduled]: {
    description: 'Run is being initialized, at this stage a new compute node ' +
      'will be created or an existing node will be reused, ' +
      'if available in the cluster. This stage takes 3 min in average to complete',
    title: <b>Initializing</b>
  },
  [Statuses.stopped]: {
    description: 'Run is stopped manually, no errors occurred. Somebody requested this job to stop',
    title: <b>Stopped</b>
  },
  [Statuses.success]: {
    description: 'Job is completed successfully, no errors occurred',
    title: <b>Success</b>
  }
};
