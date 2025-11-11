import React from 'react';
import PropTypes from 'prop-types';
import { Tooltip } from 'antd';
import { CheckCircleFilled, CloseCircleFilled } from '@ant-design/icons';

function DiagnoseState({ diagnosed = false, error }) {
  if (!diagnosed) {
    return null;
  }
  if (error) {
    return (
      <Tooltip title={error}>
        <CloseCircleFilled style={{ color: 'red' }} />
      </Tooltip>
    );
  }
  return (
    <CheckCircleFilled style={{ color: 'green' }} />
  );
}

DiagnoseState.propTypes = {
  diagnosed: PropTypes.bool,
  error: PropTypes.string,
};

DiagnoseState.defaultProps = {
  diagnosed: false,
  error: undefined,
};

export default DiagnoseState;
