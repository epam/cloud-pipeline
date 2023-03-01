import {GetGroupedStorages} from './get-grouped-storages';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

/**
 * @typedef {Object} GetGroupedFileStoragesOptions
 * @property {Object} [filters]
 * @property {BaseBillingRequestPagination|boolean} [pagination]
 * @property {boolean} [loadCostDetails]
 */

export class GetGroupedFileStorages extends GetGroupedStorages {
  prepareBody () {
    super.prepareBody();
    this.body.filters.storage_type = ['FILE_STORAGE'];
  }
}

export class GetGroupedFileStoragesWithPrevious extends GetDataWithPrevious {
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
