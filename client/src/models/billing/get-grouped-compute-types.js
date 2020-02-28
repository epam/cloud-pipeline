import BaseBillingRequest from './base-billing-request';
import {costMapper} from './utils';

export class GetGroupedComputeTypes extends BaseBillingRequest {
  constructor (filters, pagination = null) {
    super(filters, false, pagination);
    this.grouping = 'RUN_COMPUTE_TYPE';
  }

  postprocess (value) {
    const payload = super.postprocess(value);
    return this.prepareComputeTypes(payload);
  }

  prepareComputeTypes (raw) {
    const res = {};

    if (raw) {
      raw.forEach(i => {
        const name = i.groupingInfo[this.grouping];
        if (name && name !== 'unknown') {
          res[name] = {
            ...i,
            value: isNaN(i.cost) ? 0 : costMapper(i.cost)
          };
        }
      });
    }

    return res;
  }
}
