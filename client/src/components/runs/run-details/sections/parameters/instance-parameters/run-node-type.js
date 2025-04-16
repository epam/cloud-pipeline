import {generateRunInstanceParameterValueComponent} from './common';
import {isDtsEnvironment} from './utilities';

const RunNodeType = generateRunInstanceParameterValueComponent(
  'nodeType',
  {
    check: (run) => run && !isDtsEnvironment(run)
  }
);

export default RunNodeType;
