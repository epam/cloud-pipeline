import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {isDtsEnvironment} from './utilities';

const RunCores = inject()(
  observer(function (props) {
    const {className, style, run} = props;
    if (!run) {
      return null;
    }
    const {executionPreferences = {}} = run;
    const {coresNumber} = executionPreferences;
    if (coresNumber) {
      return (
        <span className={className} style={style}>
          {coresNumber}
        </span>
      );
    }
    return null;
  }),
);

RunCores.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
};

RunCores.available = function available(run) {
  return isDtsEnvironment(run) && run.executionPreferences && run.executionPreferences.coresNumber;
};

export default RunCores;
