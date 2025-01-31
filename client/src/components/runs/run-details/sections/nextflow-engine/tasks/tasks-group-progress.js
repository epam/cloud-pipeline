import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './nextflow-engine-tasks.css';
import {
  getBarClassNameForNextflowTaskStatus,
  getSortedTaskStatuses
} from './utilities';

function getStatusStyle (statusCount, total) {
  if (total === 0 || statusCount === 0) {
    return {
      width: 0,
      padding: 0
    };
  }
  const percent = (statusCount / total) * 100;
  return {
    width: `${percent}%`
  };
}

function TasksGroupProgress (props) {
  const {
    className,
    style,
    tasksGroup
  } = props;
  const {
    stats = []
  } = tasksGroup || {};
  const sorted = getSortedTaskStatuses(stats, true);
  const total = sorted.reduce((res, cur) => res + cur.count, 0);
  return (
    <div
      className={classNames(
        className,
        styles.tasksGroupProgress
      )}
      style={style}
      key="tasks-group-progress"
    >
      {sorted.map((st) => (
        <div
          key={st.status}
          style={getStatusStyle(st.count, total)}
          className={classNames(
            styles.tasksGroupStatusProgress
          )}
        >
          <div
            className={classNames(
              styles.tasksGroupStatusProgressBar,
              getBarClassNameForNextflowTaskStatus(st.status)
            )}
          />
        </div>
      ))}
    </div>
  );
}

TasksGroupProgress.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tasksGroup: PropTypes.object
};

export default TasksGroupProgress;
