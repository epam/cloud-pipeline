import {GetGroupedInstances} from './get-grouped-instances';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

export class GetGroupedPipelines extends GetGroupedInstances {
  /**
   * @param {GetGroupedInstancesOptions} options
   */
  constructor (options = {}) {
    super(options);
    this.grouping = 'PIPELINE';
  }
}

export class GetGroupedPipelinesWithPrevious extends GetDataWithPrevious {
  /**
   * @param {GetGroupedInstancesOptions} options
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
      GetGroupedPipelines,
      {
        filters: formattedFilters,
        pagination
      }
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
