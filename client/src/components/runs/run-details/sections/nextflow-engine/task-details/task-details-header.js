import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';

function TaskDetailsHeader (props) {
  const {
    className,
    style,
    task
  } = props;
  if (!task) {
    return null;
  }
  return (
    <div
      className={classNames(
        className
      )}
      style={style}
    >
      <b>{task.taskName}</b>
    </div>
  );
}

TaskDetailsHeader.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  task: PropTypes.object
};

export default TaskDetailsHeader;
