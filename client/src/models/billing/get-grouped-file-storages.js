import {GetGroupedStorages} from './get-grouped-storages';
import join from './join-periods';
import GetGroupedStoragesDataWithPrevious from './get-grouped-storages-data-with-previous';

/**
 * @typedef {Object} GetGroupedFileStoragesOptions
 * @property {Object} [filters]
 * @property {BaseBillingRequestPagination|boolean} [pagination]
 * @property {boolean} [loadCostDetails]
 */

export class GetGroupedFileStorages extends GetGroupedStorages {
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
        storageTypes: ['FILE_STORAGE']
      },
      loadDetails: true
    });
  }
}

export class GetGroupedFileStoragesWithPrevious extends GetGroupedStoragesDataWithPrevious {
  /**
   * @param {GetGroupedFileStoragesOptions} options
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
      GetGroupedFileStorages,
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
