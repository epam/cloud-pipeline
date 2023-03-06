import BaseBillingRequest from './base-billing-request';
import {costMapper, minutesToHours} from './utils';
import join from './join-periods';
import GetGroupedComputeDataWithPrevious from './get-grouped-compute-data-with-previous';

/**
 * @typedef {Object} GetGroupedInstancesOptions
 * @property {Object} filters
 * @property {BaseBillingRequestPagination|boolean} [pagination]
 */

export class GetGroupedInstances extends BaseBillingRequest {
  /**
   * @param {GetGroupedInstancesOptions} options
   */
  constructor (options = {}) {
    super({...options, loadDetails: true});
    this.grouping = 'RUN_INSTANCE_TYPE';
  }

  prepareBody () {
    super.prepareBody();
    if (this.filters.type) {
      this.body.filters.compute_type = [this.filters.type.toUpperCase()];
    }
  }

  postprocess (value) {
    const payload = super.postprocess(value);
    return this.prepareInstancesReportData(payload);
  }

  prepareInstancesReportData (raw) {
    const res = {};

    (raw && raw.length > 0 ? raw : []).forEach((item) => {
      let fullName;
      let name = item.groupingInfo[this.grouping];
      if (name.includes('/')) {
        name = name.split('/').pop();
        fullName = item.groupingInfo[this.grouping];
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
}

export class GetGroupedInstancesWithPrevious extends GetGroupedComputeDataWithPrevious {
  /**
   * @param {GetGroupedInstancesOptions} options
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
      GetGroupedInstances,
      {
        filters: formattedFilters,
        pagination
      },
      'instances'
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
