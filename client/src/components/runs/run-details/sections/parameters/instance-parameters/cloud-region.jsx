import React from 'react';
import PropTypes from 'prop-types';
import {inject, observer} from 'mobx-react';
import {isDtsEnvironment} from './utilities';
import AWSRegionTag from '../../../../../special/AWSRegionTag';

const iconsStyle = {};

const RunCloudRegion = inject()(
  observer(function (props) {
    const {className, style, run} = props;
    if (!run) {
      return null;
    }
    const {instance = {}} = run;
    const {cloudRegionId} = instance;
    if (cloudRegionId) {
      return (
        <AWSRegionTag
          className={className}
          style={style}
          flagStyle={iconsStyle}
          providerStyle={iconsStyle}
          regionId={cloudRegionId}
          displayName
        />
      );
    }
    return null;
  }),
);

RunCloudRegion.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  run: PropTypes.object,
};

RunCloudRegion.available = function runCloudRegionAvailable(run) {
  return !isDtsEnvironment(run) && run && run.instance && run.instance.cloudRegionId;
};

export default RunCloudRegion;
