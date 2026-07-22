/*
 * Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import UsageCreditsRules from './UsageCreditsRules';

const MOCK_RULES = [
  {
    id: 1,
    name: 'Idle Run Penalty',
    ruleType: 'RUN_STATE',
    description: 'Penalty for runs idle longer than the configured duration',
    statement: {
      field: 'tag',
      value: 'IDLE',
      operand: '=',
      conditionType: 'AND',
      duration: 48
    },
    exclude: {
      field: 'region_id',
      value: '1',
      operand: '=',
      conditionType: 'AND'
    },
    action: {
      type: 'DEDUCTION',
      value: 100,
      message: 'Run IDLE for >48h with no activity',
      perIncident: true
    }
  },
  {
    id: 2,
    name: 'Spot Usage Reward',
    ruleType: 'RUN_STATE',
    description: 'Reward for launching spot instances',
    statement: {
      field: 'spot',
      value: 'true',
      operand: '=',
      conditionType: 'AND'
    },
    exclude: {
      field: 'node.type',
      value: '[m5.*, c5.*]',
      operand: '=',
      conditionType: 'AND'
    },
    action: {
      type: 'INCREASE',
      value: 200,
      message: 'User launched a SPOT instance',
      perIncident: false
    }
  },
  {
    id: 3,
    name: 'Long Running Job',
    ruleType: 'RUN_STATE',
    description: 'Penalty for long-running jobs',
    statement: {
      field: 'tag',
      value: 'LONG-RUNNING',
      operand: '=',
      conditionType: 'AND',
      duration: 72
    },
    exclude: null,
    action: {
      type: 'DEDUCTION',
      value: 10,
      message: 'Long running job penalty',
      perIncident: true
    }
  },
  {
    id: 4,
    name: 'Spot Idle Penalty',
    ruleType: 'RUN_STATE',
    description: 'Penalty for idle spot runs',
    statement: {
      field: 'tag',
      value: 'IDLE',
      operand: '=',
      conditionType: 'AND',
      duration: 72
    },
    exclude: null,
    action: {
      type: 'DEDUCTION',
      value: 50,
      message: 'Spot run IDLE for >72h with no activity',
      perIncident: true
    }
  },
  {
    id: 5,
    name: 'Manual Adjustment',
    ruleType: 'MANUAL',
    description: 'Manual credits adjustment by administrator',
    statement: null,
    exclude: null,
    action: {
      type: 'INCREASE',
      value: 0,
      message: 'Admin correction: credits restored',
      perIncident: false
    }
  }
];

// Delete this class when /usage/credits/rules backend is ready.
export default class UsageCreditsRulesMock extends UsageCreditsRules {
  async fetch () {
    this._pending = true;
    this.failed = false;
    this.error = undefined;
    this.networkError = undefined;
    try {
      this.update({
        status: 'OK',
        payload: MOCK_RULES
      });
    } finally {
      this._pending = false;
    }
  }
}
