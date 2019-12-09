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

import RemotePost from '../basic/RemotePost';
import User from '../user/User';
import UsersList from '../user/Users';
import BillingCenters from './billing-centers';
import GetDataWithPrevious from './get-data-with-previous';
import costMapper from './cost-mapper';

class GetGroupedBillingData extends RemotePost {
  constructor (filters, groupedBy) {
    super();
    this.filters = filters;
    this.groupedBy = groupedBy;
    this.url = '/billing/charts';
  }

  static GROUP_BY = {
    billingCenters: 'BILLING_CENTER',
    resources: 'resource',
    storages: 'STORAGE',
    objectStorages: 'OBJECT_STORAGE',
    fileStorages: 'FILE_STORAGE',
    instances: 'RUN_INSTANCE_TYPE',
    pipelines: 'PIPELINE',
    tools: 'TOOL'
  };

  async fetch () {
    const body = {};
    let billingCenter;
    if (this.filters && this.filters.group) {
      const billingCentersRequest = new BillingCenters();
      await billingCentersRequest.fetch();
      [billingCenter] = billingCentersRequest.loaded && billingCentersRequest.value
        ? billingCentersRequest.value.filter(bc => bc.id === +this.filters.group)
        : [null];
    }
    let userRequest;
    if (this.filters && this.filters.user) {
      userRequest = new User(this.filters.user);
      await userRequest.fetchIfNeededOrWait();
    }
    body.from = this.filters && this.filters.start ? this.filters.start.toISOString() : undefined;
    body.to = this.filters && this.filters.end ? this.filters.end.toISOString() : undefined;
    if (this.groupedBy === GetGroupedBillingData.GROUP_BY.billingCenters) {
      if (this.filters && this.filters.group) {
        const usersRequest = new UsersList();
        await usersRequest.fetch();
        this.users = {};
        if (usersRequest.loaded) {
          (usersRequest.value || []).forEach(u => {
            this.users[u.userName] = u;
          });
        }
        body.filters = {};
        body.grouping = 'USER';
        if (billingCenter) {
          body.filters.billing_center = [billingCenter.name];
        }
      } else {
        body.grouping = GetGroupedBillingData.GROUP_BY.billingCenters;
      }
      return super.send(body);
    } else if (this.groupedBy === GetGroupedBillingData.GROUP_BY.resources) {
      const payload = {};
      const storageBody = {
        from: this.filters.start ? this.filters.start.toISOString() : undefined,
        to: this.filters.end ? this.filters.end.toISOString() : undefined,
        filters: {},
        grouping: 'STORAGE_TYPE'
      };
      if (this.filters && this.filters.user && userRequest.loaded && userRequest.value) {
        storageBody.filters.owner = [userRequest.value.userName];
      }
      if (this.filters && this.filters.group && billingCenter) {
        storageBody.filters.billing_center = [billingCenter.name];
      }
      await super.send(storageBody);
      payload['Storage'] = this._response && this._response.payload
        ? this._response.payload
        : [];

      const instancesBody = {
        from: this.filters.start ? this.filters.start.toISOString() : undefined,
        to: this.filters.end ? this.filters.end.toISOString() : undefined,
        filters: {},
        grouping: 'RUN_COMPUTE_TYPE'
      };
      if (this.filters && this.filters.user && userRequest.loaded && userRequest.value) {
        instancesBody.filters.owner = [userRequest.value.userName];
      }
      if (this.filters && this.filters.group && billingCenter) {
        instancesBody.filters.billing_center = [billingCenter.name];
      }
      await super.send(instancesBody);
      payload['Compute instances'] = this._response && this._response.payload
        ? this._response.payload
        : [];

      this.update({
        status: 'OK',
        payload
      });
    } else if (
      [
        GetGroupedBillingData.GROUP_BY.storages,
        GetGroupedBillingData.GROUP_BY.fileStorages,
        GetGroupedBillingData.GROUP_BY.objectStorages
      ].indexOf(this.groupedBy) >= 0
    ) {
      body.filters = {};
      body.grouping = GetGroupedBillingData.GROUP_BY.storages;
      body.loadDetails = true;
      if (this.groupedBy === GetGroupedBillingData.GROUP_BY.fileStorages) {
        body.filters.storage_type = [GetGroupedBillingData.GROUP_BY.fileStorages];
      } else if (this.groupedBy === GetGroupedBillingData.GROUP_BY.objectStorages) {
        body.filters.storage_type = [GetGroupedBillingData.GROUP_BY.objectStorages];
      }
      if (this.filters && this.filters.user && userRequest.loaded && userRequest.value) {
        body.filters.owner = [userRequest.value.userName];
      }
      if (this.filters && this.filters.group && billingCenter) {
        body.filters.billing_center = [billingCenter.name];
      }

      return super.send(body);
    } else if ([
      GetGroupedBillingData.GROUP_BY.instances,
      GetGroupedBillingData.GROUP_BY.tools,
      GetGroupedBillingData.GROUP_BY.pipelines
    ].indexOf(this.groupedBy) >= 0) {
      body.filters = {};
      body.grouping = this.groupedBy;
      body.loadDetails = true;
      if (this.filters.type) {
        body.filters.compute_type = [this.filters.type.toUpperCase()];
      }
      if (this.filters && this.filters.user && userRequest.loaded && userRequest.value) {
        body.filters.owner = [userRequest.value.userName];
      }
      if (this.filters && this.filters.group && billingCenter) {
        body.filters.billing_center = [billingCenter.name];
      }

      return super.send(body);
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
            value: isNaN(i.cost) ? 0 : costMapper(i.cost),
            user: {
              ...this.users[name],
              // todo
              // runsDuration: i.user.run_duration,
              // storageUsage: i.user.storage_duration,
              // runsCount: i.user.run_count,
              spendings: isNaN(i.cost) ? 0 : costMapper(i.cost)
            }
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
          usage: item.groupingInfo.usage,
          runsCount: item.groupingInfo.runs,
          value: isNaN(item.cost) ? 0 : costMapper(item.cost)
        };
      }
    });

    return res;
  }

  prepareData (rawData) {
    const res = {};
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
        rawData.forEach(i => {
          if ('object' in i) {
            const name = i.object.name || i.object.userName;
            res[name] = i;
          }
        });
        break;
    }
    return res;
  }
}

const KeyMappers = {
  value: 'previous',
  usage: 'previousUsage',
  runsCount: 'previousRunsCount'
};

function join (current, previous, keyMappers = KeyMappers) {
  const currentKeys = Object.keys(current || {});
  const previousKeys = Object.keys(previous || {});
  const keys = [...currentKeys, ...previousKeys]
    .filter((key, index, array) => array.indexOf(key) === index);
  const result = {};
  const prevKeys = Object.keys(keyMappers);
  for (let i = 0; i < keys.length; i++) {
    const key = keys[i];
    const c = current[key] || {};
    const p = previous[key] || {};
    const prevObj = {};
    for (let j = 0; j < prevKeys.length; j++) {
      const prevKey = prevKeys[j];
      if (p && p.hasOwnProperty(prevKey)) {
        prevObj[keyMappers[prevKey]] = p[prevKey];
        delete p[prevKey];
      }
    }
    result[key] = {
      ...p,
      value: 0,
      ...c,
      ...prevObj
    };
  }
  return result;
}

class GetGroupedBillingDataWithPreviousRange extends GetDataWithPrevious {
  constructor (filters, groupedBy) {
    super(GetGroupedBillingData, filters, groupedBy);
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
