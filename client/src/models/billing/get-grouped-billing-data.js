/*
 * Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

import RemotePost from '../basic/RemotePost';
import User from '../user/User';
import GetDataWithPrevious from './get-data-with-previous';
import costMapper from './cost-mapper';
import join from './join-periods';

const minutesToHours = (minutes) => {
  if (!minutes || isNaN(minutes)) {
    return 0;
  }

  return Math.round(((minutes / 60) + Number.EPSILON) * 100) / 100;
};
const bytesToGbs = (bytes) => {
  if (!bytes || isNaN(bytes)) {
    return 0;
  }
  const bInGb = 1024 * 1024 * 1024;
  return Math.round(((bytes / bInGb) + Number.EPSILON) * 100) / 100;
};

export class GetGroupedBillingData extends RemotePost {
  constructor (filters, groupedBy) {
    super();
    this.filters = filters;
    this.groupedBy = groupedBy;
    this.url = '/billing/charts';
  }

  static GROUP_BY = {
    billingCenters: 'BILLING_CENTER',
    resources: 'resource',
    storageType: 'STORAGE_TYPE',
    computeType: 'RUN_COMPUTE_TYPE',
    storages: 'STORAGE',
    objectStorages: 'OBJECT_STORAGE',
    fileStorages: 'FILE_STORAGE',
    instances: 'RUN_INSTANCE_TYPE',
    pipelines: 'PIPELINE',
    tools: 'TOOL'
  };

  body = {};

  async fetch () {
    let userRequest;
    if (this.filters && this.filters.user) {
      userRequest = new User(this.filters.user);
      await userRequest.fetchIfNeededOrWait();
    }
    this.body.from = this.filters && this.filters.start
      ? this.filters.start.toISOString() : undefined;
    this.body.to = this.filters && this.filters.end
      ? this.filters.end.toISOString() : undefined;
    if (this.groupedBy === GetGroupedBillingData.GROUP_BY.billingCenters) {
      if (this.filters && this.filters.group) {
        this.body.filters = {};
        this.body.grouping = 'USER';
        this.body.loadDetails = true;
        this.body.filters.billing_center = [this.filters.group];
      } else {
        this.body.grouping = GetGroupedBillingData.GROUP_BY.billingCenters;
      }
      return super.send(this.body);
    } else if (this.groupedBy === GetGroupedBillingData.GROUP_BY.resources) {
      const payload = {};

      this._pending = true;
      this._postIsExecuting = true;

      const storageTypesRequest = new GetGroupedBillingData(
        this.filters,
        GetGroupedBillingData.GROUP_BY.storageType
      );
      await storageTypesRequest.fetch();

      if (storageTypesRequest.error) {
        this._pending = false;
        this.failed = true;
        this._postIsExecuting = false;
        this.error = storageTypesRequest.error;
        return;
      }
      payload['Storage'] = storageTypesRequest.loaded ? storageTypesRequest.value : [];

      const computeTypesRequest = new GetGroupedBillingData(
        this.filters,
        GetGroupedBillingData.GROUP_BY.computeType
      );
      await computeTypesRequest.fetch();

      if (computeTypesRequest.error) {
        this._pending = false;
        this.failed = true;
        this._postIsExecuting = false;
        this.error = computeTypesRequest.error;
        return;
      }
      payload['Compute instances'] = computeTypesRequest.loaded ? computeTypesRequest.value : [];

      this.update({
        status: 'OK',
        payload
      });
      this._pending = false;
      this._postIsExecuting = false;
    } else if (
      [
        GetGroupedBillingData.GROUP_BY.storageType,
        GetGroupedBillingData.GROUP_BY.computeType
      ].includes(this.groupedBy)
    ) {
      this.body.filters = {};
      this.body.grouping = this.groupedBy;
      if (this.filters && this.filters.user && userRequest.loaded && userRequest.value) {
        this.body.filters.owner = [userRequest.value.userName];
      }
      if (this.filters && this.filters.group) {
        this.body.filters.billing_center = [this.filters.group];
      }

      return super.send(this.body);
    } else if (
      [
        GetGroupedBillingData.GROUP_BY.storages,
        GetGroupedBillingData.GROUP_BY.fileStorages,
        GetGroupedBillingData.GROUP_BY.objectStorages
      ].indexOf(this.groupedBy) >= 0
    ) {
      this.body.filters = {};
      this.body.grouping = GetGroupedBillingData.GROUP_BY.storages;
      this.body.loadDetails = true;
      if (this.groupedBy === GetGroupedBillingData.GROUP_BY.fileStorages) {
        this.body.filters.storage_type = [GetGroupedBillingData.GROUP_BY.fileStorages];
      } else if (this.groupedBy === GetGroupedBillingData.GROUP_BY.objectStorages) {
        this.body.filters.storage_type = [GetGroupedBillingData.GROUP_BY.objectStorages];
      }
      if (this.filters && this.filters.user && userRequest.loaded && userRequest.value) {
        this.body.filters.owner = [userRequest.value.userName];
      }
      if (this.filters && this.filters.group) {
        this.body.filters.billing_center = [this.filters.group];
      }

      return super.send(this.body);
    } else if ([
      GetGroupedBillingData.GROUP_BY.instances,
      GetGroupedBillingData.GROUP_BY.tools,
      GetGroupedBillingData.GROUP_BY.pipelines
    ].indexOf(this.groupedBy) >= 0) {
      this.body.filters = {};
      this.body.grouping = this.groupedBy;
      this.body.loadDetails = true;
      if (this.filters.type) {
        this.body.filters.compute_type = [this.filters.type.toUpperCase()];
      }
      if (this.filters && this.filters.user && userRequest.loaded && userRequest.value) {
        this.body.filters.owner = [userRequest.value.userName];
      }
      if (this.filters && this.filters.group) {
        this.body.filters.billing_center = [this.filters.group];
      }

      return super.send(this.body);
    }
  }

  postprocess (value) {
    const payload = super.postprocess(value);
    return this.prepareData(payload);
  }

  prepareResources (raw) {
    const res = {};
    function renameStorage (value) {
      if (/^object_storage$/i.test(value)) {
        return 'Object';
      }
      if (/^file_storage$/i.test(value)) {
        return 'File';
      }
      return value;
    }
    if (raw['Storage']) {
      res['Storage'] = {};
      raw['Storage'].forEach(i => {
        const name = renameStorage(i.groupingInfo.STORAGE_TYPE);
        if (name && name !== 'unknown') {
          res['Storage'][name] = {
            ...i,
            value: isNaN(i.cost) ? 0 : costMapper(i.cost)
          };
        }
      });
    }
    if (raw['Compute instances']) {
      res['Compute instances'] = {};
      raw['Compute instances'].forEach(i => {
        const name = i.groupingInfo.RUN_COMPUTE_TYPE;
        if (name && name !== 'unknown') {
          res['Compute instances'][name] = {
            ...i,
            value: isNaN(i.cost) ? 0 : costMapper(i.cost)
          };
        }
      });
    }

    return res;
  }

  prepareStoragesData (raw) {
    const res = {};

    (raw && raw.length ? raw : []).forEach(i => {
      let name = i.info && i.info.name ? i.info.name : i.groupingInfo.STORAGE;
      if (name && name !== 'unknown') {
        res[name] = {
          name,
          owner: i.groupingInfo.owner,
          created: i.groupingInfo.created,
          region: i.groupingInfo.region,
          provider: i.groupingInfo.provider,
          value: isNaN(i.cost) ? 0 : costMapper(i.cost),
          ...i
        };
      }
    });

    return res;
  }

  prepareBillingCentersData (raw) {
    const res = {};
    (raw && raw.length ? raw : []).forEach(i => {
      if (this.filters && this.filters.group) {
        const name = i.groupingInfo.USER;
        if (name && name !== 'unknown') {
          res[name] = {
            ...i,
            name,
            value: isNaN(i.cost) ? 0 : costMapper(i.cost),
            runsDuration: minutesToHours(i.groupingInfo.usage_runs),
            storageUsage: bytesToGbs(i.groupingInfo.usage_storages),
            runsCount: i.groupingInfo.runs,
            spendings: isNaN(i.cost) ? 0 : costMapper(i.cost)
          };
        }
      } else {
        const name = i.groupingInfo[GetGroupedBillingData.GROUP_BY.billingCenters];
        if (name && name !== 'unknown') {
          res[name] = {
            ...i,
            value: isNaN(i.cost) ? 0 : costMapper(i.cost)
          };
        }
      }
    });
    return res;
  }

  prepareInstancesReportData (raw) {
    const res = {};

    (raw && raw.length > 0 ? raw : []).forEach((item) => {
      let fullName;
      let name = item.groupingInfo[this.groupedBy];
      if (name.includes('/')) {
        name = name.split('/').pop();
        fullName = item.groupingInfo[this.groupedBy];
      }
      if (name && name !== 'unknown') {
        res[name] = {
          name,
          fullName,
          ...item,
          owner: item.groupingInfo.owner,
          usage: minutesToHours(item.groupingInfo.usage_runs),
          runsCount: item.groupingInfo.runs,
          value: isNaN(item.cost) ? 0 : costMapper(item.cost)
        };
      }
    });

    return res;
  }

  prepareData (rawData) {
    switch (this.groupedBy) {
      case GetGroupedBillingData.GROUP_BY.billingCenters:
        return this.prepareBillingCentersData(rawData);
      case GetGroupedBillingData.GROUP_BY.storages:
      case GetGroupedBillingData.GROUP_BY.objectStorages:
      case GetGroupedBillingData.GROUP_BY.fileStorages:
        return this.prepareStoragesData(rawData);
      case GetGroupedBillingData.GROUP_BY.resources:
        return this.prepareResources(rawData);
      case GetGroupedBillingData.GROUP_BY.instances:
      case GetGroupedBillingData.GROUP_BY.tools:
      case GetGroupedBillingData.GROUP_BY.pipelines:
        return this.prepareInstancesReportData(rawData);
      default:
        return rawData;
    }
  }
}

class GetGroupedBillingDataWithPreviousRange extends GetDataWithPrevious {
  constructor (filters, groupedBy) {
    const {
      end,
      endStrict,
      previousEnd,
      previousEndStrict,
      ...rest
    } = filters;
    const formattedFilters = {
      end: endStrict || end,
      previousEnd: previousEndStrict || previousEnd,
      ...rest
    };
    super(
      GetGroupedBillingData,
      formattedFilters,
      groupedBy
    );
    this.groupBy = groupedBy;
  }

  static GROUP_BY = GetGroupedBillingData.GROUP_BY;

  send () {
    return this.fetch();
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    switch (this.groupBy) {
      case GetGroupedBillingDataWithPreviousRange.GROUP_BY.resources:
        const storage = join(
          (current || {})['Storage'],
          (previous || {})['Storage']
        );
        const compute = join(
          (current || {})['Compute instances'],
          (previous || {})['Compute instances']
        );
        return {
          'Storage': storage,
          'Compute instances': compute
        };
      default:
        return join(current, previous);
    }
  }
}

export default GetGroupedBillingDataWithPreviousRange;
