import {GetGroupedInstances} from './get-grouped-instances';
import GetDataWithPrevious from './get-data-with-previous';
import join from './join-periods';

export class GetGroupedTools extends GetGroupedInstances {
  /**
   * @param {GetGroupedInstancesOptions} options
   */
  constructor (options = {}) {
    super(options);
    this.grouping = 'TOOL';
  }
}

export class GetGroupedToolsWithPrevious extends GetDataWithPrevious {
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
      GetGroupedTools,
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
