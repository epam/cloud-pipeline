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

import {DataStorageRulesList} from './rules/DataStorageRulesList';
import {DataStorageRuleRegister} from './rules/DataStorageRuleRegister';
import {DataStorageRuleDelete} from './rules/DataStorageRuleDelete';
import {observable, action, computed} from 'mobx';

export default class DataStorageRules {

  pipelineId = undefined;
  dataStorageRulesListRequest = null;

  @observable _createRuleLastError = null;
  @observable _deleteRuleLastError = null;
  @observable _createRuleIsPending = false;
  @observable _deleteRuleIsPending = false;

  constructor (pipelineId) {
    this.pipelineId = pipelineId;
    this.dataStorageRulesListRequest = new DataStorageRulesList(this.pipelineId);
    this.refresh();
  }

  @computed
  get pending () {
    return this.dataStorageRulesListRequest.pending || this._createRuleIsPending || this._deleteRuleIsPending;
  }

  @computed
  get list () {
    if (this.dataStorageRulesListRequest.value && this.dataStorageRulesListRequest.value.length) {
      return this.dataStorageRulesListRequest.value;
    } else {
      return [];
    }
  }

  @action
  async refresh () {
    if (!this.dataStorageRulesListRequest.pending) {
      await this.dataStorageRulesListRequest.fetch();
    }
  }

  @computed
  get createRuleLastError () {
    return this._createRuleLastError;
  }

  @computed
  get deleteRuleLastError () {
    return this._deleteRuleLastError;
  }

  @action
  async createRule (rule) {
    this._createRuleIsPending = true;
    const dataStorageRuleRegisterRequest = new DataStorageRuleRegister();
    if (rule.moveToSts === undefined) {
      rule.moveToSts = false;
    }
    await dataStorageRuleRegisterRequest.send(rule);
    this._createRuleLastError = dataStorageRuleRegisterRequest.error;
    this._createRuleIsPending = false;
    await this.refresh();
  }

  @action
  async deleteRule (rule) {
    this._deleteRuleIsPending = true;
    const dataStorageRuleDeleteRequest = new DataStorageRuleDelete(rule.pipelineId, rule.fileMask);
    await dataStorageRuleDeleteRequest.fetch();
    this._deleteRuleLastError = dataStorageRuleDeleteRequest.error;
    this._deleteRuleIsPending = false;
    await this.refresh();
  }

}
