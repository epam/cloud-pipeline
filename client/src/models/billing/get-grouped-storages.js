import BaseBillingRequest from './base-billing-request';
import {costMapper} from './utils';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

export class GetGroupedStorages extends BaseBillingRequest {
  constructor (filters, pagination = null) {
    super(filters, true, pagination);
    this.grouping = 'STORAGE';
  }

  postprocess (value) {
    const payload = super.postprocess(value);
    return this.prepareStoragesData(payload);
  }

  prepareStoragesData (raw) {
    const res = {};
    const GB = 1024 * 1024 * 1024;

    const emptyIfUnknown = (value) => /^unknown$/i.test(value) ? '' : value;

    (raw && raw.length ? raw : []).forEach(i => {
      let name = i.info && i.info.name ? i.info.name : i.groupingInfo.STORAGE;
      if (name && name !== 'unknown') {
        res[name] = {
          name,
          owner: emptyIfUnknown(i.groupingInfo.owner),
          created: emptyIfUnknown(i.groupingInfo.created),
          region: emptyIfUnknown(i.groupingInfo.region),
          provider: emptyIfUnknown(i.groupingInfo.provider),
          value: isNaN(i.cost) ? 0 : costMapper(i.cost),
          usage: isNaN(i.groupingInfo.usage_storages)
            ? 0
            : Math.floor(+(i.groupingInfo.usage_storages) / GB * 100) / 100.0,
          ...i,
          usageLast: isNaN(i.groupingInfo.usage_storages_last)
            ? 0
            : Math.floor(+(i.groupingInfo.usage_storages_last) / GB * 100) / 100.0,
          ...i,
          billingCenter: emptyIfUnknown(i.groupingInfo.billing_center),
          storageType: emptyIfUnknown(i.groupingInfo.storage_type)
        };
      }
    });

    return res;
  }
}

export class GetGroupedStoragesWithPrevious extends GetDataWithPrevious {
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
      GetGroupedStorages,
      formattedFilters,
      pagination
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
