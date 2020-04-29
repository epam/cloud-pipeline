import BaseBillingRequest from './base-billing-request';
import {costMapper, minutesToHours, bytesToGbs} from './utils';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

export class GetGroupedBillingCenters extends BaseBillingRequest {
  constructor (filters, pagination = null) {
    const {resourceType, ...rest} = filters || {};
    super(rest, true, pagination);
    this.resourceType = resourceType;
    if (this.filters && this.filters.group) {
      this.grouping = 'USER';
    } else {
      this.grouping = 'BILLING_CENTER';
    }
  }

  async prepareBody () {
    await super.prepareBody();
    if (this.resourceType) {
      this.body.filters.resource_type = [this.resourceType];
    }
  }

  postprocess (value) {
    const payload = super.postprocess(value);
    return this.prepareBillingCentersData(payload);
  }

  prepareBillingCentersData (raw) {
    const res = {};
    (raw && raw.length ? raw : []).forEach(i => {
      if (this.filters && this.filters.group) {
        const name = i.groupingInfo[this.grouping];
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
        const name = i.groupingInfo[this.grouping];
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
}

export class GetGroupedBillingCentersWithPrevious extends GetDataWithPrevious {
  constructor (filters, pagination = null) {
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
      GetGroupedBillingCenters,
      formattedFilters,
      pagination
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
