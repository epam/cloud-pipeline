import {GetGroupedInstances} from './get-grouped-instances';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

export class GetGroupedPipelines extends GetGroupedInstances {
  constructor (filters, pagination = null) {
    super(filters, pagination);
    this.grouping = 'PIPELINE';
  }
}

export class GetGroupedPipelinesWithPrevious extends GetDataWithPrevious {
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
      GetGroupedPipelines,
      formattedFilters,
      pagination
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
