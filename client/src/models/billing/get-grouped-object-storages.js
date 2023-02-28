import {GetGroupedStorages} from './get-grouped-storages';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

export class GetGroupedObjectStorages extends GetGroupedStorages {
  constructor (filters, pagination = null, loadCostDetails = false) {
    super(filters, true, pagination, loadCostDetails);
  }

  async prepareBody () {
    await super.prepareBody();
    this.body.filters.storage_type = ['OBJECT_STORAGE'];
  }
}

export class GetGroupedObjectStoragesWithPrevious extends GetDataWithPrevious {
  constructor (filters, pagination = null, loadCostDetails = false) {
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
      formattedFilters,
      pagination,
      loadCostDetails
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
