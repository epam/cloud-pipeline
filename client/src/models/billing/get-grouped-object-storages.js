import {GetGroupedStorages} from './get-grouped-storages';
import join from './join-periods';
import GetGroupedStoragesDataWithPrevious from './get-grouped-storages-data-with-previous';

/**
 * @typedef {Object} GetGroupedObjectStoragesOptions
 * @property {Object} [filters]
 * @property {BaseBillingRequestPagination|boolean} [pagination]
 * @property {boolean} [loadCostDetails]
 */

export class GetGroupedObjectStorages extends GetGroupedStorages {
  /**
   * @param {BaseBillingRequestOptions} [options]
   */
  constructor (options) {
    const {
      filters = {}
    } = options;
    super({
      ...options,
      filters: {
        ...filters,
        storageTypes: ['OBJECT_STORAGE']
      },
      loadDetails: true
    });
  }
}

export class GetGroupedObjectStoragesWithPrevious extends GetGroupedStoragesDataWithPrevious {
  /**
   * @param {GetGroupedObjectStoragesOptions} options
   */
  constructor (options = {}) {
    const {
      filters = {},
      pagination,
      loadCostDetails
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
      GetGroupedObjectStorages,
      {
        filters: formattedFilters,
        pagination,
        loadCostDetails
      }
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
