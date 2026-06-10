import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './nextflow-engine-tasks.module.css';
import {
  getBarClassNameForNextflowTaskStatus,
  getSortedTaskStatuses,
  NextflowTaskStatus,
} from './utilities';

function TasksStatuses(props) {
  const {className, style, statuses = [], taskGroupFilter} = props;
  const sorted = getSortedTaskStatuses(statuses);
  const total = sorted.reduce((res, cur) => res + cur.count, 0);
  return (
    <div className={classNames(className, styles.tasksStatuses)} style={style}>
      <div className={styles.tasksStatusesHeader}>
        {taskGroupFilter ? (
          <span
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              flex: 1,
              overflow: 'hidden',
            }}
          >
            <span
              style={{
                fontWeight: 'bold',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {taskGroupFilter}
            </span>
            <span style={{marginLeft: 5, flexShrink: 0}}>process tasks statuses</span>
          </span>
        ) : (
          <span style={{fontWeight: 'bold', flex: 1, overflow: 'hidden'}}>Tasks statuses</span>
        )}
      </div>
      <div className={styles.tasksStatusesContainer}>
        <table className={styles.tasksStatusesTable}>
          <tbody>
            {sorted.map((s) => (
              <tr key={s.status}>
                <td style={{width: 1}}>
                  <NextflowTaskStatus status={s.status} showLabel />
                </td>
                <td style={{minWidth: 80, width: 80}}>{`${s.count}`}</td>
                <td>
                  <div
                    className={classNames(
                      styles.tasksStatusCountBar,
                      'cp-run-engine-task-status-bar',
                    )}
                  >
                    <div
                      className={classNames(
                        styles.tasksStatusCountBarFill,
                        {[styles.nonEmpty]: s.count > 0},
                        getBarClassNameForNextflowTaskStatus(s.status),
                      )}
                      style={{width: `${total === 0 ? 0 : (s.count / total) * 100.0}%`}}
                    />
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

TasksStatuses.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  statuses: PropTypes.oneOfType([PropTypes.array, PropTypes.object]),
  taskGroupFilter: PropTypes.string,
};

export default TasksStatuses;
