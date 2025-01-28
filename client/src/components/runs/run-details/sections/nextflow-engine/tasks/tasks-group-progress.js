import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './nextflow-engine-tasks.css';
import {getClassNameForNextflowTaskStatus} from './utilities';

function TasksGroupProgress (props) {
  const {
    className,
    style,
    tasksGroup
  } = props;
  const {
    stats = []
  } = tasksGroup || {};
  const columns = stats.map((st) => `[${st.status}] minmax(5px, ${st.count}fr)`).join(' ');
  const gridStyle = {
    gridTemplateColumns: columns
  };
  return (
    <div
      className={classNames(
        className,
        styles.tasksGroupProgress
      )}
      style={{...(style || {}), ...gridStyle}}
    >
      {stats.map((st) => (
        <div
          key={st.status}
          style={{gridColumn: st.status}}
          className={classNames(
            styles.tasksGroupStatusProgress,
            getClassNameForNextflowTaskStatus(st.status)
          )}
        />
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
