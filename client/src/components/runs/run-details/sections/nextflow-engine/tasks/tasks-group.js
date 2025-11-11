import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './nextflow-engine-tasks.css';
import TasksGroupProgress from './tasks-group-progress';
import {getSortedTaskStatuses, NextflowTaskStatus} from './utilities';

function TasksGroup (props) {
  const {
    className,
    style,
    tasksGroup,
    active,
    onClick
  } = props;
  const onContainerClick = (event) => {
    if (onClick && tasksGroup) {
      event.stopPropagation();
      event.preventDefault();
      onClick(tasksGroup.key);
    }
  };
  const stats = getSortedTaskStatuses(tasksGroup ? tasksGroup.stats : [], false)
    .map((st) => (
      <div key={st.status} className={styles.tasksGroupStatsEntry}>
        <NextflowTaskStatus status={st.status} showLabel={false} style={{fontWeight: 'bold'}} />
        <span>{st.count}</span>
      </div>
    ));
  return (
    <div
      className={classNames(
        className,
        'cp-run-engine-tasks-group',
        styles.tasksGroup,
        {[styles.active]: active, 'cp-primary': active, active}
      )}
      style={style}
      onClick={onContainerClick}
    >
      <div className={styles.tasksGroupInfo}>
        <div className={styles.tasksGroupHeader}>
          <span className={styles.tasksGroupName}>{tasksGroup.name}</span>
          <div className={styles.tasksGroupStats}>
            {stats}
          </div>
        </div>
        <TasksGroupProgress tasksGroup={tasksGroup} />
      </div>
      <div className={classNames(styles.tasksGroupActiveIndicator, 'cp-primary')}>
        <div className={styles.indicator} />
      </div>
    </div>
  );
}

TasksGroup.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  active: PropTypes.bool,
  tasksGroup: PropTypes.object,
  onClick: PropTypes.func
};

export default TasksGroup;
