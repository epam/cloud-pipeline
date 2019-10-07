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

import {action, observable} from 'mobx';
import moment from 'moment';
import {
  ChartData,
  CPUUsageData,
  MemoryUsageData,
  NetworkUsageData,
  FileSystemUsageData
} from './chart-data';
import NodeInstance from '../../../../models/cluster/NodeInstance';

class ChartsData extends ChartData {
  @observable initialized = false;
  @observable error;
  @observable instanceFrom;
  @observable instanceTo;
  @observable followCommonRange = true;
  @observable from;
  @observable to;
  @observable cpuUsage;
  @observable memoryUsage;
  @observable lastMemoryUsage;
  @observable networkUsage;
  @observable fileSystemUsage;

  @observable node;

  set pending (value) {
    this._pending = value;
    if (this.initialized) {
      this.cpuUsage.pending = value;
      this.memoryUsage.pending = value;
      this.networkUsage.pending = value;
      this.fileSystemUsage.pending = value;
    }
  }

  constructor (nodeName) {
    super();
    this.nodeName = nodeName;
    this.initialize()
      .then(this.loadData);
  }

  @action
  initialize = async () => {
    this.node = new NodeInstance(this.nodeName);
    await this.node.fetchIfNeededOrWait();
    if (this.node.error) {
      this.error = this.node.error;
      return;
    }
    const {creationTimestamp, name} = this.node.value;
    this.nodeName = name;
    this.instanceFrom = moment.utc(creationTimestamp).unix();
    this.instanceTo = moment.utc().unix();
    this.from = this.instanceFrom;
    this.cpuUsage = new CPUUsageData(this.nodeName, this.instanceFrom, this.instanceTo);
    this.memoryUsage = new MemoryUsageData(this.nodeName, this.instanceFrom, this.instanceTo);
    this.networkUsage = new NetworkUsageData(this.nodeName, this.instanceFrom, this.instanceTo);
    this.fileSystemUsage = new FileSystemUsageData(
      this.nodeName,
      this.instanceFrom,
      this.instanceTo
    );
    this.initialized = true;
  };

  apply () {}

  processValues (values) {
    super.processValues(values);
    if (this.followCommonRange) {
      this.cpuUsage.processValues(values);
      this.memoryUsage.processValues(values);
      this.networkUsage.processValues(values);
    }
    this.fileSystemUsage.processValues(values);
  }
}

export default ChartsData;
