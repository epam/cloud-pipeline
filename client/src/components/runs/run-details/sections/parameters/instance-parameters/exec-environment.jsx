import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {LoadingOutlined} from '@ant-design/icons';
import {isDtsEnvironment, isFireCloudEnvironment} from './utilities';

const ExecEnvironment = inject(
  'dtsList',
  'preferences',
)(
  observer(function (props) {
    const {className, style, run, dtsList, preferences} = props;
    if (!run) {
      return null;
    }
    const {executionPreferences = {}} = run;
    let result;
    if (isDtsEnvironment(run)) {
      const {dtsId} = executionPreferences;
      if (!dtsList || dtsId === undefined || dtsId === null) {
        return null;
      }
      dtsList.fetchIfNeededOrWait();
      if (dtsList.loaded) {
        const dts = (dtsList.value || []).find((d) => d.id === dtsId);
        result = dts ? dts.name : `${dtsId}`;
      }
    } else if (isFireCloudEnvironment(run)) {
      result = 'FireCloud';
    } else if (preferences) {
      preferences.fetchIfNeededOrWait();
      result = preferences.deploymentName || 'EPAM Cloud Pipeline';
    }
    if (!result) {
      return <LoadingOutlined />;
    }
    return (
      <span className={className} style={style}>
        {result}
      </span>
    );
  }),
);

ExecEnvironment.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
};

ExecEnvironment.available = function available(run) {
  return run && run.executionPreferences && run.executionPreferences.environment;
};

export default ExecEnvironment;
