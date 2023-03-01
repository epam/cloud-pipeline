import {GetGroupedStorages} from './get-grouped-storages';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

/**
 * @typedef {Object} GetGroupedObjectStoragesOptions
 * @property {Object} [filters]
 * @property {BaseBillingRequestPagination|boolean} [pagination]
 * @property {boolean} [loadCostDetails]
 */

export class GetGroupedObjectStorages extends GetGroupedStorages {
  prepareBody () {
    super.prepareBody();
    this.body.filters.storage_type = ['OBJECT_STORAGE'];
  }
}

export class GetGroupedObjectStoragesWithPrevious extends GetDataWithPrevious {
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
