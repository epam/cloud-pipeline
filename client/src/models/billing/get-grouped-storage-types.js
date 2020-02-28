import BaseBillingRequest from './base-billing-request';
import {costMapper} from './utils';

export class GetGroupedStorageTypes extends BaseBillingRequest {
  constructor (filters, pagination = null) {
    super(filters, false, pagination);
    this.grouping = 'STORAGE_TYPE';
  }

  postprocess (value) {
    const payload = super.postprocess(value);
    return this.prepareStorageTypes(payload);
  }

  prepareStorageTypes (raw) {
    function renameStorage (value) {
      if (/^object_storage$/i.test(value)) {
        return 'Object';
      }
      if (/^file_storage$/i.test(value)) {
        return 'File';
      }
      return value;
    }
    const res = {};
    if (raw) {
      raw.forEach(i => {
        const name = renameStorage(i.groupingInfo[this.grouping]);
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
