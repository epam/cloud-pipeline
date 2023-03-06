import RemotePost from '../basic/RemotePost';
import GetDataWithPrevious from './get-data-with-previous';
import {GetGroupedStorageTypes} from './get-grouped-storage-types';
import {GetGroupedComputeTypes} from './get-grouped-compute-types';
import QuotaGroups from '../../components/billing/quotas/utilities/quota-groups';
import join from './join-periods';

/**
 * @typedef {Object} GetGroupedResourcesOptions
 * @property {Object} filters
 * @property {BaseBillingRequestPagination|boolean} [pagination]
 */

export class GetGroupedResources extends RemotePost {
  /**
   * @param {GetGroupedResourcesOptions} options
   */
  constructor (options = {}) {
    super();
    const {
      filters,
      pagination
    } = options;
    this.filters = filters;
    this.pagination = pagination;
  }
  async fetch () {
    const payload = {};

    this._pending = true;
    this._postIsExecuting = true;

    const storageTypesRequest = new GetGroupedStorageTypes(
      this.filters,
      this.pagination
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

    const computeTypesRequest = new GetGroupedComputeTypes(
      this.filters,
      this.pagination
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

    payload.quotaGroups = {
      'Storage': QuotaGroups.storages,
      'Compute instances': QuotaGroups.computeInstances
    };

    this.update({
      status: 'OK',
      payload
    });
    this._pending = false;
    this._postIsExecuting = false;
  }
}

export class GetGroupedResourcesWithPrevious extends GetDataWithPrevious {
  /**
   * @param {GetGroupedResourcesOptions} options
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
      GetGroupedResources,
      {
        filters: formattedFilters,
        pagination
      }
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
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
      'Compute instances': compute,
      quotaGroups: {
        'Storage': QuotaGroups.storages,
        'Compute instances': QuotaGroups.computeInstances
      }
    };
  }
}
