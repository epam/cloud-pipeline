import {GetGroupedInstances} from './get-grouped-instances';
import join from './join-periods';
import GetGroupedComputeDataWithPrevious from './get-grouped-compute-data-with-previous';

export class GetGroupedPipelines extends GetGroupedInstances {
  /**
   * @param {GetGroupedInstancesOptions} options
   */
  constructor (options = {}) {
    super(options);
    this.grouping = 'PIPELINE';
  }
}

export class GetGroupedPipelinesWithPrevious extends GetGroupedComputeDataWithPrevious {
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
      },
      'pipelines'
    );
  }

  postprocess (value) {
    const {current, previous} = super.postprocess(value);
    return join(current, previous);
  }
}
