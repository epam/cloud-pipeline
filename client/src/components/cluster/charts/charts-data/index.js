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

import {action, computed, observable, makeObservable} from 'mobx';
import dayjs from '../../../../utils/dayjs';
import {
  ChartData,
  CPUUsageData,
  MemoryUsageData,
  NetworkUsageData,
  FileSystemUsageData,
} from './chart-data';
import NodeInstance from '../../../../models/cluster/NodeInstance';

class ChartsData extends ChartData {
  initialized = false;
  followCommonRange = true;
  runId;
  cpuUsage;
  memoryUsage;
  lastMemoryUsage;
  networkUsage;
  fileSystemUsage;
  node;

  get pending() {
    if (!this.initialized) {
      return true;
    }
    return (
      this.cpuUsage.pending ||
      this.memoryUsage.pending ||
      this.networkUsage.pending ||
      this.fileSystemUsage.pending
    );
  }

  set pending(value) {
    if (this.initialized) {
      this.cpuUsage.pending = value;
      this.memoryUsage.pending = value;
      this.networkUsage.pending = value;
      this.fileSystemUsage.pending = value;
    }
  }

  constructor(nodeName, from, to, stores, runId) {
    const format = 'YYYY-MM-DD HH:mm:ss';
    const instanceFrom = from ? dayjs.utc(decodeURIComponent(from), format).unix() : undefined;
    const instanceTo = to ? dayjs.utc(decodeURIComponent(to), format).unix() : undefined;
    super(nodeName, instanceFrom, instanceTo, runId);
    makeObservable(this, {
      initialized: observable,
      followCommonRange: observable,
      runId: observable,
      cpuUsage: observable,
      memoryUsage: observable,
      lastMemoryUsage: observable,
      networkUsage: observable,
      fileSystemUsage: observable,
      node: observable,
      pending: computed,
      initialize: action,
    });
    this.preferences = (stores || {}).preferences;
    this.authenticatedUserInfo = (stores || {}).authenticatedUserInfo;
    this.runId = runId;
    this.nodeName = nodeName;
    this.initialize().then(this.loadData);
  }

  initialize = async () => {
    this.node = new NodeInstance(this.nodeName);
    await this.node.fetchIfNeededOrWait();
    if (this.preferences && this.authenticatedUserInfo) {
      // For admins and advanced users, if the
      // `ui.cluster.monitoring.admins.allow.range` is enabled,
      // ranges restriction will be set to node life period instead of
      // run life period (we're passing "from=...&to=..." query parameters
      // when opening node monitor logs for specific run; in that case,
      // we were restricting range selection to a run start-end by initializing
      // chartsData.instanceFrom & .instanceTo properties)
      await Promise.all([
        this.preferences.fetchIfNeededOrWait(),
        this.authenticatedUserInfo.fetchIfNeededOrWait(),
      ]);
      let adminOrAdvancedUser = false;
      if (this.authenticatedUserInfo.loaded && this.authenticatedUserInfo.value) {
        const {admin, roles = []} = this.authenticatedUserInfo.value;
        adminOrAdvancedUser = admin || roles.find((r) => /^ROLE_ADVANCED_USER$/i.test(r.name));
      }
      if (this.preferences.uiClusterMonitoringAdminsAllowRange && adminOrAdvancedUser) {
        this.instanceFrom = undefined;
        this.instanceTo = undefined;
        this.rangeEndIsFixed = false;
      }
    }
    if (this.node.loaded) {
      const {creationTimestamp, name} = this.node.value;
      this.nodeName = name;
      this.instanceFrom =
        this.instanceFrom || (creationTimestamp ? dayjs.utc(creationTimestamp).unix() : undefined);
    }
    this.instanceTo = this.instanceTo || dayjs.utc().unix();
    const lastHour = dayjs.unix(this.instanceTo).add(-1, 'hour').unix();
    this.from = Math.max(this.instanceFrom || lastHour, lastHour);
    this.cpuUsage = new CPUUsageData(this.nodeName, this.instanceFrom, this.instanceTo);
    this.memoryUsage = new MemoryUsageData(this.nodeName, this.instanceFrom, this.instanceTo);
    this.networkUsage = new NetworkUsageData(this.nodeName, this.instanceFrom, this.instanceTo);
    this.fileSystemUsage = new FileSystemUsageData(
      this.nodeName,
      this.instanceFrom,
      this.instanceTo,
    );
    this.initialized = true;
  };

  apply() {}

  processValues(values) {
    super.processValues(values);
    if (!this.initialized) {
      return;
    }
    if (this.followCommonRange) {
      this.cpuUsage.processValues(values);
      this.memoryUsage.processValues(values);
      this.networkUsage.processValues(values);
    }
    this.fileSystemUsage.processValues(values);
  }
}

export default ChartsData;
