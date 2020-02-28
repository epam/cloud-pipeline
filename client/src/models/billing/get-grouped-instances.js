import BaseBillingRequest from './base-billing-request';
import GetDataWithPrevious from './get-data-with-previous';
import {costMapper, minutesToHours} from './utils';
import join from './join-periods';

export class GetGroupedInstances extends BaseBillingRequest {
  constructor (filters, pagination = null) {
    super(filters, true, pagination);
    this.grouping = 'RUN_INSTANCE_TYPE';
  }

  async prepareBody () {
    await super.prepareBody();
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

export class GetGroupedInstancesWithPrevious extends GetDataWithPrevious {
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
      GetGroupedInstances,
      formattedFilters,
      pagination
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
