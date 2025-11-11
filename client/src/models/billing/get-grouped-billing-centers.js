import BaseBillingRequest from './base-billing-request';
import {costMapper, minutesToHours, bytesToGbs} from './utils';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';
import moment from 'moment-timezone';

/**
 * @typedef {Object} GetGroupedBillingCentersOptions
 * @property {Object} filters
 * @property {BaseBillingRequestPagination|boolean} [pagination]
 */

export class GetGroupedBillingCenters extends BaseBillingRequest {
  /**
   * @param {GetGroupedBillingCentersOptions} options
   */
  constructor (options = {}) {
    const {
      filters = {},
      pagination
    } = options;
    const {resourceType, fetchLastDay, ...rest} = filters;
    super({filters: rest, loadDetails: true, pagination});
    this.resourceType = resourceType;
    this.fetchLastDay = fetchLastDay;
    if (
      this.filters &&
      (
        this.filters.billingGroup?.length === 1 ||
        this.filters.adGroup?.length === 1
      )
    ) {
      this.grouping = 'USER';
    } else {
      this.grouping = 'BILLING_CENTER';
    }
  }

  prepareBody () {
    super.prepareBody();
    if (this.fetchLastDay && this.filters && this.filters.endStrict) {
      this.body.from = moment(this.filters.endStrict).startOf('d');
      this.body.to = moment(this.filters.endStrict).endOf('d');
    }
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
      if (
        this.filters &&
        (this.filters.billingGroup || this.filters.adGroup)
      ) {
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
  /**
   * @param {GetGroupedBillingCentersOptions} options
   */
  constructor (options = {}) {
    const {
      filters = {},
      pagination
    } = options;
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
      {filters: formattedFilters, pagination}
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
