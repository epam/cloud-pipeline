import React from 'react';
import PropTypes from 'prop-types';
import {TaskRuntimeDataDetails, TaskRuntimeDataDetailsProps} from './task-runtime-data-details';
import RunTaskLogs from '../../../../../run-task-logs';
import CodeEditor from '../../../../../../special/CodeEditor';

function Renderer (props) {
  const {
    className,
    style,
    data,
    asCommand = false
  } = props;
  if (asCommand) {
    return (
      <CodeEditor
        className={className}
        style={{border: 'none', ...(style || {})}}
        code={data}
        readOnly
      />
    );
  }
  return (
    <RunTaskLogs
      className={className}
      style={style}
      logs={data}
      showDate={false}
    />
  );
}

Renderer.propTypes = {
  ...TaskRuntimeDataDetailsProps,
  data: PropTypes.string,
  asCommand: PropTypes.bool
};

function TaskRuntimePlainTextLogs (props) {
  return (
    <TaskRuntimeDataDetails {...props} component={Renderer} />
  );
}

TaskRuntimePlainTextLogs.propTypes = {
  ...TaskRuntimeDataDetailsProps,
  asCommand: PropTypes.bool,
  errorMessage: PropTypes.string
};

export default TaskRuntimePlainTextLogs;
