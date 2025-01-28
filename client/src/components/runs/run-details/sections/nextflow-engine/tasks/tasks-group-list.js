import React from 'react';
import PropTypes from 'prop-types';
import classNames from 'classnames';
import {Input} from 'antd';
import styles from './nextflow-engine-tasks.css';
import TasksGroup from './tasks-group';

class TasksGroupList extends React.Component {
  state = {filter: ''};

  onChangeFilter = (e) => {
    this.setState({filter: e.target.value});
  };

  render () {
    const {
      className,
      style,
      tasksGroups = [],
      active,
      onActiveChange
    } = this.props;
    const {
      filter
    } = this.state;
    const filtered = tasksGroups
      .filter((t) => !filter ||
        filter.trim().length === 0 ||
        t.name.toLowerCase().includes(filter.trim().toLowerCase()));
    return (
      <div
        className={classNames(className, styles.tasksGroups)}
        style={style}
      >
        <div className={classNames(styles.tasksGroupListHeader)}>
          <span style={{fontWeight: 'bold', flexShrink: 0}}>
            Processes
          </span>
          <span style={{marginLeft: 5, flexShrink: 0}}>
            {
              filtered.length < tasksGroups.length
                ? `(${filtered.length} / ${tasksGroups.length})`
                : `(${tasksGroups.length})`
            }
          </span>
          <div style={{flex: 1, marginLeft: 5}}>
            <Input
              size="small"
              placeholder="Filter processes"
              value={filter}
              onChange={this.onChangeFilter}
            />
          </div>
        </div>
        <div className={classNames(styles.tasksGroupList)}>
          {
            filtered.map((tg) => (
              <TasksGroup
                key={tg.key}
                tasksGroup={tg}
                active={tg.key === active}
                onClick={onActiveChange}
              />
            ))
          }
          {
            filtered.length === 0 && filter && filter.trim().length && tasksGroups.length > 0 && (
              <span className="cp-text-not-important">
                Nothing found for <b>{filter}</b>
              </span>
            )
          }
          {
            tasksGroups.length === 0 && (
              <span className="cp-text-not-important">
                Processes not found
              </span>
            )
          }
        </div>
      </div>
    );
  }
}

TasksGroupList.propTypes = {
  className: PropTypes.string,
  style: PropTypes.object,
  tasksGroups: PropTypes.oneOfType([PropTypes.object, PropTypes.array]),
  active: PropTypes.string,
  onActiveChange: PropTypes.func
};

export default TasksGroupList;
