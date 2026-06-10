import {generateRunInstanceParameterComponent} from './common';
import {getRunSpotTypeName} from '../../../../../special/spot-instance-names';
import {isDtsEnvironment} from './utilities';

const RunPriceType = generateRunInstanceParameterComponent({
  render: getRunSpotTypeName,
  check: (run) => run && !isDtsEnvironment(run),
});

export default RunPriceType;
