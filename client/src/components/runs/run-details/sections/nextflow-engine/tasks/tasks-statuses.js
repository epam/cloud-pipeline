import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import styles from './nextflow-engine-tasks.css';
import {
  getClassNameForNextflowTaskStatus,
  NextflowTaskStatus,
  nextflowTaskStatusGroups
} from './utilities';

function TasksStatuses (props) {
  const {
    className,
    style,
    statuses = [],
    taskGroupFilter
  } = props;
  const sorted = nextflowTaskStatusGroups.map((st) => ({
    status: st.statuses[0],
    count: statuses.filter((s) => st.statuses.includes(s.status)).reduce((r, c) => r + c.count, 0)
  }));
  const total = sorted.reduce((res, cur) => res + cur.count, 1);
  return (
    <div
      className={classNames(
        className,
        styles.tasksStatuses
      )}
      style={style}
    >
      <div className={styles.tasksStatusesHeader}>
        {
          taskGroupFilter ? (
            <span>
              <span style={{fontWeight: 'bold'}}>
                {taskGroupFilter}
              </span>
              <span style={{marginLeft: 5}}>
                process tasks statuses
              </span>
            </span>
          ) : (
            <span style={{fontWeight: 'bold'}}>
              Tasks statuses
            </span>
          )
        }
      </div>
      <div className={styles.tasksStatusesContainer}>
        <table className={styles.tasksStatusesTable}>
          <tbody>
            {
              sorted.map((s) => (
                <tr key={s.status}>
                  <td style={{width: 1}}>
                    <NextflowTaskStatus status={s.status} showLabel/>
                  </td>
                  <td style={{minWidth: 80, width: 80}}>
                    {`${s.count}`}
                  </td>
                  <td>
                    <div className={styles.tasksStatusCountBar}>
                      <div
                        className={classNames(
                          styles.tasksStatusCountBarFill,
                          {[styles.nonEmpty]: s.count > 0},
                          getClassNameForNextflowTaskStatus(s.status)
                        )}
                        style={{width: `${(s.count / total) * 100.0}%`}}
                      />
                    </div>
                  </td>
                </tr>
              ))
            }
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
  taskGroupFilter: PropTypes.string
};

export default TasksStatuses;
