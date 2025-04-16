import React from 'react';
import classNames from 'classnames';
import {Icon} from 'antd';
import RunDetail, {RunDetailProps} from '../run-detail';

function RunStateReason (props) {
  const {
    className,
    run
  } = props;
  if (!run) {
    return null;
  }
  const {
    stateReasonMessage
  } = run || {};
  if (!stateReasonMessage) {
    return null;
  }
  return (
    <RunDetail
      {...props}
      className={classNames(className, 'cp-error')}
    >
      <Icon type="exclamation-circle-o" className="cp-error" />
      <span>Server failure reason: </span>
      <span>{stateReasonMessage}</span>
    </RunDetail>
  );
}

RunStateReason.propTypes = {
  ...RunDetailProps
};

export default RunStateReason;
