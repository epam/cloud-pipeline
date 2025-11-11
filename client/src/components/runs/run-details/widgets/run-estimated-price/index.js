import React from 'react';
import PropTypes from 'prop-types';
import RunDetail, {RunDetailProps} from '../run-detail';
import evaluateRunPrice from '../../../../../utils/evaluate-run-price';
import JobEstimatedPriceInfo from '../../../../special/job-estimated-price-info';

function RunEstimatedPrice (props) {
  const {
    run,
    runTasks = []
  } = props;
  if (!run) {
    return null;
  }
  const adjustPrice = (value) => {
    if (value === 0) {
      return 0;
    }
    let cents = Math.ceil(value * 100);
    if (cents < 1) {
      cents = 1;
    }
    return cents / 100;
  };
  return (
    <RunDetail
      {...props}
    >
      <span>Estimated price:</span>
      <JobEstimatedPriceInfo>
        {
          adjustPrice(evaluateRunPrice(
            run,
            {
              analyseSchedulingPhase: true,
              runTasks
            }
          ).total).toFixed(2)
        }
        $
      </JobEstimatedPriceInfo>
    </RunDetail>
  );
}

RunEstimatedPrice.propTypes = {
  ...RunDetailProps,
  runTasks: PropTypes.oneOfType([PropTypes.object, PropTypes.array])
};

export default RunEstimatedPrice;
