import React, { useCallback } from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import { CloseCircleFilled } from '@ant-design/icons';
import ipcResponse from '../../common/ipc-response';
import './operation.css';

const OPERATION_HEIGHT = 30;

function Progress(
  {
    progress,
  },
) {
  if (progress === 0) {
    return null;
  }
  return (
    <div className="operation-progress">
      <div
        className="operation-progress-bar"
        style={{ width: `${progress}%` }}
      />
    </div>
  );
}

Progress.propTypes = {
  progress: PropTypes.number,
};

Progress.defaultProps = {
  progress: 0,
};

function Operation(
  {
    className,
    style,
    operation,
    onClose,
  },
) {
  const abortCallback = useCallback(() => ipcResponse(
    'abortOperation',
    operation?.id,
  ), [operation?.id]);
  const onCloseCallback = useCallback(() => {
    if (typeof onClose === 'function' && operation) {
      onClose(operation);
    }
  }, [onClose, operation]);
  const inProgress = !/^(aborting|error|aborted|done)$/i.test(operation?.status);
  const finished = /^(error|aborted|done)$/i.test(operation?.status);
  return (
    <div
      className={
        classNames(
          className,
          'operation-container',
        )
      }
      style={{
        minHeight: OPERATION_HEIGHT,
        ...(style || {}),
      }}
    >
      <span
        className={
          classNames(
            'operation',
            'status',
            {
              info: !/^error$/i.test(operation?.status),
              error: /^error$/i.test(operation?.status),
            },
          )
        }
      >
        {operation?.description || '\u00A0'}
      </span>
      {
        inProgress && (
          <CloseCircleFilled
            className="close"
            onClick={abortCallback}
          />
        )
      }
      {
        finished && (
          <CloseCircleFilled
            className="close"
            onClick={onCloseCallback}
          />
        )
      }
      {
        /^pending$/i.test(operation?.status)
        && operation?.progress > 0 && (
          <Progress
            progress={operation.progress * 100}
          />
        )
      }
    </div>
  );
}

Operation.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  operation: PropTypes.shape({
    id: PropTypes.number,
    status: PropTypes.string,
    description: PropTypes.string,
    progress: PropTypes.number,
  }).isRequired,
  onClose: PropTypes.func,
};

Operation.defaultProps = {
  className: undefined,
  style: undefined,
  onClose: undefined,
};

export default Operation;
