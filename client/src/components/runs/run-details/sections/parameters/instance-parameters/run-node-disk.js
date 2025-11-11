import {generateRunInstanceParameterValueComponent} from './common';
import {isDtsEnvironment} from './utilities';

const RunNodeDisk = generateRunInstanceParameterValueComponent(
  'nodeDisk',
  {
    render: (value) => `${value} Gb`,
    check: (run) => run && !isDtsEnvironment(run)
  }
);

export default RunNodeDisk;
