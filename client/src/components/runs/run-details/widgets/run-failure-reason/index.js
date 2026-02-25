import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {ExclamationCircleOutlined} from '@ant-design/icons';
import RunDetail, {RunDetailProps} from '../run-detail';

function RunFailureReason (props) {
  const {
    className,
    run,
    wrapWithBrackets
  } = props;
  if (!run) {
    return null;
  }
  const {
    status,
    podStatus
  } = run || {};
  if (!status || !podStatus || !/^failure$/i.test(status)) {
    return null;
  }
  return (
    <RunDetail
      {...props}
      className={classNames(className, 'cp-error')}
    >
      <ExclamationCircleOutlined className="cp-error" />
      <span>{wrapWithBrackets ? `(${podStatus})` : podStatus}</span>
    </RunDetail>
  );
}

RunFailureReason.propTypes = {
  ...RunDetailProps,
  wrapWithBrackets: PropTypes.bool
};

export default RunFailureReason;
