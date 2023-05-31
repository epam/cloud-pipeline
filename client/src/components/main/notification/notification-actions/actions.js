/*
 * Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import {message} from 'antd';
import moment from 'moment-timezone';
import StopPipeline from '../../../../models/pipelines/StopPipeline';
import ResumePipeline from '../../../../models/pipelines/ResumePipeline';
import PausePipeline from '../../../../models/pipelines/PausePipeline';
import TerminatePipeline from '../../../../models/pipelines/TerminatePipeline';
import DataStorageLifeCycleRulesPostpone
from '../../../../models/dataStorage/lifeCycleRules/DataStorageLifeCycleRulesPostpone';
import {canPauseRun, canStopRun} from '../../../runs/actions';
import RunStatuses from '../../../special/run-status-icon/run-statuses';

const ACTIONS = {
  viewNodeMonitor: {
    key: 'View node monitor',
    actionFn: ({entity, router}) => {
      const {
        instance,
        startDate,
        endDate
      } = entity;
      const parts = [
        startDate && `from=${encodeURIComponent(startDate)}`,
        endDate && `to=${encodeURIComponent(endDate)}`
      ].filter(Boolean);
      const query = parts.length > 0 ? `?${parts.join('&')}` : '';
      router && router.push(`/cluster/${instance.nodeName}/monitor${query}`);
    },
    available: (entity) => entity &&
      entity.instance &&
      entity.instance.nodeName
  },
  viewRun: {
    key: 'View run',
    actionFn: ({entity, router}) => {
      router && router.push(`/run/${entity.id}`);
    },
    available: () => true
  },
  pauseRun: {
    key: 'Pause run',
    actionFn: async ({entity, callback}) => {
      const hide = message.loading('Pausing...', -1);
      const request = new PausePipeline(entity.id);
      await request.send({});
      if (request.error) {
        message.error(request.error);
      }
      hide();
      callback && callback();
    },
    available: (entity, preferences) => entity && preferences && canPauseRun(entity, preferences)
  },
  resumeRun: {
    key: 'Resume run',
    actionFn: async ({entity, callback}) => {
      const hide = message.loading('Resuming...', -1);
      const request = new ResumePipeline(entity.id);
      await request.send({});
      if (request.error) {
        message.error(request.error);
      }
      hide();
      callback && callback();
    },
    available: (entity) => entity && entity.status === RunStatuses.paused
  },
  stopRun: {
    key: 'Stop run',
    actionFn: async ({entity, callback}) => {
      const hide = message.loading('Stopping...', -1);
      const request = new StopPipeline(entity.id);
      await request.send({
        endDate: moment().format('YYYY-MM-DD HH:mm:ss.SSS'),
        status: 'STOPPED'
      });
      if (request.error) {
        message.error(request.error);
      }
      hide();
      callback && callback();
    },
    available: (entity) => entity && canStopRun(entity)
  },
  terminateRun: {
    key: 'Terminate run',
    actionFn: async ({entity, callback}) => {
      const hide = message.loading('Terminating run...', -1);
      const request = new TerminatePipeline(entity.id);
      await request.send({});
      if (request.error) {
        message.error(request.error);
      }
      hide();
      callback && callback();
    },
    available: (entity) => entity && entity.status === RunStatuses.paused
  },
  openDatastorage: {
    key: 'Open datastorage',
    actionFn: ({notification = {}, router}) => {
      const details = (notification.resources || [])[0] || {};
      const {entityId} = details;
      router && entityId && router.push(`/storage/${entityId}`);
    },
    available: () => true
  },
  postponeLifecycleRule: {
    key: 'Postpone',
    actionFn: async ({notification = {}, callback}) => {
      const details = (notification.resources || [])[0] || {};
      const hide = message.loading('Postpone...', -1);
      const request = new DataStorageLifeCycleRulesPostpone({
        datastorageId: details.entityId,
        ruleId: details.storageRuleId,
        path: details.storagePath
      });
      await request.fetch();
      if (request.error) {
        message.error(request.error);
      }
      hide();
      callback && callback();
    },
    available: () => true
  },
  viewBilling: {
    key: 'View billing',
    actionFn: ({router}) => {
      router && router.push('/billing/reports/storage');
    },
    available: () => true
  },
  openPoolsUsage: {
    key: 'Open pools usage statistics',
    actionFn: ({router}) => {
      router && router.push('/cluster/usage');
    },
    available: () => true
  }
};

const ENTITY_CLASSES = {
  RUN: 'RUN',
  STORAGE: 'STORAGE',
  ISSUE: 'ISSUE',
  QUOTA: 'QUOTA',
  NODE_POOL: 'NODE_POOL',
  USER: 'USER'
};

const NOTIFICATION_TYPES = {
  BILLING_QUOTA_EXCEEDING: 'BILLING_QUOTA_EXCEEDING',
  DATASTORAGE_LIFECYCLE_ACTION: 'DATASTORAGE_LIFECYCLE_ACTION',
  DATASTORAGE_LIFECYCLE_RESTORE_ACTION: 'DATASTORAGE_LIFECYCLE_RESTORE_ACTION',
  FULL_NODE_POOL: 'FULL_NODE_POOL',
  HIGH_CONSUMED_RESOURCES: 'HIGH_CONSUMED_RESOURCES',
  IDLE_RUN: 'IDLE_RUN',
  IDLE_RUN_PAUSED: 'IDLE_RUN_PAUSED',
  IDLE_RUN_STOPPED: 'IDLE_RUN_STOPPED',
  LONG_INIT: 'LONG_INIT',
  LONG_PAUSED: 'LONG_PAUSED',
  LONG_PAUSED_STOPPED: 'LONG_PAUSED_STOPPED',
  LONG_RUNNING: 'LONG_RUNNING',
  LONG_STATUS: 'LONG_STATUS',
  NEW_ISSUE: 'NEW_ISSUE',
  NEW_ISSUE_COMMENT: 'NEW_ISSUE_COMMENT',
  PIPELINE_RUN_STATUS: 'PIPELINE_RUN_STATUS',
  STORAGE_QUOTA_EXCEEDING: 'STORAGE_QUOTA_EXCEEDING',
  INACTIVE_USERS: 'INACTIVE_USERS',
  LDAP_BLOCKED_POSTPONED_USERS: 'LDAP_BLOCKED_POSTPONED_USERS',
  LDAP_BLOCKED_USERS: 'LDAP_BLOCKED_USERS'
};

export {ACTIONS, ENTITY_CLASSES, NOTIFICATION_TYPES};
