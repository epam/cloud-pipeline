import RemotePost from '../basic/RemotePost';
import GetDataWithPrevious from './get-data-with-previous';
import {GetGroupedStorageTypes} from './get-grouped-storage-types';
import {GetGroupedComputeTypes} from './get-grouped-compute-types';
import join from './join-periods';

export class GetGroupedResources extends RemotePost {
  constructor (filters, pagination = null) {
    super();
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

    this.update({
      status: 'OK',
      payload
    });
    this._pending = false;
    this._postIsExecuting = false;
  }
}

export class GetGroupedResourcesWithPrevious extends GetDataWithPrevious {
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
      GetGroupedResources,
      formattedFilters,
      pagination
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
      'Compute instances': compute
    };
  }
}
